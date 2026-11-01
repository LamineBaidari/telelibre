package com.laminebaidari.telelibre.data

import java.io.BufferedReader

data class M3uEntry(
    val tvgId: String?,
    val title: String,
    val logo: String?,
    val groupTitle: String?,
    val url: String,
    val userAgent: String?,
    val referrer: String?,
)

/** Streaming parser for iptv-org playlists: never holds more than one entry in memory. */
object M3uParser {
    private val ATTR = Regex("""([A-Za-z-]+)="([^"]*)"""")

    fun parse(reader: BufferedReader): Sequence<M3uEntry> = sequence {
        var inf: String? = null
        var userAgent: String? = null
        var referrer: String? = null
        while (true) {
            val line = reader.readLine()?.trim() ?: break
            when {
                line.startsWith("#EXTINF") -> {
                    inf = line
                    userAgent = null
                    referrer = null
                }
                line.startsWith("#EXTVLCOPT") -> {
                    val option = line.substringAfter(':')
                    val value = option.substringAfter('=', "")
                    when (option.substringBefore('=')) {
                        "http-user-agent" -> userAgent = value
                        "http-referrer" -> referrer = value
                    }
                }
                line.isNotEmpty() && !line.startsWith("#") && inf != null -> {
                    val attrs = ATTR.findAll(inf).associate { it.groupValues[1] to it.groupValues[2] }
                    yield(
                        M3uEntry(
                            tvgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() },
                            title = inf.substringAfterLast(',').trim(),
                            logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() },
                            groupTitle = attrs["group-title"]?.takeIf { it.isNotBlank() },
                            url = line,
                            userAgent = userAgent,
                            referrer = referrer,
                        )
                    )
                    inf = null
                }
            }
        }
    }

    private val TAGS = Regex("""\s*(\([^)]*\)|\[[^]]*])""")

    /** "France 24 (1080p) [Not 24/7]" -> "France 24" */
    fun cleanTitle(title: String): String = TAGS.replace(title, "").trim().ifEmpty { title }

    fun isNotAlways(title: String) = title.contains("[Not 24/7]")

    /** "A2iMusic.sn@SD" -> "sn" */
    fun countryOf(tvgId: String?): String? =
        tvgId?.substringBefore('@')?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.length == 2 }
}
