package com.tx24.spicyplayer.desktop

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsScreenScaleTest {
    @Test
    fun `the window's short side lands on a sideways phone's`() {
        // 1016x574: the short side is 574dp, so the screen is drawn 574 / 420 = 1.37x, in 5% steps.
        assertEquals(1.35f, lyricsScreenScale(1016f, 574f, 100), 0.001f)
        assertEquals(1.35f, lyricsScreenScale(574f, 1016f, 100), 0.001f)
    }

    @Test
    fun `never smaller than the system's size, nor past the cap`() {
        assertEquals(1f, lyricsScreenScale(600f, 300f, 100), 0.001f)
        assertEquals(MAX_SCREEN_SCALE, lyricsScreenScale(4000f, 3000f, 100), 0.001f)
    }

    @Test
    fun `the interface scale setting multiplies it`() {
        assertEquals(0.5f, lyricsScreenScale(600f, 300f, 50), 0.001f)
        assertEquals(2.75f, lyricsScreenScale(1016f, 574f, 200), 0.001f)
    }
}
