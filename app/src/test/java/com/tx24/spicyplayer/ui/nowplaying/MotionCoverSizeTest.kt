package com.tx24.spicyplayer.ui.nowplaying

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionCoverSizeTest {
    @Test
    fun `a video covers its box at its own shape`() {
        // A portrait canvas on a wide stage: as wide as the stage, taller than it.
        assertEquals(800 to 1423, coverSize(800, 900, 9f / 16f))
        // On a narrow, tall one: as tall as the stage.
        assertEquals(563 to 1000, coverSize(300, 1000, 9f / 16f))
        // A square loop: a square as big as the longer side.
        assertEquals(900 to 900, coverSize(600, 900, 1f))
        assertEquals(1200 to 1200, coverSize(1200, 700, 1f))
    }
}
