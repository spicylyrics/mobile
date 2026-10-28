package com.tx24.spicyplayer.ui.bigscreen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BigScreenThresholdTest {
    @Test
    fun `phones keep their layout in both orientations`() {
        assertFalse(usesBigScreenLayout(411f, 891f))
        assertFalse(usesBigScreenLayout(891f, 411f))
    }

    @Test
    fun `a tablet held upright keeps the phone layout`() {
        assertFalse(usesBigScreenLayout(800f, 1280f))
    }

    @Test
    fun `a tablet held sideways, DeX and an unfolded foldable get the big page`() {
        assertTrue(usesBigScreenLayout(1280f, 800f))
        assertTrue(usesBigScreenLayout(1920f, 1032f))
        assertTrue(usesBigScreenLayout(884f, 841f))
    }

    @Test
    fun `the edges count as big`() {
        assertTrue(usesBigScreenLayout(BIG_SCREEN_MIN_WIDTH_DP, BIG_SCREEN_MIN_HEIGHT_DP))
        assertFalse(usesBigScreenLayout(BIG_SCREEN_MIN_WIDTH_DP - 1f, 900f))
        assertFalse(usesBigScreenLayout(1200f, BIG_SCREEN_MIN_HEIGHT_DP - 1f))
    }
}
