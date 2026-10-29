package com.tx24.spicyplayer.desktop

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsScreenScaleTest {
    @Test
    fun `the window's short side becomes a TV's, whichever way round`() {
        assertEquals(1.7f, lyricsScreenScale(1600f, 900f, 100), 0.001f)
        assertEquals(1.7f, lyricsScreenScale(900f, 1600f, 100), 0.001f)
        assertEquals(2.7f, lyricsScreenScale(2560f, 1440f, 100), 0.001f)
    }

    @Test
    fun `never smaller than the system's size`() {
        assertEquals(1f, lyricsScreenScale(600f, 300f, 100), 0.001f)
    }

    @Test
    fun `the interface scale setting multiplies it`() {
        assertEquals(0.5f, lyricsScreenScale(600f, 300f, 50), 0.001f)
        assertEquals(3.3f, lyricsScreenScale(1600f, 900f, 200), 0.001f)
    }

    @Test
    fun `settings grow with the screen until their panel fills the window`() {
        // 1400dp tall: the screen is at 2.6x, but the 700dp panel fits only 1.76 times in 88% of it.
        assertEquals(1.8f, settingsScale(2560f, 1400f, 100), 0.001f)
        assertEquals(1f, settingsScale(1100f, 700f, 100), 0.001f)
        assertEquals(2f, settingsScale(1100f, 700f, 200), 0.001f)
    }
}
