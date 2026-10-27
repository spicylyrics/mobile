package com.tx24.spicyplayer.desktop

import com.tx24.spicyplayer.lyrics.TimedLine
import com.tx24.spicyplayer.lyrics.TimedWord
import com.tx24.spicyplayer.lyrics.spicy.models.LineRole
import com.tx24.spicyplayer.network.data.spotify.AudioAnalysis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TapSyncTest {
    /** A beat every 500 ms from 10 s on. */
    private val beats = LongArray(100) { 10_000L + it * 500L }

    /** [count] taps on successive beats, [lateMs] after each, with [jitter] added in turn. */
    private fun taps(count: Int, lateMs: Long, jitter: List<Long> = listOf(0L)) =
        List(count) { beats[it] + lateMs + jitter[it % jitter.size] }

    @Test
    fun `no answer until enough taps count`() {
        assertNull(TapSync.estimate(taps(TapSync.WARMUP_TAPS + TapSync.MIN_TAPS - 1, 0), beats, 0))
        assertEquals(0L, TapSync.estimate(taps(TapSync.WARMUP_TAPS + TapSync.MIN_TAPS, 0), beats, 0)?.offsetMs)
        assertNull(TapSync.estimate(taps(30, 0), longArrayOf(1000), 0))
    }

    @Test
    fun `a clock ahead of the sound wants the lyrics later`() {
        // Taps land 80 ms past each beat on the clock: the clock runs 80 ms ahead.
        val estimate = TapSync.estimate(taps(20, 80), beats, currentOffsetMs = 100)!!
        assertEquals(20L, estimate.offsetMs)
        assertEquals(16, estimate.taps)
        assertTrue(estimate.steady)
    }

    @Test
    fun `a clock behind the sound wants the lyrics earlier`() {
        assertEquals(120L, TapSync.estimate(taps(20, -120), beats, currentOffsetMs = 0)?.offsetMs)
    }

    @Test
    fun `misses are left out and uneven taps aren't steady`() {
        // Every fourth tap nearly half a beat out: dropped.
        val withMisses = taps(24, 30).mapIndexed { i, t -> if (i % 4 == 3) t + 200 else t }
        assertEquals(-30L, TapSync.estimate(withMisses, beats, 0)?.offsetMs)
        val uneven = TapSync.estimate(taps(20, 0, jitter = listOf(-120L, 120L, -60L, 60L)), beats, 0)!!
        assertFalse(uneven.steady)
    }

    @Test
    fun `beats from an analysis drop the unsure ones`() {
        val analysis = AudioAnalysis(120f, -8f, emptyList(), listOf(
            AudioAnalysis.Beat(1.0f, 0.5f, 0.9f),
            AudioAnalysis.Beat(1.5f, 0.5f, 0.1f),
            AudioAnalysis.Beat(2.0f, 0.5f, 0.5f),
        ))
        assertEquals(listOf(1000L, 2000L), TapSync.beatsMs(analysis)?.toList())
        assertNull(TapSync.beatsMs(null))
        assertNull(TapSync.beatsMs(AudioAnalysis(120f, -8f, emptyList(), listOf(AudioAnalysis.Beat(1f, 0.5f, 1f)))))
    }

    @Test
    fun `the line at a position`() {
        fun line(start: Long, end: Long, text: String, role: LineRole = LineRole.LEAD) =
            TimedLine(start, end, text.split(" ").map { TimedWord(it, start, end, attached = false) }, role)
        val lines = listOf(line(1000, 3000, "first line"), line(2000, 2500, "echo", LineRole.BACKGROUND), line(4000, 6000, "second"))
        assertNull(lineAt(lines, 500))
        assertEquals("first line", lineAt(lines, 2200)?.text())
        assertNull(lineAt(lines, 3500))
        assertEquals("second", lineAt(lines, 4000)?.text())
        assertNull(lineAt(emptyList(), 0))
    }
}
