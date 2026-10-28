package com.tx24.spicyplayer.desktop

import java.io.File
import java.util.Locale
import kotlin.math.roundToLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

/**
 * Spotify and Music on macOS, polled about once a second through AppleScript (`osascript`). One
 * script asks every installed player that is running (so a poll never launches one) for its state,
 * position and track, a line each in [MAC_SEP]-separated fields; [parseMacLine] reads them.
 *
 * The position is the player's own, stamped with the middle of the poll, so each poll is a fresh
 * report the lyric clock resyncs on. Players that aren't playing stay on the one followed last, and
 * a playing one takes over. macOS asks once per player for permission to control it (Automation);
 * until that's granted, the poll comes back empty.
 *
 * Other players (browsers, VLC) would need the private MediaRemote framework, which macOS 15.4 and
 * later only lets Apple-signed processes read.
 */
class MacMediaSource(
    private val osascript: String = OSASCRIPT,
    private val installed: () -> List<MacPlayer> = { installedMacPlayers() },
) : MediaSource {
    override val name = "macos"
    override val unavailableReason
        get() = if (File(osascript).canExecute()) null else "osascript isn't available, so players can't be followed"
    override val capabilities = Transport.entries.toSet()

    @Volatile private var stopped = false

    override fun updates(): Flow<Received> = flow {
        stopped = false
        var current: String? = null
        var seen = emptySet<String>()
        var warned = false
        while (currentCoroutineContext().isActive && !stopped) {
            val players = installed()
            val startedMono = monoMs()
            val startedWall = System.currentTimeMillis()
            val (exit, output) = run(listOf(osascript) + macPollArgs(players))
            // The positions were read somewhere inside the call: its middle is the best guess.
            val receivedMono = monoMs()
            val stampWall = startedWall + (receivedMono - startedMono) / 2
            if (exit != 0 && !warned) {
                System.err.println("osascript exited $exit; is Automation allowed for Spicy Lyrics in System Settings > Privacy & Security?")
                warned = true
            }
            val snapshots = output.lines().mapNotNull { parseMacLine(it, stampWall) }
            current = pickMacCurrent(snapshots, current)
            for (gone in seen - snapshots.map { it.app }.toSet()) {
                emit(Received(MediaUpdate.Removed(gone), receivedMono, System.currentTimeMillis(), ""))
            }
            seen = snapshots.map { it.app }.toSet()
            for (s in snapshots) {
                val snapshot = s.copy(current = s.app == current)
                emit(Received(MediaUpdate.Snapshot(snapshot), receivedMono, System.currentTimeMillis(), output))
            }
            delay(POLL_MS)
        }
    }.flowOn(Dispatchers.IO)

    override fun control(app: String, action: Transport, positionMs: Long) {
        val script = macControlScript(app, action, positionMs) ?: return
        ProcessBuilder(osascript, "-e", script).redirectErrorStream(true).start()
    }

    override val canShuffle = true
    override val canRepeat = true

    override fun setShuffle(app: String, on: Boolean) {
        val script = macShuffleScript(app, on) ?: return
        ProcessBuilder(osascript, "-e", script).redirectErrorStream(true).start()
    }

    override fun setRepeat(app: String, mode: RepeatMode) {
        val script = macRepeatScript(app, mode) ?: return
        ProcessBuilder(osascript, "-e", script).redirectErrorStream(true).start()
    }

    override fun close() {
        stopped = true
    }

    private fun run(command: List<String>): Pair<Int, String> = runCatching {
        val process = ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val text = process.inputStream.bufferedReader().readText()
        process.waitFor() to text
    }.getOrElse { -1 to "" }

    companion object {
        const val OSASCRIPT = "/usr/bin/osascript"
        const val POLL_MS = 1000L
    }
}

fun macMediaSource(): MediaSource = MacMediaSource()

/**
 * A player the poll asks: its application name, the unit its track duration comes in, and its
 * shuffle and repeat properties. Spotify's repeat is a boolean; Music's is off, one or all.
 */
enum class MacPlayer(
    val appName: String,
    val durationInMs: Boolean,
    val hasArtworkUrl: Boolean,
    val shuffleProperty: String,
    val repeatProperty: String,
    val repeatsOne: Boolean,
) {
    SPOTIFY("Spotify", durationInMs = true, hasArtworkUrl = true, shuffleProperty = "shuffling", repeatProperty = "repeating", repeatsOne = false),
    MUSIC("Music", durationInMs = false, hasArtworkUrl = false, shuffleProperty = "shuffle enabled", repeatProperty = "song repeat", repeatsOne = true),
}

/** Separates the fields of a poll line. */
const val MAC_SEP = "\u001f"

/**
 * The players installed in the usual places. A script that names an app macOS can't find asks the
 * user where it is, so the poll only names installed ones.
 */
fun installedMacPlayers(home: String = System.getProperty("user.home").orEmpty(), exists: (String) -> Boolean = { File(it).exists() }) =
    MacPlayer.entries.filter { player ->
        listOf("/Applications", "/System/Applications", "$home/Applications").any { exists("$it/${player.appName}.app") }
    }

/**
 * `osascript` arguments for one poll of [players]: per running player, one line of name, state,
 * position (s), duration (Spotify ms, Music s), title, artist, album, artwork URL, shuffle and
 * repeat; a player with no track gives just its name, state, shuffle and repeat. Shuffle and repeat
 * are read on their own, so a player without them still reports its track.
 */
