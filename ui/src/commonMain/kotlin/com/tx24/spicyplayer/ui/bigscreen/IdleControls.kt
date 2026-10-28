package com.tx24.spicyplayer.ui.bigscreen

/**
 * When the page's controls fade on their own: [autoHide] on, [delayMs] after the pointer last
 * moved (or the screen was last touched), and while paused only with [hideWhilePaused].
 */
data class IdleSettings(
    val autoHide: Boolean = true,
    val delayMs: Long = 3_000L,
    val hideWhilePaused: Boolean = false,
)

/** How long after the last move the controls fade, or null when they don't. */
fun idleAfterMs(settings: IdleSettings, playing: Boolean): Long? =
    settings.delayMs.takeIf { settings.autoHide && (playing || settings.hideWhilePaused) }

/**
 * How far the cover's controls are up in cinema and fullscreen: [Hidden]; [Page] with the
 * pointer on the page (or after a tap), dimly over a slightly darkened cover; [Cover] with it on
 * the cover itself, bright over a darker one.
 */
enum class ControlsLevel(val controlsAlpha: Float, val artworkBrightness: Float) {
    Hidden(0f, 1f),
    Page(0.65f, 0.78f),
    Cover(0.985f, 0.55f),
}

/**
 * The cover's controls for where the pointer is: on the cover they stay up however long it rests
 * there; elsewhere on the page ([onPage], or [revealed] by a tap) they fade once [idle]. While
 * [held] (the timeline is being dragged, the More menu is open) they keep the [previous] level, at
 * least [ControlsLevel.Page], wherever the pointer wanders.
 */
fun controlsLevel(
    onPage: Boolean,
    onCover: Boolean,
    revealed: Boolean,
    idle: Boolean,
    held: Boolean,
    previous: ControlsLevel = ControlsLevel.Hidden,
): ControlsLevel = when {
    held -> maxOf(previous, ControlsLevel.Page)
    onCover -> ControlsLevel.Cover
    (onPage || revealed) && !idle -> ControlsLevel.Page
    else -> ControlsLevel.Hidden
}

/** What a tap where nothing else took it does: brings hidden controls up, and puts shown ones away. */
fun revealedAfterTap(level: ControlsLevel): Boolean = level == ControlsLevel.Hidden

/**
 * The page's view controls (off cinema and fullscreen): hidden, at 0.7 while the pointer is on the
 * page, and fully up while it's on the row itself, after a tap, or while its menu is open.
 */
fun viewControlsAlpha(onPage: Boolean, onRow: Boolean, revealed: Boolean, held: Boolean): Float = when {
    onRow || revealed || held -> 1f
    onPage -> 0.7f
    else -> 0f
}
