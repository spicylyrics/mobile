package com.tx24.spicyplayer.lyrics.spicy

import kotlin.coroutines.CoroutineContext

/** A monotonic clock in nanoseconds, for timing and nothing else. */
internal expect fun monotonicNanos(): Long

/** The identity hash of [any], whatever its own hashCode says. */
internal expect fun identityHash(any: Any): Int

/** The Unicode code point at [index], a surrogate pair read as one. */
internal expect fun String.codePointAtIndex(index: Int): Int

/** The Unicode code point just before [index], a surrogate pair read as one. */
internal expect fun String.codePointBeforeIndex(index: Int): Int

/** How many UTF-16 chars [codePoint] takes: 2 above the BMP, else 1. */
internal expect fun charCount(codePoint: Int): Int

/** [value] rounded to the nearest Int, halves up; NaN gives 0. */
internal expect fun roundHalfUp(value: Float): Int

/**
 * Whether the lyrics, resized, put the line being sung back in view even when scrolled away by
 * hand, as a resized window does. A phone's lyrics change height as its own chrome comes and
 * goes, which shouldn't pull a hand-scrolled view back.
 */
internal expect val resizeSnapsToLine: Boolean

/**
 * Where lyrics get measured. Skia's text layout isn't thread-safe: measuring off the UI thread
 * while it draws text can corrupt a shared cache and hang the UI thread for good, so desktop
 * measures in place. Android's text layout is safe on a background thread.
 */
internal expect val textLayoutContext: CoroutineContext

/**
 * How long a new width has to hold before the lyrics are laid out again for it, while lyrics
 * laid out for the old one are on screen. Desktop measures on the UI thread, so a window dragged
 * wider by a pixel a frame would otherwise measure every line every frame; the old layout stays
 * up until the drag settles. 0 measures at once.
 */
internal expect val resizeRelayoutDelayMs: Long
