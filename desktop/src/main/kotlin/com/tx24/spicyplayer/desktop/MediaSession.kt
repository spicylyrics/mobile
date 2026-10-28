package com.tx24.spicyplayer.desktop

import com.tx24.spicyplayer.playback.LyricClock
import com.tx24.spicyplayer.playback.ReportFilter
import java.io.File

/** A track as the platform's media session names it. */
data class Track(val app: String, val title: String, val artist: String, val album: String, val durationMs: Long) {
    val key get() = "$title\u001f$artist"
}

sealed interface SessionEvent {
    data class NewTrack(val track: Track) : SessionEvent

    /** A report on the current track, and what the clock made of it. */
    data class Moved(
        val event: String,
        val status: String,
        val positionMs: Long,
        val ageMs: Long,
        val clockBeforeMs: Long,
        val outcome: LyricClock.Outcome,
    ) : SessionEvent
}

/**
 * Follows one player through a [MediaSource]'s snapshots: the current one, or the first whose app
 * id contains [appFilter]. Keeps [clock] on its position, [offsetMs] ahead of what the player
 * reports (positive shows the lyrics earlier).
 *
 * How each player reports through Windows' media sessions, as measured through the gsmtc helper:
 * - Spotify (playing on this PC) and Screenbox: the position to the ms, every 4.5-5 s and on every
 *   change. Taken as it comes.
 * - Spotify showing a Spotify Connect device: as honest while playing, but a pause or seek shows up
 *   0.7-1.1 s late, so the clock overruns a pause by that much before stepping back. A position
 *   that stops moving under a fresh stamp while playing is a stalled relay, and the clock carries on.
 * - Chrome and other Chromium browsers (YouTube Music): a report only on play, pause and seek,
 *   so the clock runs on its own in between. The report after a seek or a new song counts from
 *   before the audio is heard and runs ~65 ms ahead ([CHROMIUM_LEAD_MS]); a pause report is
 *   ~85 ms short of where the audio stops, and the resume report has it right.
 * - Apple Music: the position rounded down to the second, about twice a second; [ReportFilter]
 *   narrows it to a window a few hundred ms wide and holds the clock steady inside it. It ignored
 *   the helper's seek requests.
 * - Windows Media Player Legacy (wmplayer.exe): has no media session, so it is never seen.
 */
class MediaSession(private val appFilter: String? = null, private val offsetMs: Long = 0L) {
    val clock = LyricClock(monoMs())
    private val filter = ReportFilter()

    @Volatile var track: Track? = null
        private set

    /** The current track's thumbnail, a file the platform wrote; it can come a little after the title. */
    @Volatile var cover: File? = null
        private set

    private var lastStampMs = 0L
    private var lastStatus: PlaybackStatus? = null
    /** Whether the status changed since the stamp did: every later report with that stamp is stale. */
    private var stampOutlived = false
    /** The last report as the platform sent it, to drop the copies some players send. */
    private var lastRaw: List<Any?>? = null

    /** Takes one snapshot, received at [receivedMono] (monotonic) and [receivedWall] (epoch) ms. */
    fun feed(snapshot: MediaSnapshot, receivedMono: Long, receivedWall: Long): SessionEvent? {
        val app = snapshot.app
        val picked = if (appFilter != null) app.contains(appFilter, ignoreCase = true) else snapshot.current
        if (!picked) return null

        val title = snapshot.title
        cover = snapshot.cover
        val status = snapshot.status
        val playing = status == PlaybackStatus.Playing
        // The platform stamps the position in wall time; the clock runs on the monotonic one.
        val stamp = snapshot.updatedAtWallMs
        var updatedAtMono = receivedMono - (receivedWall - stamp)
        var positionMs = snapshot.positionMs
        // A status change can arrive a few ms before its timeline, carrying the old stamp: a
        // resume would count on through the whole pause, a pause would stop at the last
        // report. Until the timeline comes, a resume starts from where the clock stopped and
        // a pause stops the clock where it is.
        // Chrome resumes under its pause's stamp too, so once the status has changed under a
        // stamp, nothing more with that stamp is trusted.
        stampOutlived = stamp == lastStampMs && (stampOutlived || status != lastStatus)
        lastStampMs = stamp
        lastStatus = status
        val speed = if (playing) (snapshot.rate ?: 1f) else 0f
        val next = Track(app, title, snapshot.artist, snapshot.album, snapshot.durationMs)
        val newTrack = next.key != track?.key && title.isNotBlank()
        val raw = listOf(app, next.key, status, positionMs, stamp, speed)
        if (raw == lastRaw && !newTrack) return null
        lastRaw = raw
        if (stampOutlived && !newTrack) {
            positionMs = clock.positionAt(receivedMono)
            updatedAtMono = receivedMono
        } else {
            filter.leadAfterJumpMs = if (isChromium(app)) CHROMIUM_LEAD_MS else 0L
            val clockMs = if (newTrack) 0L else clock.positionAt(receivedMono) - offsetMs
            val filtered = filter.positionAt(positionMs, updatedAtMono, playing, speed, clockMs, receivedMono, newTrack)
            if (filtered != null) {
                positionMs = filtered
                updatedAtMono = receivedMono
            }
            positionMs += offsetMs
        }
        val report = LyricClock.Report(
            state = when (status) { PlaybackStatus.Playing -> 3; PlaybackStatus.Paused -> 2; PlaybackStatus.Stopped -> 1; else -> 0 },
            playing = playing,
            positionMs = positionMs,
            updatedAtMs = updatedAtMono,
            speed = speed,
        )
        if (newTrack) {
            track = next
            clock.startTrack(receivedMono, report.speed, playing)
            clock.onReport(report, receivedMono, snap = true)
            return SessionEvent.NewTrack(next)
        }
        if (!clock.isFresh(report)) return null
        val before = clock.positionAt(receivedMono)
        val outcome = clock.onReport(report, receivedMono)
        return SessionEvent.Moved(snapshot.event, status.name, report.positionMs, receivedWall - stamp, before, outcome)
    }

    /**
     * Asks the player to jump to [positionMs], and moves the clock there now rather than on its next
     * report. Does nothing when [source] can't seek.
     */
    fun seek(source: MediaSource, positionMs: Long) {
        val app = track?.app ?: return
        if (Transport.SEEK !in source.capabilities) return
        clock.seekTo(positionMs, monoMs())
        source.control(app, Transport.SEEK, (positionMs - offsetMs).coerceAtLeast(0L))
    }
}

/** The checkout's root, found from the working directory, where the helper and `.env` live. */
val projectRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
    .firstOrNull { File(it, "settings.gradle.kts").exists() } ?: File("").absoluteFile

/** How far a Chromium browser's report after a seek or a new song runs ahead of the audio. */
const val CHROMIUM_LEAD_MS = 65L

/** Chrome, Edge and the like, and the apps they install (a 16-digit hex id). */
fun isChromium(app: String) =
    Regex("chrome|msedge|brave|opera|vivaldi", RegexOption.IGNORE_CASE).containsMatchIn(app) || Regex("[0-9A-F]{16}").matches(app)
