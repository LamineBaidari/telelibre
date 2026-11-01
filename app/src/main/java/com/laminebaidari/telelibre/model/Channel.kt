package com.laminebaidari.telelibre.model

data class Channel(
    /** iptv-org tvg-id including the feed (e.g. "AlJazeera.qa@English"), or a hash of the URL. */
    val key: String,
    /** Display name, without "(720p)", "[Not 24/7]" and similar tags. */
    val name: String,
    val logo: String?,
    /** Country code from the tvg-id ("sn", "fr"…), if any. */
    val country: String?,
    /** Row the channel is shown in. */
    val group: String,
    val url: String,
    val userAgent: String?,
    val referrer: String?,
    /** iptv-org marks streams that are not on air all day with "[Not 24/7]". */
    val notAlways: Boolean,
)

data class Programme(val title: String, val start: Long, val stop: Long)
