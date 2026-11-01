package com.laminebaidari.telelibre.data

import com.laminebaidari.telelibre.model.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** A stream is alive when its playlist answers HTTP 200 and starts like an HLS playlist. */
class StreamChecker(baseClient: OkHttpClient) {
    private val client = baseClient.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    private val overall = Semaphore(12)
    // A few hosts (e.g. jmp2.uk for Pluto TV) serve dozens of channels; don't hammer them.
    private val perHost = ConcurrentHashMap<String, Semaphore>()

    suspend fun aliveSet(channels: List<Channel>): Set<String> = coroutineScope {
        channels.map { channel ->
            async {
                val host = runCatching { URI(channel.url).host }.getOrNull().orEmpty()
                val hostLimit = perHost.getOrPut(host) { Semaphore(3) }
                hostLimit.withPermit { overall.withPermit { if (isAlive(channel)) channel.url else null } }
            }
        }.awaitAll().filterNotNull().toSet()
    }

    suspend fun isAlive(channel: Channel): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(channel.url)
                .header("User-Agent", channel.userAgent ?: DEFAULT_USER_AGENT)
                .apply { channel.referrer?.let { header("Referer", it) } }
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code != 200) return@use false
                val source = response.body?.source() ?: return@use false
                source.request(4096)
                val head = source.buffer.snapshot(minOf(source.buffer.size, 4096L).toInt()).utf8()
                head.contains("#EXTM3U")
            }
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        const val DEFAULT_USER_AGENT = "VLC/3.0.21 LibVLC/3.0.21"
    }
}
