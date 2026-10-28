package com.tx24.spicyplayer.ui.bigscreen

import com.tx24.spicyplayer.ui.nowplaying.LandscapeMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BigScreenMetricsTest {
    private val page = BigScreenMetrics(1100f, 760f, 1f, ViewMode.Page)
    private val cinema = BigScreenMetrics(1100f, 760f, 1f, ViewMode.Cinema)

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
    fun `the cover scales with the window, by height on a wide one`() {
        val wide = BigScreenMetrics(2560f, 1080f, 1f, ViewMode.Page)
        assertEquals(1080f * 0.52f * 1.1f, wide.coverSizePx, 0.01f)
        val tablet = BigScreenMetrics(2560f, 1600f, 2f, ViewMode.Page)
        assertTrue(tablet.coverSizePx < 1600f)
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
        val windows = transportSlots(PlayerCapabilities(skipNext = false, skipPrevious = false))
        assertEquals(listOf(TransportButton.Previous, TransportButton.PlayPause, TransportButton.Next), windows.map { it.button })
        assertEquals(listOf(false, true, false), windows.map { it.enabled })
        assertTrue(transportSlots(PlayerCapabilities()).all { it.enabled })
        assertTrue(transportSlots(PlayerCapabilities(false, false, false, false)).none { it.enabled })
    }

    @Test
    fun `with the timeline outside the cover rises and the song moves below the timeline`() {
        val outside = BigScreenMetrics(1100f, 760f, 1f, ViewMode.Cinema, timelineOutside = true)
        assertEquals(380f - 363f * 0.625f, outside.coverTopPx, 0.01f)
        assertEquals(outside.coverTopPx + 363f, outside.outsideTimelineTopPx, 0.01f)
        assertEquals(outside.outsideTimelineTopPx + outside.outsideTimelineHeightPx, outside.textTopPx, 0.01f)
        // Padding 4.5cqw and 3cqw of the cover around a bar 1cqh of the page.
        assertEquals(363f * 0.045f, outside.outsideTimeline.insetTopPx, 0.01f)
        assertEquals(363f * 0.03f, outside.outsideTimeline.insetBottomPx, 0.01f)
        assertEquals(7.6f, outside.outsideTimeline.barHeightPx, 0.01f)
        assertTrue(outside.outsideTimelineHeightPx > 363f * 0.075f)
        // On the cover the song keeps its 5cqw margin.
        assertEquals(cinema.coverTopPx + 363f * 1.05f, cinema.textTopPx, 0.01f)
    }

    @Test
    fun `the closed bar waits off the page on its own side`() {
        val away = 363f + 96.25f * 0.7f + 55f
        assertEquals(-away, page.hiddenCoverLeftPx(onRight = false), 0.01f)
        assertEquals(1100f + away, page.hiddenCoverLeftPx(onRight = true), 0.01f)
    }
}
