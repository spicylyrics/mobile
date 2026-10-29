package com.tx24.spicyplayer.desktop

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsScreenScaleTest {
    @Test
    fun `a small window's short side lands on a sideways phone's`() {
        // 1016x574: the short side is 574dp, so 574 / 420 = 1.37, in 10% steps.
        assertEquals(1.4f, lyricsScreenScale(1016f, 574f, 100), 0.001f)
        assertEquals(1.4f, lyricsScreenScale(574f, 1016f, 100), 0.001f)
    }

    @Test
    fun `never smaller than the system's size, and a big window gets room, not giant controls`() {
        assertEquals(1f, lyricsScreenScale(600f, 300f, 100), 0.001f)
        assertEquals(MAX_AUTO_SCALE, lyricsScreenScale(2560f, 1400f, 100), 0.001f)
        assertEquals(MAX_AUTO_SCALE, lyricsScreenScale(4000f, 3000f, 100), 0.001f)
    }

    @Test
    fun `the interface scale setting multiplies it`() {
        assertEquals(0.5f, lyricsScreenScale(600f, 300f, 50), 0.001f)
        assertEquals(3f, lyricsScreenScale(4000f, 3000f, 200), 0.001f)
    }
}
