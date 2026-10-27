package com.tx24.spicyplayer.desktop.page

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageStateTest {
    private val wide = 1400f
    private val narrow = 900f

    @Test
    fun `cinema button opens cinema and closes back to the page`() {
        val cinema = PageState().toggleCinema()
        assertEquals(ViewMode.Cinema, cinema.mode)
        assertEquals(ViewMode.Page, cinema.toggleCinema().mode)
    }

    @Test
    fun `F11 swaps fullscreen and cinema, and takes the page fullscreen`() {
        val full = PageState().onKey(PageKey.F11)
        assertEquals(ViewMode.Fullscreen, full.mode)
        assertEquals(ViewMode.Cinema, full.onKey(PageKey.F11).mode)
        assertEquals(ViewMode.Fullscreen, full.onKey(PageKey.F11).onKey(PageKey.F11).mode)
    }

    @Test
    fun `escape steps back one mode at a time`() {
        val full = PageState(mode = ViewMode.Fullscreen)
        assertEquals(ViewMode.Cinema, full.onKey(PageKey.Escape).mode)
        assertEquals(ViewMode.Page, full.onKey(PageKey.Escape).onKey(PageKey.Escape).mode)
        assertEquals(PageState(), PageState().onKey(PageKey.Escape))
    }

    @Test
    fun `close goes straight to the page and forgets the compact override`() {
        val closed = PageState(mode = ViewMode.Fullscreen, compactOverride = true).close()
        assertEquals(ViewMode.Page, closed.mode)
        assertEquals(null, closed.compactOverride)
    }

    @Test
    fun `cinema and fullscreen always show the now-playing bar`() {
        val hidden = PageState(nowBarWanted = false)
        assertFalse(hidden.nowBarOpen)
        assertTrue(hidden.toggleCinema().nowBarOpen)
        assertTrue(hidden.toggleFullscreen().nowBarOpen)
    }

    @Test
    fun `compact follows the width off the page until its button overrides it`() {
        val cinema = PageState(mode = ViewMode.Cinema)
        assertFalse(PageState().isCompact(narrow))
        assertTrue(cinema.isCompact(narrow))
        assertFalse(cinema.isCompact(wide))
        assertTrue(cinema.isCompact(PageState.COMPACT_BELOW_DP))
        val forced = cinema.toggleCompact(wide)
        assertTrue(forced.isCompact(wide))
        assertFalse(forced.toggleCompact(wide).isCompact(narrow))
        assertEquals(PageState(), PageState().toggleCompact(wide))
    }

    @Test
    fun `the page shows cinema, romanization, now bar, side, more, settings`() {
        assertEquals(
            listOf(ViewControl.CinemaView, ViewControl.Romanization, ViewControl.NowBar, ViewControl.NowBarSide,
                ViewControl.More, ViewControl.Settings),
            viewControls(PageState()),
        )
    }

    @Test
    fun `the page without the bar has no side swap`() {
        assertFalse(ViewControl.NowBarSide in viewControls(PageState(nowBarWanted = false)))
    }

    @Test
    fun `cinema and fullscreen show compact, fullscreen and close instead`() {
        val expected = listOf(ViewControl.CompactMode, ViewControl.Romanization, ViewControl.NowBarSide, ViewControl.Fullscreen,
            ViewControl.More, ViewControl.Settings, ViewControl.Close)
        assertEquals(expected, viewControls(PageState(mode = ViewMode.Cinema)))
        assertEquals(expected, viewControls(PageState(mode = ViewMode.Fullscreen, nowBarWanted = false)))
    }

    @Test
    fun `tooltips follow the state`() {
        assertEquals("Fullscreen", tooltip(ViewControl.Fullscreen, PageState(mode = ViewMode.Cinema), wide))
        assertEquals("Cinema View", tooltip(ViewControl.Fullscreen, PageState(mode = ViewMode.Fullscreen), wide))
        assertEquals("Disable Compact Mode", tooltip(ViewControl.CompactMode, PageState(mode = ViewMode.Cinema), narrow))
        assertEquals("Enable Romanization", tooltip(ViewControl.Romanization, PageState(), wide))
        assertEquals("Disable Romanization", tooltip(ViewControl.Romanization, PageState(romanized = true), wide))
    }

    @Test
    fun `clicks route to the matching transition`() {
        val page = PageState()
        assertEquals(page.toggleCinema(), page.click(ViewControl.CinemaView, wide))
        assertEquals(page.swapSides(), page.click(ViewControl.NowBarSide, wide))
        assertEquals(page.toggleNowBar(), page.click(ViewControl.NowBar, wide))
        assertEquals(page.toggleRomanization(), page.click(ViewControl.Romanization, wide))
        assertEquals(page, page.click(ViewControl.More, wide))
        assertEquals(page, page.click(ViewControl.Settings, wide))
    }
}
