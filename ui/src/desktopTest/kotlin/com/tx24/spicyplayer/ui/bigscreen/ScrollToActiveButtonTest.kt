package com.tx24.spicyplayer.ui.bigscreen

import com.tx24.spicyplayer.lyrics.spicy.canvas.ActiveLineDirection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollToActiveButtonTest {
    @Test
    fun `shows only while scrolled away with the setting on`() {
        assertTrue(showsScrollToActive(settingOn = true, direction = ActiveLineDirection.Above))
        assertTrue(showsScrollToActive(settingOn = true, direction = ActiveLineDirection.Below))
        assertFalse(showsScrollToActive(settingOn = true, direction = null))
        assertFalse(showsScrollToActive(settingOn = false, direction = ActiveLineDirection.Below))
        assertFalse(showsScrollToActive(settingOn = false, direction = null))
    }
}
