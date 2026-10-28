package com.tx24.spicyplayer.ui.bigscreen

import com.tx24.spicyplayer.ui.settings.CommonAppSettings
import com.tx24.spicyplayer.ui.settings.CompactView
import com.tx24.spicyplayer.ui.settings.InMemorySettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BigScreenStateTest {
    private val desktop = PlayerCapabilities(fullscreen = true)
    private val tablet = PlayerCapabilities(fullscreen = false)
    private val tall = 760f

    @Test
    fun `cinema button opens cinema and closes back to the page`() {
        val cinema = BigScreenState().toggleCinema()
        assertEquals(ViewMode.Cinema, cinema.mode)
        assertEquals(ViewMode.Page, cinema.toggleCinema().mode)
    }

    @Test
    fun `F11 swaps fullscreen and cinema, and takes the page fullscreen`() {
        val full = BigScreenState().onKey(PageKey.F11, desktop)
        assertEquals(ViewMode.Fullscreen, full.mode)
        assertEquals(ViewMode.Cinema, full.onKey(PageKey.F11, desktop).mode)
    }

    @Test
    fun `F11 does nothing without fullscreen`() {
        assertEquals(BigScreenState(), BigScreenState().onKey(PageKey.F11, tablet))
        val cinema = BigScreenState(mode = ViewMode.Cinema)
        assertEquals(cinema, cinema.onKey(PageKey.F11, tablet))
    }

    @Test
    fun `escape steps back one mode at a time`() {
        val full = BigScreenState(mode = ViewMode.Fullscreen)
        assertEquals(ViewMode.Cinema, full.onKey(PageKey.Escape, desktop).mode)
        assertEquals(ViewMode.Page, full.onKey(PageKey.Escape, desktop).onKey(PageKey.Escape, desktop).mode)
        assertEquals(BigScreenState(), BigScreenState().onKey(PageKey.Escape, desktop))
    }

    @Test
    fun `close goes straight to the page and keeps the compact choice for next time`() {
        val closed = BigScreenState(mode = ViewMode.Fullscreen, compactOverride = true).close()
        assertEquals(ViewMode.Page, closed.mode)
        assertEquals(true, closed.compactOverride)
        // The page itself still follows the window; cinema takes the choice back up.
        assertEquals(BigScreenLayout.Side, closed.layoutFor(1400f, tall))
        assertEquals(BigScreenLayout.Compact, closed.toggleCinema().layoutFor(1400f, tall))
    }

    @Test
    fun `cinema and fullscreen always show the now-playing bar`() {
        val hidden = BigScreenState(nowBarWanted = false)
        assertFalse(hidden.nowBarOpen)
        assertTrue(hidden.toggleCinema().nowBarOpen)
        assertTrue(hidden.toggleFullscreen(desktop).nowBarOpen)
    }

    @Test
    fun `a wide window keeps the side layout on the page and in wide cinema`() {
        assertEquals(BigScreenLayout.Side, BigScreenState().layoutFor(1100f, tall))
        assertEquals(BigScreenLayout.Side, BigScreenState(mode = ViewMode.Cinema).layoutFor(1400f, tall))
    }

    @Test
    fun `cinema goes compact up to 1133dp`() {
        val cinema = BigScreenState(mode = ViewMode.Cinema)
        assertEquals(BigScreenLayout.Compact, cinema.layoutFor(1100f, tall))
        assertEquals(BigScreenLayout.Compact, cinema.layoutFor(BigScreenState.COMPACT_BELOW_DP, tall))
        assertEquals(BigScreenLayout.Side, cinema.layoutFor(1134f, tall))
    }

    @Test
    fun `narrow or upright windows go compact even on the page`() {
        assertEquals(BigScreenLayout.Compact, BigScreenState().layoutFor(650f, 500f))
        // A tablet held upright.
        assertEquals(BigScreenLayout.Compact, BigScreenState().layoutFor(800f, 1280f))
        // The same tablet turned.
        assertEquals(BigScreenLayout.Side, BigScreenState().layoutFor(1280f, 800f))
    }

    @Test
    fun `the compact button overrides the width off the page only`() {
        val cinema = BigScreenState(mode = ViewMode.Cinema)
        val forced = cinema.toggleCompact(1400f, tall)
        assertTrue(forced.isCompact(1400f, tall))
        assertFalse(forced.toggleCompact(1400f, tall).isCompact(900f, tall))
        assertEquals(BigScreenState(), BigScreenState().toggleCompact(1400f, tall))
    }

    @Test
    fun `the page shows cinema, romanization, now bar, side, more, settings`() {
        assertEquals(
            listOf(ViewControl.CinemaView, ViewControl.Romanization, ViewControl.NowBar, ViewControl.NowBarSide,
                ViewControl.More, ViewControl.Settings),
            viewControls(BigScreenState(), desktop),
        )
        assertFalse(ViewControl.NowBarSide in viewControls(BigScreenState(nowBarWanted = false), desktop))
    }

    @Test
    fun `cinema and fullscreen show compact, fullscreen and close instead`() {
        val expected = listOf(ViewControl.CompactMode, ViewControl.Romanization, ViewControl.NowBarSide, ViewControl.Fullscreen,
            ViewControl.More, ViewControl.Settings, ViewControl.Close)
        assertEquals(expected, viewControls(BigScreenState(mode = ViewMode.Cinema), desktop))
        assertEquals(expected, viewControls(BigScreenState(mode = ViewMode.Fullscreen, nowBarWanted = false), desktop))
    }

    @Test
    fun `without fullscreen cinema has no fullscreen button`() {
        assertFalse(ViewControl.Fullscreen in viewControls(BigScreenState(mode = ViewMode.Cinema), tablet))
    }

    @Test
    fun `tooltips follow the state`() {
        assertEquals("Fullscreen", tooltip(ViewControl.Fullscreen, BigScreenState(mode = ViewMode.Cinema), 1400f, tall))
        assertEquals("Cinema View", tooltip(ViewControl.Fullscreen, BigScreenState(mode = ViewMode.Fullscreen), 1400f, tall))
        assertEquals("Disable Compact Mode", tooltip(ViewControl.CompactMode, BigScreenState(mode = ViewMode.Cinema), 900f, tall))
        assertEquals("Enable Romanization", tooltip(ViewControl.Romanization, BigScreenState(), 1400f, tall))
        assertEquals("Disable Romanization", tooltip(ViewControl.Romanization, BigScreenState(romanized = true), 1400f, tall))
    }

    @Test
    fun `clicks route to the matching transition`() {
        val page = BigScreenState()
        fun click(control: ViewControl) = page.click(control, 1400f, tall, desktop)
        assertEquals(page.toggleCinema(), click(ViewControl.CinemaView))
        assertEquals(page.swapSides(), click(ViewControl.NowBarSide))
        assertEquals(page.toggleNowBar(), click(ViewControl.NowBar))
        assertEquals(page.toggleRomanization(), click(ViewControl.Romanization))
        assertEquals(page, click(ViewControl.More))
        assertEquals(page, click(ViewControl.Settings))
    }

    @Test
    fun `the lyrics manager button shows only where the platform offers it`() {
        assertFalse(ViewControl.LyricsManager in viewControls(BigScreenState(), desktop))
        val controls = viewControls(BigScreenState(), desktop.copy(lyricsManager = true))
        assertEquals(controls.indexOf(ViewControl.Settings) - 1, controls.indexOf(ViewControl.LyricsManager))
        assertEquals("Lyrics Manager", tooltip(ViewControl.LyricsManager, BigScreenState(), 1100f, tall))
        // It opens a window; the page's own state stays as it was.
        assertEquals(BigScreenState(), BigScreenState().click(ViewControl.LyricsManager, 1100f, tall, desktop))
    }

    @Test
    fun `the view is kept in the settings and comes back on the next run`() {
        val settings = CommonAppSettings(InMemorySettingsStore(), pipButtonByDefault = false, prereleasesByDefault = false)
        // A first run: the now-playing bar closed and on the left, compact following the window.
        val first = BigScreenState().withSavedView(settings)
        assertFalse(first.nowBarWanted)
        assertFalse(first.nowBarOnRight)
        assertEquals(null, first.compactOverride)

        settings.saveView(first.toggleNowBar().swapSides().toggleCinema().toggleCompact(1400f, tall))
        assertTrue(settings.nowBarOpen)
        assertTrue(settings.nowBarOnRight)
        assertEquals(CompactView.Compact, settings.compactView)

        val next = BigScreenState().withSavedView(settings)
        assertTrue(next.nowBarWanted)
        assertTrue(next.nowBarOnRight)
        assertEquals(true, next.compactOverride)
        assertEquals(ViewMode.Page, next.mode)
    }

    @Test
    fun `saving an unchanged view writes nothing`() {
        val store = InMemorySettingsStore()
        val settings = CommonAppSettings(store, pipButtonByDefault = false, prereleasesByDefault = false)
        settings.saveView(BigScreenState().withSavedView(settings))
        assertTrue(store.keys.none { it in setOf("nowBarOpen", "nowBarOnRight", "compactView") })
    }

    @Test
    fun `the compact choice round-trips through its stored form`() {
        listOf(null, true, false).forEach { assertEquals(it, CompactView.of(it).override) }
    }
}
