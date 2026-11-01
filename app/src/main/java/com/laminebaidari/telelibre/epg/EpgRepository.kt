package com.laminebaidari.telelibre.epg

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.laminebaidari.telelibre.data.Sources
import com.laminebaidari.telelibre.model.Channel
import com.laminebaidari.telelibre.model.Programme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.GZIPInputStream

/** Now/next programme data for the next 24 hours, downloaded from the XMLTV sources. */
class EpgRepository(context: Context, private val client: OkHttpClient) {
    private val file = AtomicFile(File(context.filesDir, "epg.json"))
    private val prefs = context.getSharedPreferences("telelibre", Context.MODE_PRIVATE)

    private val _guide = MutableStateFlow(load())
    val guide: StateFlow<Map<String, List<Programme>>> = _guide.asStateFlow()

    val lastRefresh: Long get() = prefs.getLong(KEY_LAST_REFRESH, 0L)

    fun nowNext(key: String, now: Long = System.currentTimeMillis()): Pair<Programme?, Programme?> {
        val list = _guide.value[key] ?: return null to null
        val index = list.indexOfFirst { it.stop > now }
        if (index < 0) return null to null
        val first = list[index]
        return if (first.start <= now) first to list.getOrNull(index + 1) else null to first
    }

    suspend fun refresh(channels: List<Channel>) = withContext(Dispatchers.IO) {
        val wanted = HashMap<String, MutableList<String>>()
        for (c in channels) EpgMatch.channelKeys(c).forEach { wanted.getOrPut(it) { ArrayList() }.add(c.key) }
        val now = System.currentTimeMillis()
        val out = HashMap<String, MutableList<Programme>>()
        val matched = HashSet<String>()
        for (url in Sources.EPG) {
            try {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    GZIPInputStream(response.body!!.byteStream()).use {
                        XmltvParser(wanted, matched, now - HOUR, now + 24 * HOUR).parse(it, out)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Guide failed: $url", e)
            }
        }
        val cleaned = out.mapValues { (_, list) ->
            list.sortedBy { it.start }.distinctBy { it.start }
        }
        Log.i(TAG, "Guide for ${cleaned.size} of ${channels.size} channels")
        if (cleaned.isNotEmpty()) {
            save(cleaned)
            _guide.value = cleaned
            prefs.edit().putLong(KEY_LAST_REFRESH, now).apply()
        }
    }

    private fun load(): Map<String, List<Programme>> = try {
        val root = JSONObject(String(file.readFully()))
        root.keys().asSequence().associateWith { key ->
            val array = root.getJSONArray(key)
            List(array.length()) { i ->
                val p = array.getJSONArray(i)
                Programme(p.getString(2), p.getLong(0), p.getLong(1))
            }
        }
    } catch (e: Exception) {
        emptyMap()
    }

    private fun save(guide: Map<String, List<Programme>>) {
        val root = JSONObject()
        for ((key, list) in guide) {
            root.put(key, JSONArray().apply { list.forEach { put(JSONArray().put(it.start).put(it.stop).put(it.title)) } })
        }
        val out = file.startWrite()
        try {
            out.write(root.toString().toByteArray())
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
        }
    }

    companion object {
        private const val TAG = "EpgRepository"
        private const val HOUR = 3_600_000L
        private const val KEY_LAST_REFRESH = "epgLastRefresh"
    }
}
