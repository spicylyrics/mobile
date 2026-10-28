package com.tx24.spicyplayer.ui.bigscreen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The one window-size test for the big-screen lyrics page and the big-screen settings panel. */
class BigScreenThresholdTest {
    @Test
    fun `phones keep their screens in both orientations`() {
        assertFalse(isBigScreenWindow(411f, 891f))
        assertFalse(isBigScreenWindow(891f, 411f))
        // A big phone on its side: wide enough, but too short.
        assertFalse(isBigScreenWindow(915f, 411f))
    }

    @Test
    fun `a tablet held upright keeps the phone's screens`() {
        assertFalse(isBigScreenWindow(800f, 1280f))
        // A window just short of the width.
        assertFalse(isBigScreenWindow(839f, 700f))
    }

    @Test
    fun `a tablet held sideways, DeX and an unfolded foldable get the big screens`() {
        assertTrue(isBigScreenWindow(1280f, 800f))
        assertTrue(isBigScreenWindow(1920f, 1032f))
        assertTrue(isBigScreenWindow(884f, 841f))
        assertTrue(isBigScreenWindow(840f, 673f))
        assertTrue(isBigScreenWindow(1024f, 480f))
    }

    @Test
    fun `the edges count as big`() {
        assertTrue(isBigScreenWindow(BIG_SCREEN_MIN_WIDTH_DP, BIG_SCREEN_MIN_HEIGHT_DP))
        assertFalse(isBigScreenWindow(BIG_SCREEN_MIN_WIDTH_DP - 1f, 900f))
        assertFalse(isBigScreenWindow(1200f, BIG_SCREEN_MIN_HEIGHT_DP - 1f))
    }

    @Test
    fun `the thresholds are 840 by 480`() {
        assertTrue(BIG_SCREEN_MIN_WIDTH_DP == 840f && BIG_SCREEN_MIN_HEIGHT_DP == 480f)
    }
}
