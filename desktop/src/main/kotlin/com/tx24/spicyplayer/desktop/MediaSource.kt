package com.tx24.spicyplayer.desktop

import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The operating system's view of what other apps are playing: a stream of now-playing snapshots,
 * and transport commands for the platforms that take them. One implementation per OS, picked by
 * [mediaSource]; [MediaSession] turns the snapshots into a lyric clock the same way on all of them.
 */
interface MediaSource : AutoCloseable {
    /** A short name for logs, e.g. "gsmtc". */
    val name: String

    /** Why this source can't run here (a missing helper or tool, an unsupported OS), or null when it can. */
    val unavailableReason: String?

    /** The commands [control] carries out on this platform. */
    val capabilities: Set<Transport>

    /**
     * Every snapshot and removal the platform reports, as it arrives. Collecting starts the
     * platform's reader and the flow ends when the reader does; [close] stops it from any thread.
     */
    fun updates(): Flow<Received>

    /** Asks [app]'s player to carry out [action] (to [positionMs] for a seek); ignored outside [capabilities]. */
    fun control(app: String, action: Transport, positionMs: Long = 0L)

    /** Stops the reader, so a collection of [updates] ends. */
    override fun close() {}
}

enum class Transport { PLAY, PAUSE, NEXT, PREVIOUS, SEEK }

enum class PlaybackStatus { Closed, Opened, Changing, Stopped, Playing, Paused, Unknown }

/**
 * One player's state as the platform reported it.
 *
 * [updatedAtWallMs] is when the player says [positionMs] was true (epoch ms); a platform that gives
 * no such stamp uses the time the report arrived. [current] is whether the platform counts this
 * player as the one in focus. [cover] is an image file on disk; [artUrl] is the player's own
 * artwork address when it isn't one (a remote URL the app doesn't fetch yet).
 */
data class MediaSnapshot(
    val app: String,
    val current: Boolean,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val positionMs: Long,
    val updatedAtWallMs: Long,
    val status: PlaybackStatus,
    val rate: Float?,
    val cover: File? = null,
    val artUrl: String? = null,
    /** What made the platform report, e.g. "poll" or "timeline"; for logs. */
    val event: String = "",
)

sealed interface MediaUpdate {
    data class Snapshot(val snapshot: MediaSnapshot) : MediaUpdate

    /** [app]'s player went away. */
    data class Removed(val app: String) : MediaUpdate
}

/** An update, stamped with when it arrived on the monotonic and epoch clocks, and the line it came from. */
data class Received(val update: MediaUpdate, val receivedMono: Long, val receivedWall: Long, val raw: String)

/** A source for a platform with no reader: it reports nothing and takes no commands. */
class UnsupportedMediaSource(override val name: String, override val unavailableReason: String) : MediaSource {
    override val capabilities = emptySet<Transport>()
    override fun updates(): Flow<Received> = emptyFlow()
    override fun control(app: String, action: Transport, positionMs: Long) {}
}

enum class Platform { WINDOWS, LINUX, MAC, OTHER }

/** The platform an `os.name` value names. */
fun platformOf(osName: String): Platform {
    val os = osName.lowercase()
    return when {
        os.startsWith("windows") -> Platform.WINDOWS
        os.startsWith("mac") || os.startsWith("darwin") -> Platform.MAC
        os.startsWith("linux") || "bsd" in os -> Platform.LINUX
        else -> Platform.OTHER
    }
}

/** The media source for this OS; [helper] overrides where the Windows helper is looked for. */
fun mediaSource(helper: File? = null, osName: String = System.getProperty("os.name").orEmpty()): MediaSource =
    when (platformOf(osName)) {
        Platform.WINDOWS -> GsmtcMediaSource(helper ?: defaultHelper())
        Platform.LINUX -> MprisMediaSource()
        Platform.MAC -> macMediaSource()
        Platform.OTHER -> UnsupportedMediaSource("none", "No media session support for $osName yet")
    }

fun monoMs() = System.nanoTime() / 1_000_000

/** How long to wait before starting a source again after its [failures]th failure in a row: 1 s, doubling, at most 30 s. */
fun retryDelayMs(failures: Int): Long = (1000L shl (failures - 1).coerceIn(0, 5)).coerceAtMost(30_000L)
