package com.tx24.spicyplayer.desktop

import com.google.gson.JsonObject
import com.tx24.spicyplayer.playback.LyricClock
import java.io.File

/** A track as Windows' media session names it. */
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
 * Follows one of Windows' media sessions through the gsmtc helper's JSON lines: the current one,
 * or the first whose app id contains [appFilter]. Keeps [clock] on its position.
 */
class MediaSession(private val appFilter: String? = null) {
    val clock = LyricClock(monoMs())

    @Volatile var track: Track? = null
        private set

    /** The current track's thumbnail, a file the helper wrote; it can come a little after the title. */
    @Volatile var cover: File? = null
        private set

    private var lastStampMs = 0L
    private var lastStatus = ""
    /** Whether the status changed since the stamp did: every later report with that stamp is stale. */
    private var stampOutlived = false

    /** Takes one helper line, received at [receivedMono] (monotonic) and [receivedWall] (epoch) ms. */
    fun feed(line: JsonObject, receivedMono: Long, receivedWall: Long): SessionEvent? {
        if (!line.has("status")) return null
        val app = line.str("app")
        val picked = if (appFilter != null) app.contains(appFilter, ignoreCase = true) else line["current"]?.asBoolean == true
        if (!picked) return null

        val title = line.str("title")
        cover = line.str("cover").takeIf(String::isNotBlank)?.let(::File)
        // Apple Music for Windows sends "artist — album" as the artist and no album.
        val (artist, album) = line.str("artist").let { raw ->
            if (line.str("album").isBlank() && " — " in raw) raw.substringBeforeLast(" — ") to raw.substringAfterLast(" — ")
            else raw to line.str("album")
        }
        val status = line.str("status")
        val playing = status == "Playing"
        // GSMTC stamps the position in wall time; the clock runs on the monotonic one.
        var updatedAtMono = receivedMono - (receivedWall - line["lastUpdatedMs"].asLong)
        var positionMs = line["positionMs"].asLong
        // A status change can arrive a few ms before its timeline, carrying the old stamp: a
        // resume would count on through the whole pause, a pause would stop at the last
        // report. Until the timeline comes, a resume starts from where the clock stopped and
        // a pause stops the clock where it is.
        // Chrome resumes under its pause's stamp too, so once the status has changed under a
        // stamp, nothing more with that stamp is trusted.
        val stamp = line["lastUpdatedMs"].asLong
        stampOutlived = stamp == lastStampMs && (stampOutlived || status != lastStatus)
        lastStampMs = stamp
        lastStatus = status
        if (stampOutlived && track?.key == "$title\u001f$artist") {
            positionMs = clock.positionAt(receivedMono)
            updatedAtMono = receivedMono
        }
        val report = LyricClock.Report(
            state = when (status) { "Playing" -> 3; "Paused" -> 2; "Stopped" -> 1; else -> 0 },
            playing = playing,
            positionMs = positionMs,
            updatedAtMs = updatedAtMono,
            speed = if (playing) (line["rate"]?.takeUnless { it.isJsonNull }?.asFloat ?: 1f) else 0f,
        )
        val next = Track(app, title, artist, album, line["durationMs"].asLong)
        if (next.key != track?.key && title.isNotBlank()) {
            track = next
            clock.startTrack(receivedMono, report.speed, playing)
            clock.onReport(report, receivedMono, snap = true)
            return SessionEvent.NewTrack(next)
        }
        if (!clock.isFresh(report)) return null
        val before = clock.positionAt(receivedMono)
        val outcome = clock.onReport(report, receivedMono)
        return SessionEvent.Moved(line.str("event"), status, report.positionMs, receivedWall - stamp, before, outcome)
    }

    /** Asks the player to jump to [positionMs], and moves the clock there now rather than on its next report. */
    fun seek(helper: File, positionMs: Long) {
        val app = track?.app ?: return
        clock.seekTo(positionMs, monoMs())
        ProcessBuilder(helper.path, "ctl", app, "seek", positionMs.toString()).redirectErrorStream(true).start()
    }
}

/** The checkout's root, found from the working directory, where the helper and `.env` live. */
val projectRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
    .firstOrNull { File(it, "settings.gradle.kts").exists() } ?: File("").absoluteFile

fun defaultHelper() = File(projectRoot, "desktop/gsmtc/target/release/gsmtc.exe")

fun monoMs() = System.nanoTime() / 1_000_000

internal fun JsonObject.str(name: String) = get(name)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
