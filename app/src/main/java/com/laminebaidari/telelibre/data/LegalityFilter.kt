package com.laminebaidari.telelibre.data

import java.net.URI

/**
 * Keeps only streams served by the broadcaster or a free ad-supported platform.
 * Relays on bare IP addresses or on [Sources.DENIED_HOST_SUFFIXES] are rejected.
 */
object LegalityFilter {
    private val IPV4 = Regex("""^[\d.]+$""")

    fun allowed(url: String): Boolean {
        val host = runCatching { URI(url).host }.getOrNull()?.lowercase() ?: return false
        if (IPV4.matches(host) || host.startsWith("[")) return false
        return Sources.DENIED_HOST_SUFFIXES.none { host == it || host.endsWith(".$it") }
    }
}
