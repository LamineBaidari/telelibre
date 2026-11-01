package com.laminebaidari.telelibre.epg

import com.laminebaidari.telelibre.model.Channel
import java.net.URI
import java.text.Normalizer

/** Keys used to pair our channels with XMLTV channels: by id, Pluto id, or normalized name. */
object EpgMatch {
    private val TAGS = Regex("""\([^)]*\)|\[[^]]*]""")
    private val NON_ALNUM = Regex("[^a-z0-9]")
    private val ACCENTS = Regex("\\p{M}+")

    /** "BFM TV (1080p)" -> "bfmtv", "Télé Sénégal HD" -> "telesenegal" */
    fun normalize(name: String): String {
        val plain = Normalizer.normalize(TAGS.replace(name, ""), Normalizer.Form.NFD).replace(ACCENTS, "")
        val compact = NON_ALNUM.replace(plain.lowercase(), "")
        return if (compact.length > 4 && compact.endsWith("hd")) compact.dropLast(2) else compact
    }

    fun channelKeys(channel: Channel): Set<String> = buildSet {
        if (!channel.key.startsWith("url:")) add("id:" + channel.key.substringBefore('@').lowercase())
        plutoId(channel.url)?.let { add("id:$it") }
        normalize(channel.name).takeIf { it.isNotEmpty() }?.let { add("name:$it") }
    }

    fun xmltvKeys(id: String, displayNames: List<String>): Set<String> = buildSet {
        add("id:" + id.lowercase())
        // epgshare01 uses ids like "BFM2.fr"; compare without the country suffix too.
        normalize(id.replace(Regex("""\.[a-z]{2}$"""), "")).takeIf { it.isNotEmpty() }?.let { add("name:$it") }
        displayNames.forEach { n -> normalize(n).takeIf { it.isNotEmpty() }?.let { add("name:$it") } }
    }

    /** "https://jmp2.uk/plu-5f8ed1ff5c39700007e2204a.m3u8" -> "5f8ed1ff5c39700007e2204a" */
    fun plutoId(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (uri.host != "jmp2.uk") return null
        val path = uri.path ?: return null
        if (!path.startsWith("/plu-")) return null
        return path.removePrefix("/plu-").substringBefore('.').lowercase().ifEmpty { null }
    }
}
