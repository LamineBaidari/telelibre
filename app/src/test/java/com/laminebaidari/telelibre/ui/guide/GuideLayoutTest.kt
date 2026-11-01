package com.laminebaidari.telelibre.ui.guide

import com.laminebaidari.telelibre.model.Programme
import org.junit.Assert.assertEquals
import org.junit.Test

class GuideLayoutTest {
    private val min = 60_000L
    private val start = 1_793_512_800_000L // 06:00 UTC, a half-hour boundary

    @Test
    fun windowStartsOnTheHalfHour() {
        assertEquals(start, GuideLayout.windowStart(start + 17 * min))
        assertEquals(start + 30 * min, GuideLayout.windowStart(start + 30 * min))
    }

    @Test
    fun clipsProgrammesAndFillsGaps() {
        val programmes = listOf(
            Programme("Matin", start - 60 * min, start + 30 * min), // starts before the window
            Programme("Journal", start + 60 * min, start + 90 * min), // 30 min gap before it
            Programme("Film", start + 120 * min, start + 240 * min), // runs past the end (150 min)
        )
        val cells = GuideLayout.cells(programmes, start, 1500) // 10 px per minute
        assertEquals(listOf("Matin", null, "Journal", null, "Film"), cells.map { it.programme?.title })
        assertEquals(listOf(0, 300, 600, 900, 1200), cells.map { it.left })
        assertEquals(listOf(300, 300, 300, 300, 300), cells.map { it.width })
    }

    @Test
    fun emptyGuideIsOneEmptyCell() {
        val cells = GuideLayout.cells(emptyList(), start, 1500)
        assertEquals(1, cells.size)
        assertEquals(1500, cells.single().width)
    }

    @Test
    fun overlappingProgrammesDoNotOverlapCells() {
        val programmes = listOf(
            Programme("A", start, start + 60 * min),
            Programme("B", start + 30 * min, start + 90 * min),
        )
        val cells = GuideLayout.cells(programmes, start, 1500)
        assertEquals(listOf("A", "B", null), cells.map { it.programme?.title })
        assertEquals(listOf(0, 600, 900), cells.map { it.left })
    }
}
