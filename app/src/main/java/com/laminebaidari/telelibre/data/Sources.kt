package com.laminebaidari.telelibre.data

/** Where channels and programme guides come from, and how they are grouped. */
object Sources {
    private const val IPTV_ORG = "https://iptv-org.github.io/iptv/"

    const val NEWS_PLAYLIST = IPTV_ORG + "categories/news.m3u"

    val AFRICA = listOf("ci", "cm", "ml", "bf", "gn", "bj", "tg", "ne", "cd", "cg", "ga", "mg")

    /** Order matters: when the same URL appears twice, the first playlist decides the row. */
    val PLAYLISTS: List<String> = buildList {
        add(IPTV_ORG + "countries/sn.m3u")
        add(NEWS_PLAYLIST)
        AFRICA.forEach { add(IPTV_ORG + "countries/$it.m3u") }
        add(IPTV_ORG + "countries/fr.m3u")
        add(IPTV_ORG + "languages/fra.m3u")
    }

    /** Only these tvg-ids are taken from the (worldwide) news playlist. */
    val NEWS_ALLOWLIST = setOf(
        "AfricanewsEnglish.fr@SD", "AfricanewsFrench.fr@SD",
        "AlJazeera.qa@English", "AlJazeera.qa@Arabic",
        "BBCNews.uk@Africa", "BBCArabic.uk@SD",
        "CGTN.cn@SD", "CGTNFrench.cn@SD",
        "CNAInternational.sg@SD",
        "DW.de@English", "DW.de@Arabic",
        "EuronewsEnglish.fr@SD",
        "France24.fr@English", "France24.fr@Arabic",
        "NHKWorldJapan.jp@HD", "NHKWorldJapan.jp@SD",
        "SkyNewsArabia.ae@HD",
        "TRTWorld.tr@SD", "TRTWorld.tr@HD",
    )

    /**
     * Legality guard: these hosts relay channels that are not offered freely by their owners.
     * Bare IP addresses are rejected separately in [LegalityFilter].
     */
    val DENIED_HOST_SUFFIXES = listOf("githubusercontent.com", "mcquack.net")

    /** XMLTV guides, in priority order (a channel takes its guide from the first match). */
    val EPG = listOf(
        "https://i.mjh.nz/PlutoTV/fr.xml.gz",
        "https://i.mjh.nz/SamsungTVPlus/fr.xml.gz",
        "https://xmltvfr.fr/xmltv/xmltv_tnt.xml.gz",
        "https://epgshare01.online/epgshare01/epg_ripper_FR1.xml.gz",
    )

    const val GROUP_SENEGAL = "Sénégal"
    const val GROUP_AFRICA = "Afrique francophone"
    const val GROUP_WORLD_NEWS = "Infos internationales"
    const val GROUP_RADIO = "Radio"

    private val CATEGORY_GROUPS = mapOf(
        "news" to "Actualités",
        "general" to "Généralistes",
        "movies" to "Films",
        "series" to "Séries",
        "kids" to "Jeunesse",
        "animation" to "Jeunesse",
        "music" to "Musique",
        "sports" to "Sport",
        "documentary" to "Découverte",
        "education" to "Découverte",
        "science" to "Découverte",
        "travel" to "Découverte",
        "outdoor" to "Découverte",
        "religious" to "Religion",
    )
    private const val GROUP_ENTERTAINMENT = "Divertissement"
    private const val GROUP_OTHER = "Autres"
    private val ENTERTAINMENT = setOf(
        "entertainment", "comedy", "lifestyle", "cooking", "family", "classic", "culture",
        "shop", "auto", "business", "relax",
    )

    /** Row order in the browse screen (favourites are always first). */
    val GROUP_ORDER = listOf(
        GROUP_SENEGAL, GROUP_AFRICA, "Actualités", GROUP_WORLD_NEWS, GROUP_RADIO, "Généralistes", "Films",
        "Séries", "Jeunesse", "Musique", "Sport", "Découverte", GROUP_ENTERTAINMENT, "Religion",
        GROUP_OTHER,
    )

    fun groupFor(fromNewsPlaylist: Boolean, country: String?, groupTitle: String?): String {
        if (fromNewsPlaylist) return GROUP_WORLD_NEWS
        if (country == "sn") return GROUP_SENEGAL
        if (country in AFRICA) return GROUP_AFRICA
        val category = groupTitle?.split(';')?.firstOrNull()?.trim()?.lowercase().orEmpty()
        CATEGORY_GROUPS[category]?.let { return it }
        return if (category in ENTERTAINMENT) GROUP_ENTERTAINMENT else GROUP_OTHER
    }
}
