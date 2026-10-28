package com.tx24.spicyplayer.ui.bigscreen

import androidx.compose.ui.input.key.Key
import com.tx24.spicyplayer.ui.nowplaying.NowPlayingState
import com.tx24.spicyplayer.ui.nowplaying.RepeatState

/**
 * What the platform can do for the big-screen page: which of [com.tx24.spicyplayer.ui.nowplaying.PlaybackTransport]'s
 * commands reach the player, and whether the window can go fullscreen (a desktop window can; a
 * tablet's app already fills the screen, so cinema is as far as it goes). [shuffle] and [repeat]
 * are off unless the platform can set them.
 */
data class PlayerCapabilities(
    val playPause: Boolean = true,
    val skipNext: Boolean = true,
    val skipPrevious: Boolean = true,
    val seek: Boolean = true,
    val fullscreen: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: Boolean = false,
    /** A Lyrics Manager to open from the page, and its button turned on (`lyricsManagerButton`). */
    val lyricsManager: Boolean = false,
)

/** A button in the cover's playback row; shuffle and repeat sit at its ends ([playbackToggles]). */
enum class TransportButton { Previous, PlayPause, Next }

/** One playback button and whether the player can carry it out. */
data class TransportSlot(val button: TransportButton, val enabled: Boolean)

/**
 * The playback row for [capabilities]: previous, play/pause and next, always all three so the row
 * keeps its spacing, each enabled only when the platform can do it.
 */
fun transportSlots(capabilities: PlayerCapabilities): List<TransportSlot> = listOf(
    TransportSlot(TransportButton.Previous, capabilities.skipPrevious),
    TransportSlot(TransportButton.PlayPause, capabilities.playPause),
    TransportSlot(TransportButton.Next, capabilities.skipNext),
)

/**
 * The row's toggles: shuffle on the left and repeat on the right, each null (not shown) unless the
 * platform can set it and the player says where it stands.
 */
data class PlaybackToggles(val shuffle: Boolean?, val repeat: RepeatState?)

fun playbackToggles(capabilities: PlayerCapabilities, playing: NowPlayingState): PlaybackToggles = PlaybackToggles(
    shuffle = playing.shuffle.takeIf { capabilities.shuffle },
    repeat = playing.repeat.takeIf { capabilities.repeat },
)

/**
 * What a press on repeat asks for next: off, then the whole queue, then the one song where the
 * player can ([oneSupported]), then off. Without it, all goes straight back to off, or the button
 * would ask for one and read back all forever.
 */
fun nextRepeat(current: RepeatState, oneSupported: Boolean = true): RepeatState = when (current) {
    RepeatState.Off -> RepeatState.All
    RepeatState.All -> if (oneSupported) RepeatState.One else RepeatState.Off
    RepeatState.One -> RepeatState.Off
}

/** Share of the song played, 0..1; 0 for an unknown length. */
fun progressOf(positionMs: Long, durationMs: Long): Float =
    if (durationMs <= 0) 0f else (positionMs.coerceIn(0, durationMs).toFloat() / durationMs)

/** The position a click [fraction] (0..1) of the way along the timeline asks for. */
fun seekTarget(fraction: Float, durationMs: Long): Long =
    if (durationMs <= 0) 0L else (fraction.coerceIn(0f, 1f) * durationMs).toLong()

/** The page's playback keys: Space, ←/→ (seek), Shift+←/→ (previous/next song). */
enum class PlaybackKey { PlayPause, SeekBack, SeekForward, Previous, Next }

/**
 * [key] as a playback key, with Shift held or not; null for anything else, and for any key with
 * Ctrl, Alt or Meta held ([otherModifier]), which belong to the app's own shortcuts.
 */
fun playbackKeyOf(key: Key, shift: Boolean, otherModifier: Boolean): PlaybackKey? {
    if (otherModifier) return null
    return when (key) {
        Key.Spacebar -> PlaybackKey.PlayPause.takeIf { !shift }
        Key.DirectionLeft -> if (shift) PlaybackKey.Previous else PlaybackKey.SeekBack
        Key.DirectionRight -> if (shift) PlaybackKey.Next else PlaybackKey.SeekForward
        else -> null
    }
}

/** What a playback key asks the player for. */
sealed interface PlaybackCommand {
    data object PlayPause : PlaybackCommand
    data object Previous : PlaybackCommand
    data object Next : PlaybackCommand
    data class Seek(val positionMs: Long) : PlaybackCommand
}

/** How far ←/→ move. */
const val KEY_SEEK_MS = 5_000L

/**
 * The command [key] asks for at [positionMs] of a song [durationMs] long (0: unknown), or null
 * where the platform or the player ([canSeek]) can't do it. A seek stays inside the song.
 */
fun playbackCommand(key: PlaybackKey, positionMs: Long, durationMs: Long, capabilities: PlayerCapabilities, canSeek: Boolean): PlaybackCommand? =
    when (key) {
        PlaybackKey.PlayPause -> PlaybackCommand.PlayPause.takeIf { capabilities.playPause }
        PlaybackKey.Previous -> PlaybackCommand.Previous.takeIf { capabilities.skipPrevious }
        PlaybackKey.Next -> PlaybackCommand.Next.takeIf { capabilities.skipNext }
        PlaybackKey.SeekBack, PlaybackKey.SeekForward -> {
            if (!capabilities.seek || !canSeek) {
                null
            } else {
                val step = if (key == PlaybackKey.SeekBack) -KEY_SEEK_MS else KEY_SEEK_MS
                val target = (positionMs + step).coerceAtLeast(0L)
                PlaybackCommand.Seek(if (durationMs > 0) target.coerceAtMost(durationMs) else target)
            }
        }
    }
