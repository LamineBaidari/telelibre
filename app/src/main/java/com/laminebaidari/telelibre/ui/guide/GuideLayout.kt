package com.laminebaidari.telelibre.ui.guide

import com.laminebaidari.telelibre.model.Programme

/** Placement of programmes on the guide's time axis: pure math, kept apart from views so it can be tested. */
object GuideLayout {
    const val HALF_HOUR = 30 * 60_000L
    /** The grid shows two and a half hours at a time. */
    const val WINDOW = 5 * HALF_HOUR

    data class Cell(val programme: Programme?, val left: Int, val width: Int)

    /** Start of the half-hour containing [time]. */
    fun windowStart(time: Long): Long = time - Math.floorMod(time, HALF_HOUR)

    /**
     * Cells for one channel between [start] and start + [WINDOW], [widthPx] wide.
     * Programmes are clipped to the window and gaps become empty cells (programme = null), so the row has no holes.
     */
    fun cells(programmes: List<Programme>, start: Long, widthPx: Int): List<Cell> {
        val end = start + WINDOW
        fun x(time: Long) = ((time.coerceIn(start, end) - start) * widthPx / WINDOW).toInt()
        val out = ArrayList<Cell>()
        var cursor = start
        for (p in programmes.sortedBy { it.start }) {
            if (p.stop <= start || p.start >= end || p.stop <= cursor) continue
            if (p.start > cursor) out += Cell(null, x(cursor), x(p.start) - x(cursor))
            val from = maxOf(p.start, cursor)
            out += Cell(p, x(from), x(p.stop) - x(from))
            cursor = p.stop
        }
        if (cursor < end) out += Cell(null, x(cursor), widthPx - x(cursor))
        return out.filter { it.width > 0 }
    }
}
