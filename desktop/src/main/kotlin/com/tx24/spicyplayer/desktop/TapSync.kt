package com.tx24.spicyplayer.desktop

import com.tx24.spicyplayer.lyrics.TimedLine
import com.tx24.spicyplayer.lyrics.spicy.models.LineRole
import com.tx24.spicyplayer.network.data.spotify.AudioAnalysis
import kotlin.math.abs

/**
 * Finding a player's lyric offset from taps along to the song's beats. Each tap is where the lyric
 * clock (offset already applied) was when a beat was heard, so how far the taps sit from the beats
 * is how far the offset is still off.
 *
 * The first taps, while the hand finds the beat, and taps far from any beat don't count. The median
 * of the rest is the correction, and their spread says whether it can be trusted.
 */
object TapSync {
    /** Taps that count before there is an answer. */
    const val MIN_TAPS = 12
    /** Taps left out at the start while the hand finds the beat. */
    const val WARMUP_TAPS = 4
    /** A tap further than this share of a beat from its nearest beat is a miss. */
    private const val MATCH_FRACTION = 0.45
    /** Taps spread wider than this (median distance from their median) are too uneven to trust. */
    const val MAX_SPREAD_MS = 40L
    /** Beats Spotify is less sure of than this are left out. */
    private const val BEAT_CONFIDENCE = 0.2f

    /** [offsetMs]: the offset the taps call for. [spreadMs]: how unevenly they fell. [taps]: how many counted. */
    data class Estimate(val offsetMs: Long, val spreadMs: Long, val taps: Int) {
        val steady get() = spreadMs <= MAX_SPREAD_MS
    }

    /**
     * [tapClockMs]: the lyric clock at each tap, in order. [beatsMs]: the song's beats, sorted.
     * [currentOffsetMs]: the offset the clock ran with. Null until enough taps count.
     */
    fun estimate(tapClockMs: List<Long>, beatsMs: LongArray, currentOffsetMs: Long): Estimate? {
        if (beatsMs.size < 2) return null
        val early = tapClockMs.drop(WARMUP_TAPS).mapNotNull { at ->
            val i = nearest(beatsMs, at)
            val beatLength = if (i + 1 < beatsMs.size) beatsMs[i + 1] - beatsMs[i] else beatsMs[i] - beatsMs[i - 1]
            (at - beatsMs[i]).takeIf { abs(it) <= beatLength * MATCH_FRACTION }
        }
        if (early.size < MIN_TAPS) return null
        val median = median(early)
        // The clock ran `median` ahead of the sound: show the lyrics that much later.
        return Estimate(currentOffsetMs - median, median(early.map { abs(it - median) }), early.size)
    }

    /** The beats of [analysis] in ms, or null when it has too few. */
    fun beatsMs(analysis: AudioAnalysis?): LongArray? = analysis?.beats
        ?.filter { it.confidence >= BEAT_CONFIDENCE }
        ?.map { (it.start * 1000f).toLong() }
        ?.takeIf { it.size >= 2 }
        ?.toLongArray()

    private fun nearest(sorted: LongArray, value: Long): Int {
        var lo = 0
        var hi = sorted.size - 1
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sorted[mid] < value) lo = mid + 1 else hi = mid
        }
        return if (lo > 0 && value - sorted[lo - 1] < sorted[lo] - value) lo - 1 else lo
    }

    private fun median(values: List<Long>): Long = values.sorted().let { s ->
        if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }
}

/** The lead line sung at [positionMs], or null before the first and in gaps between lines. */
fun lineAt(lines: List<TimedLine>, positionMs: Long): TimedLine? =
    lines.lastOrNull { it.role == LineRole.LEAD && it.startMs <= positionMs }?.takeIf { positionMs <= it.endMs }

/** A line's words as text. */
fun TimedLine.text() = words.joinToString("") { (if (it.attached) "" else " ") + it.text }.trim()
