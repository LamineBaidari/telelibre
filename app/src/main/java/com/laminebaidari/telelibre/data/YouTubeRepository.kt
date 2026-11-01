package com.laminebaidari.telelibre.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request

data class YtChannel(
    val name: String,
    val channelId: String,
    /** Channel avatar from its page. */
    val avatar: String?,
    val liveVideoId: String?,
    val latestTitle: String?,
    val latestVideoId: String?,
) {
    /** What OK opens: the live stream if there is one, else the newest video. */
    val videoId: String? get() = liveVideoId ?: latestVideoId

    /** The live picture while live, else the newest video's thumbnail, else the avatar. */
    val image: String?
        get() = liveVideoId?.let { "https://i.ytimg.com/vi/$it/hqdefault_live.jpg" }
            ?: latestVideoId?.let { "https://i.ytimg.com/vi/$it/mqdefault.jpg" }
            ?: avatar
}

/**
 * Official YouTube channels of Senegalese broadcasters. Most only go live for some programmes, so the row
 * shows who is live now and otherwise the latest programme, both opened in the YouTube app.
 */
class YouTubeRepository(private val client: OkHttpClient) {
    private val _channels = MutableStateFlow(CHANNELS.map { (name, id) -> YtChannel(name, id, null, null, null, null) })
    val channels: StateFlow<List<YtChannel>> = _channels.asStateFlow()

    // Own scope: leaving the rows (e.g. for the player) must not cancel a check halfway.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running: Job? = null
    private var lastRefresh = 0L

    /** Cheap enough to call whenever the rows are shown: does nothing if checked in the last 10 minutes. */
    fun refreshIfStale() {
        if (running?.isActive == true || System.currentTimeMillis() - lastRefresh < MAX_AGE) return
        running = scope.launch {
            val updated = coroutineScope { _channels.value.map { async { check(it) } }.awaitAll() }
            // Live channels first, then the original order.
            _channels.value = updated.sortedBy { if (it.liveVideoId != null) 0 else 1 }
            if (updated.any { it.latestVideoId != null }) lastRefresh = System.currentTimeMillis()
        }
    }

    private fun check(channel: YtChannel): YtChannel {
        val page = get("https://www.youtube.com/channel/${channel.channelId}/live", PAGE_LIMIT)
        val feed = get("https://www.youtube.com/feeds/videos.xml?channel_id=${channel.channelId}", FEED_LIMIT)
        val (latestId, latestTitle) = feed?.let(::parseLatest) ?: (null to null)
        return channel.copy(
            liveVideoId = page?.let(::parseLiveVideoId),
            avatar = page?.let(::parseImage) ?: channel.avatar,
            latestVideoId = latestId ?: channel.latestVideoId,
            latestTitle = latestTitle ?: channel.latestTitle,
        )
    }

    /** Reads at most [limit] bytes: the tags needed sit about 700 KB in, the rest is more script. */
    private fun get(url: String, limit: Long): String? = try {
        val request = Request.Builder().url(url)
            .header("User-Agent", BROWSER_UA)
            .header("Accept-Language", "fr")
            .header("Cookie", "CONSENT=YES+1")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "YouTube HTTP ${response.code}: $url")
                return null
            }
            val source = response.body!!.source()
            source.request(limit)
            source.buffer.snapshot(minOf(source.buffer.size, limit).toInt()).utf8()
        }
    } catch (e: Exception) {
        Log.w(TAG, "YouTube request failed: $url", e)
        null
    }

    companion object {
        private const val TAG = "YouTubeRepository"
        private const val MAX_AGE = 10 * 60_000L
        private const val PAGE_LIMIT = 800_000L
        private const val FEED_LIMIT = 8_000L
        private const val BROWSER_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

        /** Official channels, checked on YouTube (name to channel id). */
        val CHANNELS = listOf(
            "TFM" to "UC5NQ49FVRIAuWE1el6L2gkg",
            "2STV" to "UCeLEGbj240J6JhpP7ba8GwA",
            "Sen TV" to "UCKbMNmSR3KlI9v3xeInHEYA",
            "RTS" to "UC3Pwur55-OPFYDN_xg6JR_w",
            "Walf TV" to "UCLx-m9nUdtVEF56rVQy_75Q",
            "Senegal7" to "UC5eVGjO4ITJA1KM_tva_OSQ",
            "iTV Sénégal" to "UCIdNAY1QlzXahhX6lsZv2kg",
            "Dakaractu TV" to "UCG0t6XiAHui-ziz7SwFTN0g",
            "Senego TV" to "UCwzMh0iy29fijWI8H27RrAg",
            "Touba TV" to "UCavUkP6uhnT8wJpFgu-Q3kg",
        )

        private val CANONICAL_WATCH = Regex("""<link rel="canonical" href="https://www\.youtube\.com/watch\?v=([\w-]{11})"""")
        private val OG_IMAGE = Regex("""<meta property="og:image" content="([^"]+)"""")
        private val ENTRY_ID = Regex("""<entry>.*?<yt:videoId>([\w-]{11})</yt:videoId>.*?<title>([^<]*)</title>""", RegexOption.DOT_MATCHES_ALL)

        /** The /live page points its canonical link at the stream only while the channel is live. */
        fun parseLiveVideoId(page: String): String? = CANONICAL_WATCH.find(page)?.groupValues?.get(1)

        fun parseImage(page: String): String? = OG_IMAGE.find(page)?.groupValues?.get(1)?.replace("&amp;", "&")

        fun parseLatest(feed: String): Pair<String, String>? =
            ENTRY_ID.find(feed)?.let { it.groupValues[1] to unescape(it.groupValues[2]) }

        private fun unescape(text: String) = text
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
    }
}
