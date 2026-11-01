package com.laminebaidari.telelibre.data

import android.util.Log
import com.laminebaidari.telelibre.epg.EpgMatch
import com.laminebaidari.telelibre.model.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

/** Radio stations from the community Radio Browser directory: Senegal first, then a few French-language ones. */
class RadioRefresher(private val client: OkHttpClient) {

    /** Returns an empty list when every mirror failed, so the caller keeps what it has. */
    suspend fun refresh(): List<Channel> = withContext(Dispatchers.IO) {
        val senegal = fetch("stations/search?countrycode=SN&hidebroken=true&order=clickcount&reverse=true&limit=60")
            ?: return@withContext emptyList()
        val international = fetch("stations/byuuid?uuids=" + INTERNATIONAL.joinToString(",")).orEmpty()
        merge(parseStations(senegal).take(SENEGAL_LIMIT), parseStations(international))
    }

    private fun fetch(path: String): String? {
        for (mirror in MIRRORS) {
            try {
                val request = Request.Builder().url("https://$mirror/json/$path").header("User-Agent", USER_AGENT).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) return response.body!!.string()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Radio Browser $mirror failed", e)
            }
        }
        return null
    }

    companion object {
        private const val TAG = "RadioRefresher"
        private const val USER_AGENT = "TeleLibre/1.1 (Android TV)"
        private const val SENEGAL_LIMIT = 40
        private val MIRRORS = listOf("de1.api.radio-browser.info", "fi1.api.radio-browser.info", "nl1.api.radio-browser.info")

        /** RFI Afrique, RFI Monde, France Inter, Africa N°1. */
        private val INTERNATIONAL = listOf(
            "960c2914-0601-11e8-ae97-52543be04c81",
            "960c68f8-0601-11e8-ae97-52543be04c81",
            "0b80555f-eb5c-4fce-94d2-109eec7bee6b",
            "960ca944-0601-11e8-ae97-52543be04c81",
        )

        /** Plain MP3/AAC streams only: playlists (.m3u/.pls) and HLS need other handling. */
        private val PLAYABLE_CODECS = setOf("MP3", "AAC", "AAC+")

        fun parseStations(json: String): List<Channel> {
            val array = JSONArray(json)
            return (0 until array.length()).mapNotNull { i ->
                val o = array.getJSONObject(i)
                val url = o.optString("url_resolved").ifEmpty { o.optString("url") }
                val codec = o.optString("codec").uppercase()
                val name = o.optString("name").trim()
                when {
                    name.isEmpty() || url.isEmpty() -> null
                    codec !in PLAYABLE_CODECS -> null
                    o.optInt("lastcheckok", 1) != 1 -> null
                    !LegalityFilter.allowed(url) -> null
                    else -> station(o.getString("stationuuid"), name, o.optString("favicon").ifEmpty { null }, url)
                }
            }
        }

        /** Keeps the first station of each name (lists come sorted by popularity). */
        fun merge(vararg lists: List<Channel>): List<Channel> {
            val seen = HashSet<String>()
            return lists.flatMap { it }.filter { seen.add(EpgMatch.normalize(it.name)) }
        }

        private fun station(id: String, name: String, logo: String?, url: String) = Channel(
            key = "radio:$id", name = name, logo = logo?.takeIf { it.startsWith("http") }, country = null,
            group = Sources.GROUP_RADIO, url = url, userAgent = null, referrer = null, notAlways = false, radio = true,
        )

        /** Shown until the first successful download, so the Radio row is never empty. */
        val FALLBACK = listOf(
            station("sudfm", "Sud FM Sen Radio", null, "http://stream.zenolive.com/rq40edfn3reuv"),
            station("rsi", "RTS 92.5 RSI", null, "https://10gb1.acangroup.org:8000/rsi"),
            station("rewmi", "Rewmi FM 97.5 Dakar", null, "https://stream-61.zeno.fm/nkzsqg16t8quv"),
            station("fmsenegal", "FM Sénégal 103.1 Dakar", null, "https://stream-154.zeno.fm/t8gcyq6ts0quv"),
            station("lovefm", "Love FM 107.3 Dakar", null, "https://stream.zeno.fm/7cru7g7rcwzuv"),
            station("dakarmusique", "Dakar Musique", null, "http://listen.senemultimedia.net:8090/stream"),
            station("h24", "H24 Senegal", null, "https://listen.senemultimedia.net:8072/stream"),
            station("rfiafrique", "RFI Afrique", null, "http://live02.rfi.fr/rfiafrique-64.mp3"),
            station("rfimonde", "RFI Monde", null, "http://live02.rfi.fr/rfimonde-64.mp3"),
            station("franceinter", "France Inter", null, "https://icecast.radiofrance.fr/franceinter-hifi.aac"),
            station("africa1", "Africa N°1", null, "http://african1paris.ice.infomaniak.ch/african1paris-128.mp3"),
        )
    }
}
