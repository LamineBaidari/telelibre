package com.laminebaidari.telelibre.data

import android.util.Log
import com.laminebaidari.telelibre.model.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Downloads the playlists, keeps allowed streams that answer right now. */
class ChannelRefresher(private val client: OkHttpClient) {

    suspend fun refresh(): List<Channel> {
        val candidates = withContext(Dispatchers.IO) { collectCandidates() }
        val alive = StreamChecker(client).aliveSet(candidates)
        // Several URLs can share a key (different feeds or mirrors): keep the first that works.
        val seenKeys = HashSet<String>()
        return candidates.filter { it.url in alive && seenKeys.add(it.key) }
            .also { Log.i(TAG, "${candidates.size} candidates, ${it.size} live channels") }
    }

    private fun collectCandidates(): List<Channel> {
        val byUrl = LinkedHashMap<String, Channel>()
        for (playlist in Sources.PLAYLISTS) {
            try {
                fetch(playlist) { entries ->
                    for (entry in entries) toChannel(playlist, entry)?.let { byUrl.putIfAbsent(it.url, it) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Playlist failed: $playlist", e)
            }
        }
        return byUrl.values.toList()
    }

    private fun fetch(url: String, block: (Sequence<M3uEntry>) -> Unit) {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            response.body!!.charStream().buffered().use { block(M3uParser.parse(it)) }
        }
    }

    companion object {
        private const val TAG = "ChannelRefresher"

        /** Turns a playlist entry into a channel, or null when it must be skipped. */
        fun toChannel(playlist: String, entry: M3uEntry): Channel? {
            val fromNews = playlist == Sources.NEWS_PLAYLIST
            if (fromNews && entry.tvgId !in Sources.NEWS_ALLOWLIST) return null
            if (!LegalityFilter.allowed(entry.url)) return null
            val playlistCountry = Regex("""countries/([a-z]{2})\.m3u$""").find(playlist)?.groupValues?.get(1)
            val country = M3uParser.countryOf(entry.tvgId)
            return Channel(
                key = entry.tvgId ?: "url:" + entry.url.hashCode().toUInt().toString(16),
                name = M3uParser.cleanTitle(entry.title),
                logo = entry.logo,
                country = country,
                group = Sources.groupFor(fromNews, playlistCountry ?: country, entry.groupTitle),
                url = entry.url,
                userAgent = entry.userAgent,
                referrer = entry.referrer,
                notAlways = M3uParser.isNotAlways(entry.title),
            )
        }
    }
}
