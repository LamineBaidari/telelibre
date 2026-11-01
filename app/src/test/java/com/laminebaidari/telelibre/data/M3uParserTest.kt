package com.laminebaidari.telelibre.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {
    private fun sample() = javaClass.classLoader!!.getResourceAsStream("sample.m3u")!!.bufferedReader()

    @Test
    fun parsesEveryEntryOfTheRealPlaylist() {
        val entries = M3uParser.parse(sample()).toList()
        assertEquals(151, entries.size)
        val first = entries.first()
        assertEquals("A2iMusic.sn@SD", first.tvgId)
        assertEquals("A2i Music (720p) [Not 24/7]", first.title)
        assertEquals("https://stream.sen-gt.com/A2iMusic/myStream/playlist.m3u8", first.url)
        assertTrue(first.logo!!.startsWith("https://"))
    }

    @Test
    fun readsVlcOptions() {
        val text = """
            #EXTM3U
            #EXTINF:-1 tvg-id="X.fr@SD" group-title="News",X TV (720p)
            #EXTVLCOPT:http-referrer=https://x.example/
            #EXTVLCOPT:http-user-agent=Mozilla/5.0
            https://x.example/live.m3u8
        """.trimIndent()
        val entry = M3uParser.parse(text.reader().buffered()).single()
        assertEquals("https://x.example/", entry.referrer)
        assertEquals("Mozilla/5.0", entry.userAgent)
        assertEquals("News", entry.groupTitle)
    }

    @Test
    fun cleansTitlesAndCountries() {
        assertEquals("France 24", M3uParser.cleanTitle("France 24 (1080p) [Geo-blocked]"))
        assertTrue(M3uParser.isNotAlways("A2i Music (720p) [Not 24/7]"))
        assertFalse(M3uParser.isNotAlways("A2i TV (1080p)"))
        assertEquals("sn", M3uParser.countryOf("A2iMusic.sn@SD"))
        assertEquals("qa", M3uParser.countryOf("AlJazeera.qa@English"))
        assertNull(M3uParser.countryOf(null))
    }

    @Test
    fun legalityFilterRejectsRelays() {
        assertFalse(LegalityFilter.allowed("http://177.107.1.2:8080/tf1/index.m3u8"))
        assertFalse(LegalityFilter.allowed("https://raw.githubusercontent.com/x/y/cnews.m3u8"))
        assertFalse(LegalityFilter.allowed("https://live.mcquack.net/mezzo.m3u8"))
        assertTrue(LegalityFilter.allowed("https://jmp2.uk/plu-5f8ed1ff5c39700007e2204a.m3u8"))
        assertTrue(LegalityFilter.allowed("https://stream.sen-gt.com/A2itv/myStream/playlist.m3u8"))
        assertFalse(LegalityFilter.allowed("not a url"))
    }

    @Test
    fun groupsChannels() {
        val sn = M3uEntry("A2iTV.it@SD", "A2i TV (1080p)", null, "General", "https://stream.sen-gt.com/a.m3u8", null, null)
        // Listed in the Senegal playlist, so it goes to the Senegal row even with an Italian id.
        assertEquals(Sources.GROUP_SENEGAL, ChannelRefresher.toChannel("https://iptv-org.github.io/iptv/countries/sn.m3u", sn)!!.group)

        val news = M3uEntry("DW.de@English", "DW English (1080p)", null, "News", "https://dwamdstream.akamaized.net/a.m3u8", null, null)
        assertEquals(Sources.GROUP_WORLD_NEWS, ChannelRefresher.toChannel(Sources.NEWS_PLAYLIST, news)!!.group)
        val notAllowed = news.copy(tvgId = "SomeLocalNews.us@SD")
        assertNull(ChannelRefresher.toChannel(Sources.NEWS_PLAYLIST, notAllowed))

        val fr = M3uEntry("Trace.fr@Africa", "Trace Africa", null, "Music", "https://trace.plus/a.m3u8", null, null)
        assertEquals("Musique", ChannelRefresher.toChannel("https://iptv-org.github.io/iptv/languages/fra.m3u", fr)!!.group)

        val relay = fr.copy(url = "http://51.147.1.1/m6.m3u8")
        assertNull(ChannelRefresher.toChannel("https://iptv-org.github.io/iptv/languages/fra.m3u", relay))
    }
}