fun macPollArgs(players: List<MacPlayer>): List<String> {
    val lines = mutableListOf("set sep to character id 31", "set out to \"\"")
    for (p in players) {
        val art = if (p.hasArtworkUrl) "artwork url of t" else "\"\""
        lines += listOf(
            "if application \"${p.appName}\" is running then",
            "tell application \"${p.appName}\"",
            "set st to player state as string",
            "set sh to \"\"",
            "try",
            "set sh to (${p.shuffleProperty} as string)",
            "end try",
            "set rp to \"\"",
            "try",
            "set rp to (${p.repeatProperty} as string)",
            "end try",
            "try",
            "set t to current track",
            "set out to out & \"${p.appName}\" & sep & st & sep & (player position as string) & sep & (duration of t as string) & sep & (name of t) & sep & (artist of t) & sep & (album of t) & sep & ($art) & sep & sh & sep & rp & linefeed",
            "on error",
            "set out to out & \"${p.appName}\" & sep & st & sep & sh & sep & rp & linefeed",
            "end try",
            "end tell",
            "end if",
        )
    }
    lines += "return out"
    return lines.flatMap { listOf("-e", it) }
}

/** One poll line, polled at [stampWall] (epoch ms), or null for one that isn't from a known player. */
fun parseMacLine(raw: String, stampWall: Long): MediaSnapshot? {
    val fields = raw.trimEnd('\r', '\n').split(MAC_SEP)
    val player = MacPlayer.entries.firstOrNull { it.appName == fields[0] } ?: return null
    // Two and eight fields are the lines from before shuffle and repeat.
    if (fields.size !in setOf(2, 4, 8, 10)) return null
    val status = when (fields[1].trim().lowercase()) {
        "playing" -> PlaybackStatus.Playing
        "paused" -> PlaybackStatus.Paused
        "stopped" -> PlaybackStatus.Stopped
        else -> PlaybackStatus.Unknown
    }
    val shuffle = when (fields.getOrNull(fields.size - 2)?.trim()?.takeIf { fields.size == 4 || fields.size == 10 }) {
        "true" -> true
        "false" -> false
        else -> null
    }
    // Spotify's repeat is on or off, which reads as repeating the list.
    val repeat = when (fields.last().trim().takeIf { fields.size == 4 || fields.size == 10 }) {
        "off", "false" -> RepeatMode.Off
        "one" -> RepeatMode.One
        "all", "true" -> RepeatMode.All
        else -> null
    }
    // No track: nothing to follow, whatever the state says.
    if (fields.size <= 4) return MediaSnapshot(player.appName, false, "", "", "", 0, 0, stampWall, status, null, event = "poll", shuffle = shuffle, repeat = repeat, repeatOneSupported = player.repeatsOne)
    val position = seconds(fields[2]) ?: return null
    val duration = number(fields[3]) ?: 0.0
    return MediaSnapshot(
        app = player.appName,
        current = false,
        title = fields[4],
        artist = fields[5],
        album = fields[6],
        durationMs = (if (player.durationInMs) duration else duration * 1000).roundToLong(),
        positionMs = (position * 1000).roundToLong(),
        updatedAtWallMs = stampWall,
        status = status,
        rate = 1f,
        artUrl = fields[7].trim().takeIf { it.isNotEmpty() && it != "missing value" },
        event = "poll",
        shuffle = shuffle,
        repeat = repeat,
        repeatOneSupported = player.repeatsOne,
    )
}

/** The player to follow: a playing one (the one already followed if it plays), else [previous] while it's still there, else the first. */
fun pickMacCurrent(snapshots: List<MediaSnapshot>, previous: String?): String? {
    val playing = snapshots.filter { it.status == PlaybackStatus.Playing }.map { it.app }
    return when {
        previous in playing -> previous
        playing.isNotEmpty() -> playing.first()
        snapshots.any { it.app == previous } -> previous
        else -> snapshots.firstOrNull()?.app
    }
}

/** The AppleScript for [action] on [app] (a [MacPlayer]'s name), or null for any other app. */
fun macControlScript(app: String, action: Transport, positionMs: Long): String? {
    val player = MacPlayer.entries.firstOrNull { it.appName == app } ?: return null
    val command = when (action) {
        Transport.PLAY -> "play"
        Transport.PAUSE -> "pause"
        Transport.NEXT -> "next track"
        Transport.PREVIOUS -> "previous track"
        Transport.SEEK -> "set player position to " + String.format(Locale.ROOT, "%.3f", positionMs.coerceAtLeast(0L) / 1000.0)
    }
    return "tell application \"${player.appName}\" to $command"
}

/** The AppleScript to turn [app]'s shuffle on or off, or null for an app that isn't a [MacPlayer]. */
fun macShuffleScript(app: String, on: Boolean): String? {
    val player = MacPlayer.entries.firstOrNull { it.appName == app } ?: return null
    return "tell application \"${player.appName}\" to set ${player.shuffleProperty} to $on"
}

/**
 * The AppleScript to set [app]'s repeat to [mode], or null for an app that isn't a [MacPlayer].
 * Spotify only repeats or doesn't, so [RepeatMode.One] turns on the same repeat as [RepeatMode.All].
 */
fun macRepeatScript(app: String, mode: RepeatMode): String? {
    val player = MacPlayer.entries.firstOrNull { it.appName == app } ?: return null
    val value = when (player) {
        MacPlayer.SPOTIFY -> (mode != RepeatMode.Off).toString()
        MacPlayer.MUSIC -> mode.name.lowercase()
    }
    return "tell application \"${player.appName}\" to set ${player.repeatProperty} to $value"
}

/** A number as AppleScript printed it, in whatever decimal separator the user's locale uses. */
private fun number(text: String) = text.trim().replace(',', '.').toDoubleOrNull()

private fun seconds(text: String) = number(text)?.takeIf { it >= 0 }
