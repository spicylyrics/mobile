package com.tx24.spicyplayer.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SpicyToastTest {
    @Test
    fun `short messages hold three seconds`() {
        assertEquals(3_000L, toastHoldMs(0))
        assertEquals(3_000L, toastHoldMs(60))
    }

    @Test
    fun `each character past sixty adds forty milliseconds`() {
        assertEquals(3_040L, toastHoldMs(61))
        assertEquals(5_000L, toastHoldMs(110))
    }

    @Test
    fun `the hold is capped at eight seconds`() {
        assertEquals(8_000L, toastHoldMs(185))
        assertEquals(8_000L, toastHoldMs(10_000))
    }
}
