package com.laminebaidari.telelibre.epg

import com.laminebaidari.telelibre.model.Programme
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.Calendar
import java.util.TimeZone

/**
 * Streams an XMLTV file and keeps only programmes of wanted channels within [from, to].
 *
 * @param wanted match key (see [EpgMatch]) -> our channel keys
 * @param alreadyMatched our channels that took their guide from an earlier source; updated in place
 * @param out our channel key -> programmes; filled in place
 */
class XmltvParser(
    private val wanted: Map<String, List<String>>,
    private val alreadyMatched: MutableSet<String>,
    private val from: Long,
    private val to: Long,
) {
    fun parse(input: InputStream, out: MutableMap<String, MutableList<Programme>>) {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(input, null)
        // XMLTV channel id -> our channel keys, for channels matched by this source.
        val mapped = HashMap<String, List<String>>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "channel" -> readChannel(parser, mapped)
                "programme" -> readProgramme(parser, mapped, out)
            }
        }
    }

    private fun readChannel(parser: XmlPullParser, mapped: HashMap<String, List<String>>) {
        val id = parser.getAttributeValue(null, "id") ?: return skip(parser)
        val names = ArrayList<String>()
        val depth = parser.depth
        while (nextInside(parser, depth)) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "display-name") {
                names.add(parser.nextText())
            }
        }
        val ours = EpgMatch.xmltvKeys(id, names)
            .flatMap { wanted[it].orEmpty() }
            .filter { it !in alreadyMatched }
            .distinct()
        if (ours.isNotEmpty()) mapped[id] = ours
    }

    private fun readProgramme(
        parser: XmlPullParser,
        mapped: Map<String, List<String>>,
        out: MutableMap<String, MutableList<Programme>>,
    ) {
        val ours = mapped[parser.getAttributeValue(null, "channel")]
        val start = parser.getAttributeValue(null, "start")?.let(::parseTime)
        val stop = parser.getAttributeValue(null, "stop")?.let(::parseTime)
        if (ours == null || start == null || stop == null || stop < from || start > to) return skip(parser)
        var title: String? = null
        val depth = parser.depth
        while (nextInside(parser, depth)) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "title" && title == null) {
                title = parser.nextText().trim()
            }
        }
        if (title.isNullOrEmpty()) return
        val programme = Programme(title, start, stop)
        for (key in ours) {
            out.getOrPut(key) { ArrayList() }.add(programme)
            alreadyMatched.add(key)
        }
    }

    /** Advances and returns false once the element opened at [depth] is closed (or the file ends). */
    private fun nextInside(parser: XmlPullParser, depth: Int): Boolean {
        val event = parser.next()
        return event != XmlPullParser.END_DOCUMENT && !(event == XmlPullParser.END_TAG && parser.depth == depth)
    }

    private fun skip(parser: XmlPullParser) {
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    companion object {
        private val UTC = TimeZone.getTimeZone("UTC")

        /** "20261101074400 +0100" -> epoch millis. Parsed by hand: SimpleDateFormat is slow here. */
        fun parseTime(value: String): Long? {
            val s = value.trim()
            if (s.length < 14 || !s.substring(0, 14).all(Char::isDigit)) return null
            val cal = Calendar.getInstance(UTC)
            cal.clear()
            cal.set(
                s.substring(0, 4).toInt(), s.substring(4, 6).toInt() - 1, s.substring(6, 8).toInt(),
                s.substring(8, 10).toInt(), s.substring(10, 12).toInt(), s.substring(12, 14).toInt(),
            )
            var millis = cal.timeInMillis
            val zone = s.substring(14).trim()
            if (zone.length == 5 && (zone[0] == '+' || zone[0] == '-') && zone.substring(1).all(Char::isDigit)) {
                val offset = (zone.substring(1, 3).toInt() * 60 + zone.substring(3, 5).toInt()) * 60_000L
                millis -= if (zone[0] == '+') offset else -offset
            }
            return millis
        }
    }
}
