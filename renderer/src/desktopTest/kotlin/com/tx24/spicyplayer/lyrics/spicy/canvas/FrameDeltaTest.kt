package com.tx24.spicyplayer.lyrics.spicy.canvas

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameDeltaTest {
    private val interval120 = nominalFrameIntervalS(120f)
    private fun ms(value: Long) = value * 1_000_000L

    @Test
    fun `nominal interval follows the refresh rate and falls back to 60 Hz`() {
        assertEquals(1f / 120f, interval120, 1e-7f)
        assertEquals(1f / 60f, nominalFrameIntervalS(null), 1e-7f)
        assertEquals(1f / 60f, nominalFrameIntervalS(0f), 1e-7f)
    }

    @Test
    fun `an on-time frame advances by its real gap`() {
        assertEquals(0.008f, frameDeltaSeconds(ms(1008), ms(1000), interval120, resumed = false), 1e-6f)
    }

    @Test
    fun `a late frame advances by at most two intervals`() {
        assertEquals(2f * interval120, frameDeltaSeconds(ms(1500), ms(1000), interval120, resumed = false), 1e-7f)
    }

    @Test
    fun `with a hitch cap, a normal frame slower than the display advances by its real gap`() {
        // A 165 Hz display drawn at 60 fps: two display intervals (12 ms) would cut each frame short.
        val interval165 = nominalFrameIntervalS(165f)
        assertEquals(0.0167f, frameDeltaSeconds(ms(1000) + 16_700_000L, ms(1000), interval165, resumed = false, capS = 0.1f), 1e-6f)
        assertEquals(0.1f, frameDeltaSeconds(ms(1500), ms(1000), interval165, resumed = false, capS = 0.1f), 1e-7f)
    }

    @Test
    fun `the first frame after a rest advances by one interval`() {
        assertEquals(interval120, frameDeltaSeconds(ms(5000), ms(1000), interval120, resumed = true), 1e-7f)
    }

    @Test
    fun `the very first frame advances by one interval`() {
        assertEquals(interval120, frameDeltaSeconds(ms(5000), 0L, interval120, resumed = false), 1e-7f)
    }

    @Test
    fun `time going backwards advances nothing`() {
        assertEquals(0f, frameDeltaSeconds(ms(1000), ms(1010), interval120, resumed = false), 0f)
    }
}
