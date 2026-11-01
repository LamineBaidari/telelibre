package com.laminebaidari.telelibre.epg

import com.laminebaidari.telelibre.model.Channel
import com.laminebaidari.telelibre.model.Programme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XmltvParserTest {
    private fun channel(key: String, name: String, url: String = "https://example.com/a.m3u8") =
        Channel(key, name, null, null, "Actualités", url, null, null, false)

    @Test
    fun parsesTimesWithOffsets() {
        assertEquals(1_793_512_800_000L, XmltvParser.parseTime("20261101060000 +0000"))
        assertEquals(1_793_512_800_000L, XmltvParser.parseTime("20261101070000 +0100"))
        assertEquals(1_793_512_800_000L, XmltvParser.parseTime("20261101010000 -0500"))
        assertNull(XmltvParser.parseTime("garbage"))
    }

    @Test
    fun normalizesNames() {
        assertEquals("bfmtv", EpgMatch.normalize("BFM TV (1080p)"))
        assertEquals("telesenegal", EpgMatch.normalize("Télé Sénégal HD"))
        assertEquals("5f8ed1ff5c39700007e2204a", EpgMatch.plutoId("https://jmp2.uk/plu-5f8ed1ff5c39700007e2204a.m3u8"))
        assertNull(EpgMatch.plutoId("https://example.com/plu-123.m3u8"))
    }

    @Test
    fun keepsWantedProgrammesInWindow() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <channel id="BFMTV.fr"><display-name>BFM TV</display-name></channel>
              <channel id="5f8ed1ff5c39700007e2204a"><display-name>Pluto Polar</display-name></channel>
              <channel id="Other.fr"><display-name>Other</display-name></channel>
              <programme start="20261101060000 +0000" stop="20261101070000 +0000" channel="BFMTV.fr">
                <title lang="fr">Première édition</title><desc>…</desc>
              </programme>
              <programme start="20261101070000 +0000" stop="20261101080000 +0000" channel="BFMTV.fr">
                <title>BFM Story</title>
              </programme>
              <programme start="20261105070000 +0000" stop="20261105080000 +0000" channel="BFMTV.fr">
                <title>Too far ahead</title>
              </programme>
              <programme start="20261101060000 +0000" stop="20261101070000 +0000" channel="5f8ed1ff5c39700007e2204a">
                <title>Columbo</title>
              </programme>
              <programme start="20261101060000 +0000" stop="20261101070000 +0000" channel="Other.fr">
                <title>Not ours</title>
              </programme>
            </tv>
        """.trimIndent()
        val channels = listOf(
            channel("BFMTV.fr@SD", "BFM TV (1080p)"),
            channel("Polar.us@FR", "Pluto TV Polar", "https://jmp2.uk/plu-5f8ed1ff5c39700007e2204a.m3u8"),
        )
        val wanted = HashMap<String, MutableList<String>>()
        channels.forEach { c -> EpgMatch.channelKeys(c).forEach { wanted.getOrPut(it) { ArrayList() }.add(c.key) } }
        val out = HashMap<String, MutableList<Programme>>()
        val matched = HashSet<String>()
        val from = XmltvParser.parseTime("20261101050000 +0000")!!
        XmltvParser(wanted, matched, from, from + 24 * 3_600_000L).parse(xml.byteInputStream(), out)

        assertEquals(listOf("Première édition", "BFM Story"), out["BFMTV.fr@SD"]!!.map { it.title })
        assertEquals(listOf("Columbo"), out["Polar.us@FR"]!!.map { it.title })
        assertFalse(out.values.flatten().any { it.title == "Not ours" || it.title == "Too far ahead" })
        assertTrue("BFMTV.fr@SD" in matched)
    }
}
