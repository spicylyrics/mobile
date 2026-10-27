package com.tx24.spicyplayer.playback

import kotlin.math.abs

/**
 * Fixes up a player's position reports before they reach [LyricClock], for the players whose
 * reports aren't good to the millisecond. An honest report passes through untouched.
 *
 * - **Whole seconds.** Some players (Apple Music for Windows) report the position rounded down to
 *   the second, several times a second, each stamped when it was made. One such report only says
 *   the song is somewhere in a one-second window, so taking each as it comes jumps the clock back
 *   and forth by up to a second. Each report rules out where the song *can't* be, though: the
 *   reports of the last [WINDOW_MS] together narrow it to a window a few hundred ms wide, since
 *   they land at different points within their seconds. The clock holds still while it is near
 *   that window's middle, and moves there when the window narrows away from it. A player counts
 *   as one once [WHOLE_RUN] reports in a row land on whole seconds, and stops counting as soon as
 *   one doesn't.
 * - **A frozen position.** A playing report with the same position as the last one but a newer
 *   stamp says the song stood still while playing. A player relaying another device (Spotify
 *   showing a Spotify Connect device) can send those when the relay stalls; the clock carries on.
 * - **A lead after a jump** ([leadAfterJumpMs]). A player whose position counts on from the
 *   moment of a seek or a new song, before its audio is heard, runs ahead by that much until its
 *   next report, which on a browser can be minutes away.
 *
 * Plain Kotlin, no Android: times are a monotonic ms clock, passed in.
 */
class ReportFilter(var leadAfterJumpMs: Long = 0L) {
    /** One whole-second report: the song's start lies in (startMs - 1000, startMs], on the monotonic clock. */
    private class Sighting(val atMs: Long, val startMs: Long)

    private val sightings = ArrayDeque<Sighting>()
    private var wholeRun = 0
    private var runFirstMs = -1L
    private var lastPlaying: Pair<Long, Long>? = null

    /** Whether the player is reporting whole seconds. */
    var wholeSeconds = false
        private set

    /**
     * Where a report from the player puts the song at [nowMs], or null to take it as it is.
     * [clockMs]: where the clock has the song at [nowMs]. [newTrack]: the report starts a song.
     */
    fun positionAt(
        positionMs: Long,
        updatedAtMs: Long,
        playing: Boolean,
        speed: Float,
        clockMs: Long,
        nowMs: Long,
        newTrack: Boolean = false,
    ): Long? {
        if (newTrack) reset()
        trackWholeSeconds(positionMs)
        val previous = lastPlaying
        lastPlaying = if (playing) positionMs to updatedAtMs else null

        if (wholeSeconds && (speed == 1f || !playing)) return wholeSecond(positionMs, updatedAtMs, playing, clockMs, nowMs)
        if (!playing) return null
        if (speed > 0f && previous != null && previous.first == positionMs && updatedAtMs > previous.second && !newTrack) return clockMs
        if (leadAfterJumpMs != 0L) {
            val reported = positionMs + ((nowMs - updatedAtMs).coerceAtLeast(0L) * speed).toLong()
            if (newTrack || abs(reported - clockMs) >= LyricClock.JUMP_MS) return (reported - leadAfterJumpMs).coerceAtLeast(0L)
        }
        return null
    }

    private fun trackWholeSeconds(positionMs: Long) {
        if (positionMs % 1000L != 0L) {
            wholeRun = 0
            wholeSeconds = false
            sightings.clear()
            return
        }
        if (wholeRun == 0) runFirstMs = positionMs
        wholeRun++
        // A run that never moves is a song paused or starting at a whole second.
        if (wholeRun >= WHOLE_RUN && positionMs != runFirstMs) wholeSeconds = true
    }

    private fun wholeSecond(positionMs: Long, updatedAtMs: Long, playing: Boolean, clockMs: Long, nowMs: Long): Long {
        // Paused, the song is somewhere in the reported second; where the clock stopped is the best guess.
        if (!playing) return clockMs.coerceIn(positionMs, positionMs + 999L)
        val start = updatedAtMs - positionMs
        while (sightings.isNotEmpty() && updatedAtMs - sightings.first().atMs > WINDOW_MS) sightings.removeFirst()
        // A report the window can't hold is a seek or a resume: start over from it.
        if (sightings.isNotEmpty() && (start <= latestStart() - 1000L - SLACK_MS || start - 1000L >= earliestStart() + SLACK_MS)) {
            sightings.clear()
        }
        sightings.addLast(Sighting(updatedAtMs, start))
        val lo = latestStart() - 1000L
        val hi = earliestStart()
        val middle = (lo + hi) / 2
        if (abs(nowMs - clockMs - middle) <= HOLD_MS) return clockMs
        return nowMs - middle
    }

    private fun latestStart() = sightings.maxOf { it.startMs }
    private fun earliestStart() = sightings.minOf { it.startMs }

    /** A new song or a lost player: nothing seen so far counts. */
    fun reset() {
        sightings.clear()
        wholeRun = 0
        wholeSeconds = false
        lastPlaying = null
    }

    companion object {
        /** Whole-second reports in a row, at two or more positions, before a player counts as reporting whole seconds. */
        const val WHOLE_RUN = 3
        /** How far back whole-second reports narrow the song's position. */
        const val WINDOW_MS = 20_000L
        /** Stamp jitter: a report this far outside the window still belongs to it. */
        const val SLACK_MS = 40L
        /** The clock this close to the window's middle is left where it is. */
        const val HOLD_MS = 50L
    }
}
