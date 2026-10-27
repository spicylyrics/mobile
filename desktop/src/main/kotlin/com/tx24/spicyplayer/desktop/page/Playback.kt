package com.tx24.spicyplayer.desktop.page

import com.tx24.spicyplayer.desktop.Transport

/** A button in the cover's playback row. Shuffle and repeat are left out: no platform takes them yet. */
enum class TransportButton { Previous, PlayPause, Next }

/** One playback button and whether the player can carry it out. */
data class TransportSlot(val button: TransportButton, val enabled: Boolean)

/**
 * The playback row for a source that takes [capabilities]: previous, play/pause and next, always
 * all three so the row keeps its spacing, each enabled only when the platform can do it.
 */
fun transportSlots(capabilities: Set<Transport>): List<TransportSlot> = listOf(
    TransportSlot(TransportButton.Previous, Transport.PREVIOUS in capabilities),
    TransportSlot(TransportButton.PlayPause, Transport.PLAY in capabilities && Transport.PAUSE in capabilities),
    TransportSlot(TransportButton.Next, Transport.NEXT in capabilities),
)

/** Whether the timeline takes clicks and drags. */
fun canSeek(capabilities: Set<Transport>): Boolean = Transport.SEEK in capabilities

/** [ms] as `M:SS`, minutes uncapped; negative counts as zero. */
fun formatPlaybackTime(ms: Long): String {
    if (ms < 0) return "0:00"
    val totalSeconds = ms / 1000
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}

/** Share of the song played, 0..1; 0 for an unknown length. */
fun progressOf(positionMs: Long, durationMs: Long): Float =
    if (durationMs <= 0) 0f else (positionMs.coerceIn(0, durationMs).toFloat() / durationMs)

/** The position a click [fraction] (0..1) of the way along the timeline asks for. */
fun seekTarget(fraction: Float, durationMs: Long): Long =
    if (durationMs <= 0) 0L else (fraction.coerceIn(0f, 1f) * durationMs).toLong()
