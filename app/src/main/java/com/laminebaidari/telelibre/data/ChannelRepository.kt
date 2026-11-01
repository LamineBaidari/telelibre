package com.laminebaidari.telelibre.data

import android.content.Context
import android.util.AtomicFile
import com.laminebaidari.telelibre.model.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Cached channel list, favourites, last channel and channels found dead while playing. */
class ChannelRepository(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "channels.json"))
    private val prefs = context.getSharedPreferences("telelibre", Context.MODE_PRIVATE)

    private val all = MutableStateFlow(load())
    private val dead = MutableStateFlow(prefs.getStringSet(KEY_DEAD, emptySet())!!.toSet())

    private val _visible = MutableStateFlow(computeVisible())
    /** Channels to show: the cached list minus channels that failed while playing. */
    val channels: StateFlow<List<Channel>> = _visible.asStateFlow()

    private val _favourites = MutableStateFlow(prefs.getStringSet(KEY_FAVOURITES, emptySet())!!.toSet())
    val favourites: StateFlow<Set<String>> = _favourites.asStateFlow()

    val lastRefresh: Long get() = prefs.getLong(KEY_LAST_REFRESH, 0L)

    var lastChannelKey: String?
        get() = prefs.getString(KEY_LAST_CHANNEL, null)
        set(value) = prefs.edit().putString(KEY_LAST_CHANNEL, value).apply()

    fun find(key: String): Channel? = _visible.value.firstOrNull { it.key == key }

    fun inGroup(group: String): List<Channel> = _visible.value.filter { it.group == group }

    fun favouriteChannels(): List<Channel> = _favourites.value.let { favs -> _visible.value.filter { it.key in favs } }

    /** Returns true when the channel is now a favourite. */
    fun toggleFavourite(key: String): Boolean {
        val updated = _favourites.value.toMutableSet()
        val added = key !in updated
        if (added) updated.add(key) else updated.remove(key)
        _favourites.value = updated
        prefs.edit().putStringSet(KEY_FAVOURITES, updated).apply()
        return added
    }

    fun markDead(key: String) {
        dead.value = dead.value + key
        prefs.edit().putStringSet(KEY_DEAD, dead.value).apply()
        _visible.value = computeVisible()
    }

    /** Replaces the cache after a successful refresh; dead marks start over. */
    fun replaceAll(channels: List<Channel>) {
        save(channels)
        all.value = channels
        dead.value = emptySet()
        prefs.edit().remove(KEY_DEAD).putLong(KEY_LAST_REFRESH, System.currentTimeMillis()).apply()
        _visible.value = computeVisible()
    }

    private fun computeVisible() = all.value.filter { it.key !in dead.value }

    private fun load(): List<Channel> = try {
        val array = JSONArray(String(file.readFully()))
        List(array.length()) { i ->
            val o = array.getJSONObject(i)
            Channel(
                key = o.getString("key"),
                name = o.getString("name"),
                logo = o.optString("logo").ifEmpty { null },
                country = o.optString("country").ifEmpty { null },
                group = o.getString("group"),
                url = o.getString("url"),
                userAgent = o.optString("ua").ifEmpty { null },
                referrer = o.optString("ref").ifEmpty { null },
                notAlways = o.optBoolean("notAlways"),
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun save(channels: List<Channel>) {
        val array = JSONArray()
        for (c in channels) {
            array.put(
                JSONObject()
                    .put("key", c.key).put("name", c.name).put("logo", c.logo ?: "")
                    .put("country", c.country ?: "").put("group", c.group).put("url", c.url)
                    .put("ua", c.userAgent ?: "").put("ref", c.referrer ?: "")
                    .put("notAlways", c.notAlways)
            )
        }
        val out = file.startWrite()
        try {
            out.write(array.toString().toByteArray())
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }

    companion object {
        private const val KEY_DEAD = "dead"
        private const val KEY_FAVOURITES = "favourites"
        private const val KEY_LAST_REFRESH = "lastRefresh"
        private const val KEY_LAST_CHANNEL = "lastChannel"
    }
}
