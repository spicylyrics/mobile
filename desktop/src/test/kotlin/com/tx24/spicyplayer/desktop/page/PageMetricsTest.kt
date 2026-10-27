package com.tx24.spicyplayer.desktop.page

import com.tx24.spicyplayer.desktop.Transport
import com.tx24.spicyplayer.ui.nowplaying.LandscapeMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageMetricsTest {
    private val page = PageMetrics(1100f, 760f, 1f, ViewMode.Page)
    private val cinema = PageMetrics(1100f, 760f, 1f, ViewMode.Cinema)

    @Test
    fun `the cover is 1_1 times the smaller of 30cqw and 52cqh`() {
        assertEquals(363f, page.coverSizePx, 0.01f)
        assertEquals(380f - 363f * 0.6f, page.coverTopPx, 0.01f)
        assertEquals(363f * 0.02f, page.coverCornerPx, 0.01f)
    }

    @Test
    fun `the bar sits closer to the edge on the page than in cinema`() {
        assertEquals(96.25f * 0.7f, page.coverLeftPx(onRight = false), 0.01f)
        assertEquals(96.25f, cinema.coverLeftPx(onRight = false), 0.01f)
        assertEquals(1100f - 363f - 96.25f, cinema.coverLeftPx(onRight = true), 0.01f)
        assertEquals((1100f - 363f) / 2f, cinema.coverLeftPx(onRight = false, lyricsHidden = true), 0.01f)
    }

    @Test
    fun `on the page the lyrics box matches the shared landscape geometry`() {
        val shared = LandscapeMetrics(1100f, 760f, 1f)
        for ((withBar, onRight) in listOf(true to false, true to true, false to false)) {
            val expected = shared.lyricsBox(withBar, onRight)
            val actual = page.lyricsBox(withBar, onRight)
            assertEquals(expected.leftPx, actual.leftPx, 0.01f)
            assertEquals(expected.widthPx, actual.widthPx, 0.01f)
        }
    }

    @Test
    fun `without the bar the lyrics keep their place whichever side it was on`() {
        assertEquals(page.lyricsBox(withBar = false, barOnRight = false), page.lyricsBox(withBar = false, barOnRight = true))
    }

    @Test
    fun `in cinema the lyrics start at half the page less the bar's gap`() {
        val box = cinema.lyricsBox(withBar = true, barOnRight = false)
        val textStart = box.leftPx + box.widthPx * 0.05f
        assertEquals(550f - 363f / 4f * 0.3f, textStart, 0.01f)
    }

    @Test
    fun `view controls are 110 percent of their row`() {
        assertEquals(760f * 0.05f * 1.1f, page.pageControlSizePx, 0.01f)
        assertEquals(363f * 0.099f, cinema.coverControlSizePx, 0.01f)
    }

    @Test
    fun `playback times are M SS with uncapped minutes`() {
        assertEquals("0:00", formatPlaybackTime(0))
        assertEquals("0:59", formatPlaybackTime(59_999))
        assertEquals("1:01", formatPlaybackTime(61_000))
        assertEquals("60:00", formatPlaybackTime(3_600_000))
        assertEquals("0:00", formatPlaybackTime(-5))
    }

    @Test
    fun `progress and seek targets clamp to the song`() {
        assertEquals(0.5f, progressOf(30_000, 60_000), 0.0001f)
        assertEquals(1f, progressOf(90_000, 60_000), 0.0001f)
        assertEquals(0f, progressOf(10_000, 0), 0.0001f)
        assertEquals(15_000L, seekTarget(0.25f, 60_000))
        assertEquals(60_000L, seekTarget(1.5f, 60_000))
        assertEquals(0L, seekTarget(0.5f, 0))
    }

    @Test
    fun `playback buttons keep their places and enable by capability`() {
        val windows = transportSlots(setOf(Transport.PLAY, Transport.PAUSE, Transport.SEEK))
        assertEquals(listOf(TransportButton.Previous, TransportButton.PlayPause, TransportButton.Next), windows.map { it.button })
        assertEquals(listOf(false, true, false), windows.map { it.enabled })
        assertTrue(transportSlots(Transport.entries.toSet()).all { it.enabled })
        assertTrue(transportSlots(emptySet()).none { it.enabled })
        assertTrue(canSeek(setOf(Transport.SEEK)))
        assertFalse(canSeek(setOf(Transport.PLAY)))
    }
}
