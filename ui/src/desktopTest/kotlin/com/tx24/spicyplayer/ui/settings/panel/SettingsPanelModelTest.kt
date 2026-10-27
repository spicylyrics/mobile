package com.tx24.spicyplayer.ui.settings.panel

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPanelModelTest {
    private val leadTime = SliderSpec(0, 800, 10, default = 250, unit = "ms")
    private val offset = SliderSpec(-5000, 5000, 10, default = 0, unit = "ms")

    @Test
    fun `clamp holds to the range and snaps to the step`() {
        assertEquals(0, leadTime.clamp(-5))
        assertEquals(800, leadTime.clamp(900))
        assertEquals(250, leadTime.clamp(254))
        assertEquals(260, leadTime.clamp(255))
        assertEquals(15, SliderSpec(15, 240, 5).clamp(16))
    }

    @Test
    fun `format adds the unit, and a plus sign only across zero`() {
        assertEquals("250 ms", leadTime.format(250))
        assertEquals("+40 ms", offset.format(40))
        assertEquals("-40 ms", offset.format(-40))
        assertEquals("0 ms", offset.format(0))
        assertEquals("12", SliderSpec(0, 67).format(12))
    }

    @Test
    fun `fraction and valueAt round trip along the track`() {
        assertEquals(0f, leadTime.fraction(0), 0f)
        assertEquals(1f, leadTime.fraction(800), 0f)
        assertEquals(400, leadTime.valueAt(0.5f))
        assertEquals(0, leadTime.valueAt(-1f))
        assertEquals(800, leadTime.valueAt(2f))
        assertEquals(0.5f, offset.zeroFraction, 0f)
        assertEquals(0f, leadTime.zeroFraction, 0f)
    }

    private fun toggle(id: String, label: String, description: String?, keywords: String? = null) =
        PanelRow.Toggle(id, label, description, keywords = keywords, checked = { false }, onChange = {})

    @Test
    fun `search needs every term in the row's own text`() {
        val row = toggle("a", "Early Scroll", "Start scrolling just before the line.", keywords = "lead ahead")
        assertTrue(matchesRow("early", row))
        assertTrue(matchesRow("scroll lead", row))
        assertTrue(matchesRow("  ", row))
        assertFalse(matchesRow("scroll volume", row))
    }

    @Test
    fun `label hits come before description hits`() {
        val byText = toggle("a", "Smooth Scrolling", "Glide from line to line.")
        val byLabel = toggle("b", "Duet Line Padding", null)
        val section = PanelSection(PanelSectionId.Lyrics, "Lyrics", PanelIcons.Lyrics, listOf(PanelGroup(null, listOf(byText, byLabel))))
        val hits = searchPanel("line", listOf(section))
        assertEquals(listOf("b", "a"), hits.single().rows.map { it.id })
        assertTrue(searchPanel("nothing matches", listOf(section)).isEmpty())
    }

    @Test
    fun `highlight ranges cover each term and merge where they touch`() {
        assertEquals(listOf(0..4, 6..11), highlightRanges("Early Scroll", "early scroll"))
        assertEquals(listOf(0..4), highlightRanges("Early", "ea rly"))
        assertTrue(highlightRanges("Early", "").isEmpty())
    }

    @Test
    fun `big-screen settings take over from 840dp wide and 480dp tall`() {
        // A phone upright, a tablet in portrait, a window just short of the line.
        assertFalse(usesBigScreenSettings(411.dp, 891.dp))
        assertFalse(usesBigScreenSettings(800.dp, 1280.dp))
        assertFalse(usesBigScreenSettings(839.dp, 700.dp))
        // A big phone on its side: wide enough, but too short.
        assertFalse(usesBigScreenSettings(915.dp, 411.dp))
        // A tablet in landscape, an unfolded foldable on its side, a DeX window.
        assertTrue(usesBigScreenSettings(1280.dp, 800.dp))
        assertTrue(usesBigScreenSettings(840.dp, 673.dp))
        assertTrue(usesBigScreenSettings(1024.dp, 480.dp))
    }

    @Test
    fun `layout is one pane below 600dp, compact below 900dp, else wide`() {
        // A phone in portrait, a small window, a tablet in portrait, a desktop window.
        assertEquals(PanelLayout.SinglePane, panelLayoutFor(360.dp))
        assertEquals(PanelLayout.SinglePane, panelLayoutFor(599.dp))
        assertEquals(PanelLayout.Compact, panelLayoutFor(600.dp))
        assertEquals(PanelLayout.Compact, panelLayoutFor(899.dp))
        assertEquals(PanelLayout.Wide, panelLayoutFor(900.dp))
        assertEquals(PanelLayout.Wide, panelLayoutFor(1400.dp))
        assertEquals(184.dp, PanelLayout.Compact.railWidth)
        assertEquals(22.dp, PanelLayout.Compact.gutter)
        assertEquals(216.dp, PanelLayout.Wide.railWidth)
        assertEquals(32.dp, PanelLayout.Wide.gutter)
    }
}
