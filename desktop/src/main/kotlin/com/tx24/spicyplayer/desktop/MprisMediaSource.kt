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
            process.inputStream.bufferedReader().useLines { lines ->
                for (raw in lines) {
                    val receivedMono = monoMs()
                    val receivedWall = System.currentTimeMillis()
                    val update = parsePlayerctlLine(raw, receivedWall) ?: continue
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

    override fun close() {
        running.getAndSet(null)?.destroy()
    }
}

/** Separates the fields of a playerctl line; players don't put it in titles. */
private const val SEP = "\u001f"

/** Player instance, status, position and length (both µs), title, artist, album, artwork URL. */
val PLAYERCTL_FORMAT = listOf(
    "{{playerInstance}}", "{{status}}", "{{position}}", "{{mpris:length}}",
    "{{xesam:title}}", "{{xesam:artist}}", "{{xesam:album}}", "{{mpris:artUrl}}",
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

/**
 * One line of `playerctl --follow` in [PLAYERCTL_FORMAT], received at [receivedWall] (epoch ms), or
 * null for a blank line (no player left) or one that isn't in the format.
 */
fun parsePlayerctlLine(raw: String, receivedWall: Long): MediaUpdate? {
    val fields = raw.trimEnd('\r', '\n').split(SEP)
    if (fields.size != 8) return null
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
        ),
    )
}

/** Whether [command] is an executable file in one of [path]'s directories. */
fun onPath(command: String, path: String?): Boolean =
    path.orEmpty().split(File.pathSeparatorChar).filter(String::isNotBlank).any { File(it, command).let { f -> f.isFile && f.canExecute() } }
