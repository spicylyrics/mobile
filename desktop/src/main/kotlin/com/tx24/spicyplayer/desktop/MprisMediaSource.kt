package com.tx24.spicyplayer.desktop

import java.io.File
import java.net.URI
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Linux players over MPRIS, read through `playerctl --follow`: playerctl picks the player that
 * changed last and prints one line in [PLAYERCTL_FORMAT] whenever its state does. Needs playerctl
 * on the PATH; no D-Bus library.
 *
 * MPRIS gives no stamp for the position, so each snapshot counts from when its line arrived, and
 * no playback rate, so a playing player runs at 1x.
 */
class MprisMediaSource(private val playerctl: String = "playerctl", private val path: String? = System.getenv("PATH")) : MediaSource {
    override val name = "mpris"
    override val unavailableReason
        get() = if (onPath(playerctl, path)) null else "playerctl isn't installed; install it from your distribution's packages to follow MPRIS players"
    override val capabilities = Transport.entries.toSet()

    private val running = AtomicReference<Process?>()

    override fun updates(): Flow<Received> = flow {
        val process = ProcessBuilder(playerctlFollowArgs(playerctl)).redirectErrorStream(true).start()
        running.getAndSet(process)?.destroy()
        try {
            var last: String? = null
            process.inputStream.bufferedReader().useLines { lines ->
                for (raw in lines) {
                    val receivedMono = monoMs()
                    val receivedWall = System.currentTimeMillis()
                    val update = parsePlayerctlLine(raw, receivedWall, last) ?: continue
                    last = (update as? MediaUpdate.Snapshot)?.snapshot?.app
                    emit(Received(update, receivedMono, receivedWall, raw))
                }
            }
            System.err.println("playerctl exited (${process.waitFor()})")
        } finally {
            process.destroy()
            running.compareAndSet(process, null)
        }
    }.flowOn(Dispatchers.IO)

    override fun control(app: String, action: Transport, positionMs: Long) {
        ProcessBuilder(playerctlArgs(playerctl, app, action, positionMs)).redirectErrorStream(true).start()
    }

    override val canShuffle = true
    override val canRepeat = true

    override fun setShuffle(app: String, on: Boolean) {
        ProcessBuilder(playerctlShuffleArgs(playerctl, app, on)).redirectErrorStream(true).start()
    }

    override fun setRepeat(app: String, mode: RepeatMode) {
        ProcessBuilder(playerctlLoopArgs(playerctl, app, mode)).redirectErrorStream(true).start()
    }

    override fun close() {
        running.getAndSet(null)?.destroy()
    }
}

/** Separates the fields of a playerctl line; players don't put it in titles. */
private const val SEP = "\u001f"

/**
 * Player instance, status, position and length (both µs), title, artist, album, artwork URL, then
 * shuffle and loop status, empty for a player that doesn't have them.
 */
val PLAYERCTL_FORMAT = listOf(
    "{{playerInstance}}", "{{status}}", "{{position}}", "{{mpris:length}}",
    "{{xesam:title}}", "{{xesam:artist}}", "{{xesam:album}}", "{{mpris:artUrl}}",
    "{{shuffle}}", "{{loop}}",
).joinToString(SEP)

fun playerctlFollowArgs(playerctl: String = "playerctl") = listOf(playerctl, "--follow", "metadata", "--format", PLAYERCTL_FORMAT)

/** The playerctl command for [action] on the player instance [app]; a seek goes to [positionMs]. */
fun playerctlArgs(playerctl: String, app: String, action: Transport, positionMs: Long): List<String> {
    val player = listOf(playerctl, "--player=$app")
    return when (action) {
        Transport.PLAY -> player + "play"
        Transport.PAUSE -> player + "pause"
        Transport.NEXT -> player + "next"
        Transport.PREVIOUS -> player + "previous"
        Transport.SEEK -> player + listOf("position", String.format(Locale.ROOT, "%.3f", positionMs.coerceAtLeast(0L) / 1000.0))
    }
}

/** The playerctl command to turn [app]'s shuffle on or off. */
fun playerctlShuffleArgs(playerctl: String, app: String, on: Boolean) = listOf(playerctl, "--player=$app", "shuffle", if (on) "On" else "Off")

/** The playerctl command to set [app]'s loop status. */
fun playerctlLoopArgs(playerctl: String, app: String, mode: RepeatMode) = listOf(
    playerctl, "--player=$app", "loop",
    when (mode) {
        RepeatMode.Off -> "None"
        RepeatMode.One -> "Track"
        RepeatMode.All -> "Playlist"
    },
)

/**
 * One line of `playerctl --follow` in [PLAYERCTL_FORMAT], received at [receivedWall] (epoch ms), or
 * null for one that isn't in the format. playerctl prints a blank line (or "No players found") when
 * the last player quits: that removes [lastApp], the player of the line before.
 */
fun parsePlayerctlLine(raw: String, receivedWall: Long, lastApp: String? = null): MediaUpdate? {
    val line = raw.trimEnd('\r', '\n')
    if (line.isBlank() || line.trim() == "No players found") return lastApp?.let { MediaUpdate.Removed(it) }
    val fields = line.split(SEP)
    // Eight fields is the format before shuffle and loop.
    if (fields.size != 8 && fields.size != 10) return null
    val (app, status, position, length, title) = fields
    if (app.isBlank()) return null
    val artUrl = fields[7].takeIf(String::isNotBlank)
    return MediaUpdate.Snapshot(
        MediaSnapshot(
            app = app,
            // playerctl only follows the player that changed last.
            current = true,
            title = title,
            artist = fields[5],
            album = fields[6],
            durationMs = (length.trim().toLongOrNull() ?: 0L) / 1000,
            positionMs = (position.trim().toLongOrNull() ?: 0L) / 1000,
            updatedAtWallMs = receivedWall,
            status = when (status.trim()) {
                "Playing" -> PlaybackStatus.Playing
                "Paused" -> PlaybackStatus.Paused
                "Stopped" -> PlaybackStatus.Stopped
                else -> PlaybackStatus.Unknown
            },
            rate = null,
            cover = artUrl?.takeIf { it.startsWith("file:") }?.let { runCatching { File(URI(it)) }.getOrNull() },
            artUrl = artUrl,
            event = "follow",
            shuffle = when (fields.getOrNull(8)?.trim()?.lowercase()) {
                "true", "on" -> true
                "false", "off" -> false
                else -> null
            },
            repeat = when (fields.getOrNull(9)?.trim()) {
                "None" -> RepeatMode.Off
                "Track" -> RepeatMode.One
                "Playlist" -> RepeatMode.All
                else -> null
            },
        ),
    )
}

/** Whether [command] is an executable file in one of [path]'s directories. */
fun onPath(command: String, path: String?): Boolean =
    path.orEmpty().split(File.pathSeparatorChar).filter(String::isNotBlank).any { File(it, command).let { f -> f.isFile && f.canExecute() } }
