package com.tx24.spicyplayer.lyrics.spicy.canvas

import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsScrollbarTest {
    private val view = Size(800f, 1016f) // a 1000px track between the 8px ends

    @Test fun theThumbShowsHowMuchOfTheLyricsIsOnScreenAndWhere() {
        val top = scrollbarThumb(ScrollbarSpan(position = 0f, visible = 0.25f, rangePx = 3000f), view, 1f)!!
        assertEquals(8f, top.top, 0.001f)
        assertEquals(250f, top.height, 0.001f)
        assertEquals(800f - 4f, top.right, 0.001f)
        assertEquals(6f, top.width, 0.001f)
        val end = scrollbarThumb(ScrollbarSpan(position = 1f, visible = 0.25f, rangePx = 3000f), view, 1f)!!
        assertEquals(1016f - 8f, end.bottom, 0.001f)
        assertEquals(750f, scrollbarTravelPx(ScrollbarSpan(0.5f, 0.25f, 3000f), view.height, 1f), 0.001f)
    }

    @Test fun aLongSongStillGetsAThumbItCanTakeHoldOf() {
        val thumb = scrollbarThumb(ScrollbarSpan(position = 0.5f, visible = 0.001f, rangePx = 1_000_000f), view, 2f)!!
        assertEquals(SCROLLBAR_MIN_THUMB_DP * 2f, thumb.height, 0.001f)
    }

    @Test fun nothingToScrollMeansNoScrollbar() {
        assertNull(scrollbarThumb(null, view, 1f))
        assertNull(scrollbarThumb(ScrollbarSpan(0f, 1f, 1f), view, 1f))
        assertNull(scrollbarThumb(ScrollbarSpan(0f, 0.5f, 100f), Size(800f, 20f), 1f))
    }

    @Test fun aPressBesideTheThumbStillTakesIt() {
        val thumb = scrollbarThumb(ScrollbarSpan(0f, 0.25f, 3000f), view, 1f)!!
        assertTrue(scrollbarHit(thumb, thumb.left - 5f, thumb.top + 10f, 1f))
        assertTrue(scrollbarHit(thumb, 799f, thumb.top + 10f, 1f))
        assertFalse(scrollbarHit(thumb, thumb.left - 20f, thumb.top + 10f, 1f))
        assertFalse(scrollbarHit(thumb, thumb.left, thumb.bottom + 20f, 1f))
    }
}
