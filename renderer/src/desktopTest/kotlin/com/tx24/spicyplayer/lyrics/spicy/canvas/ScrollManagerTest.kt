package com.tx24.spicyplayer.lyrics.spicy.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollManagerTest {
    private var now = 1_000L
    private val scroll = ScrollManager(clockMs = { now })
    private val height = 5_000f

    /** Runs [seconds] of 60fps frames following line [index] centred at [targetY]. */
    private fun frames(seconds: Float, index: Int, targetY: Float, visible: Float = 100f) {
        repeat((seconds * 60).toInt()) {
            now += 16
            scroll.updateScroll(1f / 60f, height, index, targetY, targetVisiblePx = visible)
        }
    }

    private fun drag(dy: Float) {
        scroll.onDragStart()
        now += 16
        scroll.onDrag(dy, height)
        now += 200 // held still before lifting: no fling
        scroll.onDragEnd()
    }

    @Test fun firstFrameSnapsToTheLine() {
        frames(0.02f, index = 0, targetY = -300f)
        assertEquals(-300f, scroll.animScrollY, 0.01f)
    }

    @Test fun aRaisedTopHoldsTheOpeningLinesHighUntilCentringNeedsAMove() {
        scroll.topY = -500f
        // Before the first line: straight to the top.
        now += 16
        scroll.updateScroll(1f / 60f, height, null, null)
        assertEquals(-500f, scroll.animScrollY, 0.01f)
        // A line centred above the top stays at the top...
        frames(1f, 0, -100f)
        assertEquals(-500f, scroll.animScrollY, 0.01f)
        // ...and one further down is centred.
        frames(2f, 3, -900f)
        assertEquals(-900f, scroll.animScrollY, 0.01f)
    }

    @Test fun lyricsShorterThanARaisedTopRestAtTheTop() {
        // The crash: one ~92px line under a top raised to -156px made the scroll range empty.
        scroll.topY = -156f
        repeat(120) {
            now += 16
            scroll.updateScroll(1f / 60f, 92f, 0, -40f, targetVisiblePx = 92f)
        }
        assertEquals(-156f, scroll.animScrollY, 0.01f)
        drag(-300f)
        repeat(120) {
            now += 16
            scroll.updateScroll(1f / 60f, 92f, 0, -40f, targetVisiblePx = 92f)
        }
        assertTrue(scroll.animScrollY.isFinite())
    }

    @Test fun aLineChangeGlidesFrontLoadedLikeDesktopChromium() {
        frames(0.1f, 0, 0f)
        // 400px: sqrt(400) = 20 frames. cubic-bezier(0.4, 0, 0, 1) covers ~86% of it by the
        // halfway mark, where ease-in-out is only at 50%.
        frames(10f / 60f, 1, -400f)
        assertTrue(scroll.animScrollY < -320f)
        frames(15f / 60f, 1, -400f)
        assertEquals(-400f, scroll.animScrollY, 0.01f)
    }

    @Test fun touchingHidesBlurAndStopsAutoScrollUntilCooldown() {
        frames(0.1f, 0, -300f)
        drag(-200f)
        assertTrue(scroll.hideLineBlur)
        val left = scroll.animScrollY

        // A new line starts while the user is still in control: the view stays put.
        frames(0.5f, 1, -600f)
        assertEquals(left, scroll.animScrollY, 0.01f)
        assertTrue(scroll.hideLineBlur)

        // After the cooldown auto-scroll resumes, and the line has changed, so it glides there.
        frames(2f, 1, -600f)
        assertFalse(scroll.hideLineBlur)
        assertEquals(-600f, scroll.animScrollY, 1f)
    }

    @Test fun afterResumingANudgeIsLeftAloneUntilTheLineChanges() {
        frames(0.1f, 0, -300f)
        drag(-50f)
        frames(2f, 0, -300f)
        assertFalse(scroll.hideLineBlur)
        assertEquals(-350f, scroll.animScrollY, 0.01f)

        frames(2f, 1, -600f)
        assertEquals(-600f, scroll.animScrollY, 1f)
    }

    @Test fun scrolledAwayFromTheLineStaysManual() {
        frames(0.1f, 0, -300f)
        drag(-2_000f)
        frames(3f, 1, -600f, visible = 0f)
        assertTrue(scroll.hideLineBlur)
        assertEquals(-2_300f, scroll.animScrollY, 0.01f)
    }

    @Test fun aSeekSnapsBackEvenWhenScrolledAway() {
        frames(0.1f, 0, -300f)
        drag(-2_000f)
        now += 16
        scroll.updateScroll(1f / 60f, height, 5, -1_500f, snap = true, targetVisiblePx = 0f)
        assertFalse(scroll.hideLineBlur)
        assertEquals(-1_500f, scroll.animScrollY, 0.01f)
    }

    @Test fun aFlingKeepsControlUntilItStops() {
        frames(0.1f, 0, -300f)
        scroll.onDragStart()
        now += 16
        scroll.onDrag(-40f, height)
        scroll.onDragEnd() // lifted mid-motion: flings
        val start = scroll.animScrollY
        frames(0.3f, 1, -600f)
        assertTrue(scroll.hideLineBlur)
        assertNotEquals(start, scroll.animScrollY)
    }

    @Test fun smoothScrollingSpringsToTheLineWithoutOvershooting() {
        scroll.smoothScrolling = true
        frames(0.1f, 0, 0f)
        var furthest = 0f
        repeat(150) {
            frames(1f / 60f, 1, -400f)
            furthest = minOf(furthest, scroll.animScrollY)
        }
        assertTrue(furthest >= -400f)
        assertEquals(-400f, scroll.animScrollY, 0.01f)
    }

    @Test fun smoothScrollingCarriesItsSpeedIntoTheNextLine() {
        val glide = ScrollManager(clockMs = { now })
        scroll.smoothScrolling = true
        for (manager in listOf(scroll, glide)) {
            manager.updateScroll(1f / 60f, height, 0, 0f)
            repeat(12) { manager.updateScroll(1f / 60f, height, 1, -400f) }
        }
        // Mid-move the line changes again: the spring's next step keeps going at speed, the
        // glide starts over from a standstill.
        val springBefore = scroll.animScrollY
        val glideBefore = glide.animScrollY
        scroll.updateScroll(1f / 60f, height, 2, -800f)
        glide.updateScroll(1f / 60f, height, 2, -800f)
        assertTrue(springBefore - scroll.animScrollY > glideBefore - glide.animScrollY)
    }

    @Test fun switchingSmoothScrollingOffMidMoveStillLands() {
        scroll.smoothScrolling = true
        frames(0.1f, 0, 0f)
        frames(0.1f, 1, -400f)
        scroll.smoothScrolling = false
        frames(2f, 1, -400f)
        assertEquals(-400f, scroll.animScrollY, 0.01f)
    }

    @Test fun aWheelNotchEasesTheViewAndHidesTheBlur() {
        frames(0.1f, 0, -300f)
        scroll.onWheel(-100f, height)
        assertTrue(scroll.hideLineBlur)
        // Eased, not jumped: partway after one frame, there within a few tenths of a second.
        now += 16
        scroll.updateScroll(1f / 60f, height, 0, -300f, targetVisiblePx = 100f)
        assertTrue(scroll.animScrollY < -300f && scroll.animScrollY > -400f)
        frames(0.3f, 0, -300f)
        assertEquals(-400f, scroll.animScrollY, 0.01f)
    }

    @Test fun wheelNotchesAddUpFromWhereTheLastOneWasGoing() {
        frames(0.1f, 0, -300f)
        scroll.onWheel(-100f, height)
        scroll.onWheel(-100f, height)
        frames(0.4f, 0, -300f)
        assertEquals(-500f, scroll.animScrollY, 0.01f)
    }

    @Test fun theWheelStaysInsideTheLyrics() {
        frames(0.1f, 0, -300f)
        scroll.onWheel(1_000f, height)
        frames(0.4f, 0, -300f)
        assertEquals(0f, scroll.animScrollY, 0.01f)
        scroll.onWheel(-10_000f, height)
        frames(0.4f, 0, -300f)
        assertEquals(-height, scroll.animScrollY, 0.01f)
    }

    @Test fun autoScrollComesBackAfterTheWheelAsAfterATouch() {
        frames(0.1f, 0, -300f)
        scroll.onWheel(-100f, height)
        frames(0.5f, 1, -600f)
        assertEquals(-400f, scroll.animScrollY, 0.01f)
        assertTrue(scroll.hideLineBlur)
        frames(2f, 1, -600f)
        assertFalse(scroll.hideLineBlur)
        assertEquals(-600f, scroll.animScrollY, 1f)
    }

    @Test fun theScrollbarFollowsItsThumbExactlyAndNeverFlings() {
        frames(0.1f, 0, -300f)
        scroll.onDragStart()
        now += 16
        scroll.onScrollbarDrag(-250f, height)
        scroll.onScrollbarRelease() // let go mid-motion: a scrollbar still doesn't fling
        assertEquals(-550f, scroll.animScrollY, 0.01f)
        frames(0.3f, 0, -300f, visible = 0f)
        assertEquals(-550f, scroll.animScrollY, 0.01f)
        assertTrue(scroll.hideLineBlur)
        // And it stops at the ends, with no rubber band.
        scroll.onDragStart()
        scroll.onScrollbarDrag(10_000f, height)
        assertEquals(0f, scroll.animScrollY, 0.01f)
    }

    @Test fun theScrollbarSaysHowFarDownTheViewIs() {
        frames(0.1f, 0, -1_000f)
        val span = scroll.scrollbar(height, 1_000f)!!
        assertEquals(0.2f, span.position, 0.0001f)
        assertEquals(1_000f / 6_000f, span.visible, 0.0001f)
        assertEquals(height, span.rangePx, 0.01f)
        // Lyrics that don't scroll have no scrollbar.
        assertEquals(null, ScrollManager().scrollbar(0f, 1_000f))
    }
}
