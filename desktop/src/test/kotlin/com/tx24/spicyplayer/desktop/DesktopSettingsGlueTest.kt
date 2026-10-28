package com.tx24.spicyplayer.desktop

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.font.FontFamily
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.ui.settings.CommonAppSettings
import com.tx24.spicyplayer.ui.settings.InMemorySettingsStore
import com.tx24.spicyplayer.ui.settings.LyricsFont
import com.tx24.spicyplayer.ui.settings.renderConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopSettingsGlueTest {
    private fun settings() = CommonAppSettings(InMemorySettingsStore(), pipButtonByDefault = false, prereleasesByDefault = false)

    @Test
    fun `the lyrics follow the settings' render config`() {
        val s = settings().apply { simpleLyricsMode = true; glow = false }
        val display = s.desktopLyrics(LyricsState.Idle, 200_000L)
        assertEquals(s.renderConfig(200_000L), display.config)
        assertTrue(display.config.simpleLyricsMode)
        assertFalse(display.config.glowEnabled)
    }

    @Test
    fun `the lyrics size is the one the settings show, whatever the window's shape`() {
        val s = settings().apply { lyricsSizeLandscape = 150; lyricsSizePortrait = 80 }
        assertEquals(1.5f, s.desktopLyrics(LyricsState.Idle, 0L).fontSizeScale, 0.0001f)
    }

    @Test
    fun `the lyrics font is ours by default and the computer's for System`() {
        assertEquals(DesktopLyricsFont(null, ""), desktopLyricsFont(LyricsFont.Default))
        assertEquals(DesktopLyricsFont(FontFamily.Default, "System"), desktopLyricsFont(LyricsFont.System))
        // No file loading on desktop yet: a font picked on the phone falls back to ours.
        assertEquals(DesktopLyricsFont(null, ""), desktopLyricsFont(LyricsFont.Custom))
    }

    @Test
    fun `no lyrics means nothing to romanize`() {
        val s = settings().apply { romanize = true }
        assertFalse(s.desktopLyrics(LyricsState.Idle, 0L, 1f, 1f).romanize)
    }

    @Test
    fun `a change in the settings window reaches the lyrics window`() = runBlocking {
        val s = settings()
        val next = async { withTimeout(5_000L) { snapshotFlow { s.desktopLyrics(LyricsState.Idle, 0L) }.drop(1).first() } }
        repeat(5) { yield() }
        // As the settings window does: a write to the shared settings, then the snapshot is applied.
        s.distanceBlur = false
        Snapshot.sendApplyNotifications()
        assertFalse(next.await().config.distanceBlurEnabled)
    }

    @Test
    fun `the background holds still and drops the beats when static or in low performance`() {
        val default = settings().desktopBackground()
        assertTrue(default.animate)
        assertTrue(default.beatReactive)
        assertEquals(8, default.blurPasses)

        val static = settings().apply { staticBackground = true }.desktopBackground()
        assertFalse(static.animate)
        assertFalse(static.beatReactive)

        val low = settings().apply { lowPerformance = true }.desktopBackground()
        assertFalse(low.animate)
        assertFalse(low.beatReactive)

        assertTrue(settings().apply { beatReactiveBackground = false }.desktopBackground().animate)
        assertFalse(settings().apply { beatReactiveBackground = false }.desktopBackground().beatReactive)
    }

    @Test
    fun `the blur setting maps to Kawarp's passes as on the phone`() {
        assertEquals(1, kawarpBlurPasses(0))
        assertEquals(8, kawarpBlurPasses(60))
        assertEquals(13, kawarpBlurPasses(100))
        assertEquals(13, kawarpBlurPasses(250))
    }
}
