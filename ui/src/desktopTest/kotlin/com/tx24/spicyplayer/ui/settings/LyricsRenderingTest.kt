package com.tx24.spicyplayer.ui.settings

import com.tx24.spicyplayer.lyrics.spicy.RenderConfig
import com.tx24.spicyplayer.lyrics.spicy.ScrollConfig
import com.tx24.spicyplayer.lyrics.spicy.SyllableMerge
import com.tx24.spicyplayer.lyrics.spicy.canvas.PinnedFooterMode
import com.tx24.spicyplayer.lyrics.spicy.romanization.RomanizationMode
import com.tx24.spicyplayer.translation.TranslationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsRenderingTest {
    private fun settings() = CommonAppSettings(InMemorySettingsStore(), pipButtonByDefault = false, prereleasesByDefault = true)

    private val song = 200_000L

    @Test
    fun `a fresh install renders with today's config`() {
        val expected = RenderConfig(
            simpleLyricsMode = false,
            minimalLyricsMode = false,
            appleMusic = false,
            refreshedSimple = false,
            wordMotionBoost = CommonAppSettings.WORD_MOTION_BOOST,
            wideDuetPadding = true,
            creditsFollowLastLine = false,
            refinedCredits = true,
            backgroundPop = false,
            keepOverlappingLines = true,
            syllableMerge = SyllableMerge.Off,
            distanceBlurEnabled = true,
            glowEnabled = true,
            scroll = ScrollConfig(leadMs = 0L, smooth = false, seekFadeCompensation = true, staticSongMs = song),
        )
        assertEquals(expected, settings().renderConfig(song))
    }

    @Test
    fun `the defaults are the full look apart from the word motion, the seek fade and static scrolling`() {
        val config = settings().renderConfig(song)
        val full = RenderConfig.FULL
        assertEquals(full.copy(wordMotionBoost = config.wordMotionBoost, scroll = config.scroll), config)
    }

    @Test
    fun `each setting flips its own field`() {
        fun flipped(change: CommonAppSettings.() -> Unit) = settings().apply(change).renderConfig(song)

        assertTrue(flipped { simpleLyricsMode = true }.simpleLyricsMode)
        assertTrue(flipped { minimalLyricsMode = true }.minimalLyricsMode)
        assertTrue(flipped { simpleLyricsMode = true; refreshedSimpleLyrics = true }.refreshedSimple)
        assertFalse(flipped { refreshedSimpleLyrics = true }.refreshedSimple)
        assertEquals(1f, flipped { originalWordMotion = true }.wordMotionBoost)
        assertFalse(flipped { duetLinePadding = false }.wideDuetPadding)
        assertTrue(flipped { creditsFollowLastLine = true }.creditsFollowLastLine)
        assertFalse(flipped { refinedCredits = false }.refinedCredits)
        assertTrue(flipped { backgroundPop = true }.backgroundPop)
        assertFalse(flipped { keepOverlappingLines = false }.keepOverlappingLines)
        assertEquals(SyllableMerge.Steady, flipped { syllableMerge = SyllableMerge.Steady }.syllableMerge)
        assertFalse(flipped { distanceBlur = false }.distanceBlurEnabled)
        assertFalse(flipped { glow = false }.glowEnabled)
        assertTrue(flipped { smoothScrolling = true }.scroll.smooth)
        assertFalse(flipped { seekFadeCompensation = false }.scroll.seekFadeCompensation)
        assertEquals(0L, flipped { staticAutoScroll = false }.scroll.staticSongMs)
        assertFalse(flipped { lineHoverBackground = false }.scroll.lineHoverBackground)
    }

    @Test
    fun `early scroll leads by its setting only while on`() {
        assertEquals(0L, settings().apply { scrollLeadMs = 400 }.renderConfig(song).scroll.leadMs)
        assertEquals(400L, settings().apply { scrollLeadEnabled = true; scrollLeadMs = 400 }.renderConfig(song).scroll.leadMs)
    }

    @Test
    fun `low performance drops the blur and the glow, and nothing else`() {
        val normal = settings().renderConfig(song)
        val low = settings().apply { lowPerformance = true }.renderConfig(song)
        assertFalse(low.distanceBlurEnabled)
        assertFalse(low.glowEnabled)
        assertEquals(normal, low.copy(distanceBlurEnabled = true, glowEnabled = true))
    }

    @Test
    fun `the Apple Music style turns off the Spicy Lyrics modes and the pop-out`() {
        val config = settings().apply {
            lyricsStyle = LyricsStyle.AppleMusic
            simpleLyricsMode = true
            refreshedSimpleLyrics = true
            minimalLyricsMode = true
            backgroundPop = true
        }.renderConfig(song)
        assertTrue(config.appleMusic)
        assertFalse(config.simpleLyricsMode)
        assertFalse(config.minimalLyricsMode)
        assertFalse(config.refreshedSimple)
        assertFalse(config.backgroundPop)
    }

    @Test
    fun `romanization shows only when asked for and there is one`() {
        assertFalse(settings().romanizes(available = true))
        assertFalse(settings().apply { romanize = true }.romanizes(available = false))
        assertTrue(settings().apply { romanize = true }.romanizes(available = true))
    }

    @Test
    fun `the display carries the rest of the lyrics settings`() {
        val display = settings().apply {
            lyricsSizeLandscape = 120
            romanize = true
            romanizationMode = RomanizationMode.UnderLine
            translationMode = TranslationMode.Replace
            textCredits = false
            pinnedFooter = PinnedFooterMode.entries.last()
        }.lyricsDisplay(song, landscape = true, romanizationAvailable = true)
        assertEquals(1.2f, display.fontSizeScale, 0.0001f)
        assertTrue(display.romanize)
        assertEquals(RomanizationMode.UnderLine, display.romanizationMode)
        assertEquals(TranslationMode.Replace, display.translationMode)
        assertFalse(display.textCredits)
        assertEquals(PinnedFooterMode.entries.last(), display.pinnedFooter)
        assertEquals(settings().renderConfig(song), display.config)
    }
}
