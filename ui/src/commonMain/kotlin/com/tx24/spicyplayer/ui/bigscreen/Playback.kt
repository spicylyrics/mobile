package com.tx24.spicyplayer.ui.bigscreen

/**
 * What the platform can do for the big-screen page: which of [com.tx24.spicyplayer.ui.nowplaying.PlaybackTransport]'s
 * commands reach the player, and whether the window can go fullscreen (a desktop window can; a
 * tablet's app already fills the screen, so cinema is as far as it goes).
 */
data class PlayerCapabilities(
    val playPause: Boolean = true,
    val skipNext: Boolean = true,
    val skipPrevious: Boolean = true,
    val seek: Boolean = true,
    val fullscreen: Boolean = false,
)

/** A button in the cover's playback row. Shuffle and repeat are left out for now. */
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

/** Share of the song played, 0..1; 0 for an unknown length. */
fun progressOf(positionMs: Long, durationMs: Long): Float =
    if (durationMs <= 0) 0f else (positionMs.coerceIn(0, durationMs).toFloat() / durationMs)

/** The position a click [fraction] (0..1) of the way along the timeline asks for. */
fun seekTarget(fraction: Float, durationMs: Long): Long =
    if (durationMs <= 0) 0L else (fraction.coerceIn(0f, 1f) * durationMs).toLong()
