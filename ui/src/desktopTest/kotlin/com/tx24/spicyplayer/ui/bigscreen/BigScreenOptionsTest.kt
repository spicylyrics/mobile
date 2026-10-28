package com.tx24.spicyplayer.ui.bigscreen

import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.NoticeKind
import com.tx24.spicyplayer.ui.settings.CommonAppSettings
import com.tx24.spicyplayer.ui.settings.ControlsHideDelay
import com.tx24.spicyplayer.ui.settings.InMemorySettingsStore
import com.tx24.spicyplayer.ui.settings.ViewControlsPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BigScreenOptionsTest {
    private val page = BigScreenState()
    private val cinema = BigScreenState(mode = ViewMode.Cinema)
    private val noLyrics = LyricsState.Error(NoticeKind.NoLyrics)

    @Test
    fun `the defaults are the settings' own`() {
        assertEquals(BigScreenOptions(), CommonAppSettings(InMemorySettingsStore(), false, true).bigScreenOptions())
    }

    @Test
    fun `the settings map through`() {
        val s = CommonAppSettings(InMemorySettingsStore(), false, true)
        s.autoHideControls = false
        s.controlsHideDelay = ControlsHideDelay.Longer
        s.hideControlsWhilePaused = true
        s.timelineOutsideCover = false
        s.viewControlsPosition = ViewControlsPosition.Bottom
        s.showNoLyricsNotice = false
        s.hideHeader = true
        assertEquals(
            BigScreenOptions(IdleSettings(false, 10_000L, true), timelineOutsideCover = false, viewControlsAtBottom = true, showNoLyricsNotice = false, hideHeader = true),
            s.bigScreenOptions(),
        )
    }

    @Test
    fun `only a song without lyrics, with the notice off, in cinema hides the lyrics`() {
        val off = BigScreenOptions(showNoLyricsNotice = false)
        assertTrue(hidesLyrics(noLyrics, immersive = true, off))
        assertFalse(hidesLyrics(noLyrics, immersive = false, off))
        assertFalse(hidesLyrics(noLyrics, immersive = true, BigScreenOptions()))
        assertFalse(hidesLyrics(LyricsState.Error(NoticeKind.Offline), immersive = true, off))
        assertFalse(hidesLyrics(LyricsState.Loading, immersive = true, off))
    }

    @Test
    fun `hiding the header closes the bar on the page only`() {
        val hidden = BigScreenOptions(hideHeader = true)
        assertFalse(showsNowBar(page, hidden))
        assertTrue(showsNowBar(cinema, hidden))
        assertTrue(showsNowBar(page, BigScreenOptions()))
        assertFalse(showsNowBar(page.toggleNowBar(), BigScreenOptions()))
    }

    @Test
    fun `without the header the page drops the bar's own buttons`() {
        val all = PlayerCapabilities(fullscreen = true)
        val hidden = BigScreenOptions(hideHeader = true)
        val shown = pageViewControls(page, all, hidden)
        assertFalse(ViewControl.NowBar in shown)
        assertFalse(ViewControl.NowBarSide in shown)
        assertEquals(viewControls(page, all), pageViewControls(page, all, BigScreenOptions()))
        assertEquals(viewControls(cinema, all), pageViewControls(cinema, all, hidden))
    }
}
