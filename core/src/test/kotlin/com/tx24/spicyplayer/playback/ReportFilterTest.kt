package com.tx24.spicyplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ReportFilterTest {
    private val playing = 3
    private val paused = 2

    /** A filter in front of a clock, fed reports the way the desktop session feeds them. */
    private class Rig(lead: Long = 0L) {
        val filter = ReportFilter(lead)
        val clock = LyricClock(0L)
        var moves = 0
        var started = false

        fun report(nowMs: Long, positionMs: Long, updatedAtMs: Long = nowMs, isPlaying: Boolean = true): LyricClock.Outcome {
            val newTrack = !started
            started = true
            val clockMs = if (newTrack) 0L else clock.positionAt(nowMs)
            val filtered = filter.positionAt(positionMs, updatedAtMs, isPlaying, if (isPlaying) 1f else 0f, clockMs, nowMs, newTrack)
            val report = LyricClock.Report(
                state = if (isPlaying) 3 else 2,
                playing = isPlaying,
                positionMs = filtered ?: positionMs,
                updatedAtMs = if (filtered != null) nowMs else updatedAtMs,
                speed = if (isPlaying) 1f else 0f,
            )
            if (newTrack) {
                clock.startTrack(nowMs, report.speed, isPlaying)
                return clock.onReport(report, nowMs, snap = true)
            }
            return clock.onReport(report, nowMs).also { if (it.appliedMs != 0L) moves++ }
        }
    }

    /**
     * Apple Music's reports: the song (which started at [startMs]) rounded down to the second,
     * every ~540 ms, each sent twice, from [fromMs] to [toMs]. Returns the clock's worst error.
     */
    private fun Rig.wholeSecondPlayer(startMs: Long, fromMs: Long, toMs: Long, step: Long = 541L): Long {
        var worst = 0L
        var t = fromMs
        var i = 0
        while (t < toMs) {
            val truth = t - startMs
            val floored = truth / 1000 * 1000
            report(t, floored, updatedAtMs = t - 4)
            report(t + 1, floored, updatedAtMs = t - 4)
            // How far off the lyrics are, checked between reports once the window has narrowed.
            if (t - fromMs > 3_000) worst = maxOf(worst, abs(clock.positionAt(t + 200) - (t + 200 - startMs)))
            t += step + (i++ % 3) * 7
        }
        return worst
    }

    @Test fun honestReportsPassThrough() {
        val filter = ReportFilter()
        assertNull(filter.positionAt(10_123L, 990L, true, 1f, clockMs = 10_130L, nowMs = 1_000L, newTrack = true))
        assertNull(filter.positionAt(14_627L, 5_495L, true, 1f, clockMs = 14_635L, nowMs = 5_500L))
        assertNull(filter.positionAt(14_700L, 6_000L, false, 0f, clockMs = 14_700L, nowMs = 6_000L))
        assertFalse(filter.wholeSeconds)
    }

    @Test fun anHonestReportOnAWholeSecondIsNotWholeSecondMode() {
        // A seek lands on 90 000 exactly, and a paused song sits at 0 three times.
        val filter = ReportFilter()
        filter.positionAt(0L, 0L, false, 0f, 0L, 0L, newTrack = true)
        filter.positionAt(0L, 100L, false, 0f, 0L, 100L)
        filter.positionAt(0L, 200L, true, 1f, 0L, 200L)
        assertFalse(filter.wholeSeconds)
        filter.positionAt(90_000L, 1_000L, true, 1f, 90_000L, 1_000L)
        filter.positionAt(90_090L, 1_090L, true, 1f, 90_090L, 1_090L)
        assertFalse(filter.wholeSeconds)
    }

    @Test fun wholeSecondReportsHoldASteadyClock() {
        val rig = Rig()
        val worst = rig.wholeSecondPlayer(startMs = -21_300L, fromMs = 0L, toMs = 60_000L)
        assertTrue(rig.filter.wholeSeconds)
        // Taken as they come, these reports move the clock ±500 ms twice a second.
        assertTrue("worst error $worst ms", worst <= 150L)
        assertTrue("${rig.moves} moves", rig.moves <= 6)
    }

    @Test fun wholeSecondPhaseIsFoundWhereverTheSongStarted() {
        for (phase in listOf(0L, 130L, 499L, 500L, 777L, 999L)) {
            val rig = Rig()
            val worst = rig.wholeSecondPlayer(startMs = -20_000L - phase, fromMs = 0L, toMs = 30_000L)
            assertTrue("phase $phase: worst error $worst ms", worst <= 150L)
        }
    }

    @Test fun wholeSecondPauseStaysInItsSecond() {
        val rig = Rig()
        rig.wholeSecondPlayer(startMs = -20_400L, fromMs = 0L, toMs = 10_000L)
        val stoppedAt = rig.clock.positionAt(10_050L)
        // Paused at 30.45 s: Apple Music says 30 000.
        rig.report(10_050L, 30_000L, isPlaying = false)
        assertFalse(rig.clock.isPlaying)
        assertTrue("stopped at ${rig.clock.positionAt(10_050L)}", abs(rig.clock.positionAt(10_050L) - stoppedAt) < 150L)
        assertEquals(0L, rig.clock.biasMs)
        // Resumed five seconds later; the song carries on from 30.45 s.
        val worst = rig.wholeSecondPlayer(startMs = 15_050L - 30_450L, fromMs = 15_050L, toMs = 30_000L)
        assertTrue("worst error after resume $worst ms", worst <= 150L)
        assertEquals(0L, rig.clock.biasMs)
    }

    @Test fun wholeSecondSeekMovesTheClockAtOnce() {
        val rig = Rig()
        rig.wholeSecondPlayer(startMs = -20_400L, fromMs = 0L, toMs = 10_000L)
        // A seek to 90.2 s.
        rig.report(10_000L, 90_000L, updatedAtMs = 9_800L)
        assertTrue(abs(rig.clock.positionAt(10_000L) - 90_200L) < 600L)
        val worst = rig.wholeSecondPlayer(startMs = 9_800L - 90_000L, fromMs = 10_500L, toMs = 30_000L)
        assertTrue("worst error after seek $worst ms", worst <= 150L)
    }

    @Test fun aNonWholeReportEndsWholeSecondMode() {
        val rig = Rig()
        rig.wholeSecondPlayer(startMs = -20_400L, fromMs = 0L, toMs = 5_000L)
        assertTrue(rig.filter.wholeSeconds)
        rig.report(5_000L, 25_437L)
        assertFalse(rig.filter.wholeSeconds)
        assertEquals(25_437L, rig.clock.positionAt(5_000L))
    }

    @Test fun aFrozenPositionWhilePlayingIsIgnored() {
        val rig = Rig()
        rig.report(0L, 50_000L)
        rig.report(4_500L, 54_500L)
        // The relay stalls: the same position under newer stamps.
        assertEquals(0L, rig.report(9_000L, 54_500L, updatedAtMs = 9_000L).appliedMs)
        assertEquals(0L, rig.report(13_500L, 54_500L, updatedAtMs = 13_500L).appliedMs)
        assertEquals(63_500L, rig.clock.positionAt(13_500L))
        // Moving again, it's taken as it comes.
        rig.report(18_000L, 66_000L)
        assertEquals(66_000L, rig.clock.positionAt(18_000L))
    }

    @Test fun aLeadAfterAJumpIsTakenOff() {
        // Chrome: the song's first report and a seek's both run 65 ms ahead.
        val rig = Rig(lead = 65L)
        rig.report(1_000L, 5_013L)
        assertEquals(5_013L - 65L, rig.clock.positionAt(1_000L))
        // A report under the lead into the song can only start the clock at 0.
        assertEquals(0L, rig.filter.positionAt(13L, 2_000L, true, 1f, 0L, 2_000L, newTrack = true))
        rig.report(130_000L, 30_013L)
        assertEquals(30_013L - 65L, rig.clock.positionAt(130_000L))
        // A report near the clock (a pause, a resume) is left alone.
        rig.report(150_000L, 49_960L, isPlaying = false)
        assertEquals(49_960L, rig.clock.positionAt(150_000L))
    }

    @Test fun noLeadMeansNoChange() {
        val filter = ReportFilter()
        assertNull(filter.positionAt(30_013L, 130_000L, true, 1f, clockMs = 115_000L, nowMs = 130_000L))
    }
}
