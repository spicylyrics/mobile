package com.tx24.spicyplayer.widget

import com.tx24.spicyplayer.lyrics.TimedLine
import com.tx24.spicyplayer.lyrics.TimedWord
import com.tx24.spicyplayer.lyrics.spicy.models.LineRole
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetLinesTest {
    private fun line(startMs: Long, endMs: Long, vararg words: String, role: LineRole = LineRole.LEAD) =
        TimedLine(startMs, endMs, words.map { TimedWord(it, startMs, endMs, attached = false) }, role = role)

    private val leads = WidgetLines.leads(
        listOf(
            line(1_000, 3_000, "Hello ", "world "),
            line(2_000, 3_000, "(echo)", role = LineRole.BACKGROUND),
            line(3_000, 5_000, "Second"),
            line(12_000, 14_000, "After  ", " the break"),
        ),
    )

    @Test
    fun `background vocals are left out and words keep their own spacing`() {
        assertEquals(listOf("Hello world", "Second", "After the break"), leads.map { it.text })
    }

    @Test
    fun `before the first line the break shows with the first line next`() {
        assertEquals(WidgetLines(WidgetLines.BREAK, "Hello world", 1_000), WidgetLines.at(leads, 0))
    }

    @Test
    fun `a line shows until the next one starts`() {
        assertEquals(WidgetLines("Hello world", "Second", 3_000), WidgetLines.at(leads, 2_500))
    }

    @Test
    fun `a long gap shows the break once the line ends`() {
        assertEquals(WidgetLines("Second", "After the break", 5_000), WidgetLines.at(leads, 4_000))
        assertEquals(WidgetLines(WidgetLines.BREAK, "After the break", 12_000), WidgetLines.at(leads, 6_000))
    }

    @Test
    fun `the last line stays with nothing after it`() {
        assertEquals(WidgetLines("After the break", "", null), WidgetLines.at(leads, 20_000))
    }

    @Test
    fun `unsynced lyrics show their first two lines`() {
        assertEquals(WidgetLines("Hello world", "Second", null), WidgetLines.unsynced(leads))
    }
}
