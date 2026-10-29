package com.tx24.spicyplayer.lyrics.spicy

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

internal actual fun monotonicNanos(): Long = System.nanoTime()

internal actual fun identityHash(any: Any): Int = System.identityHashCode(any)

internal actual fun String.codePointAtIndex(index: Int): Int = codePointAt(index)

internal actual fun String.codePointBeforeIndex(index: Int): Int = codePointBefore(index)

internal actual fun charCount(codePoint: Int): Int = Character.charCount(codePoint)

internal actual fun roundHalfUp(value: Float): Int = Math.round(value)

internal actual val resizeSnapsToLine: Boolean = true

internal actual val textLayoutContext: CoroutineContext = EmptyCoroutineContext

internal actual val resizeRelayoutDelayMs: Long = 100L

internal actual val slicesTextLayout: Boolean = true

// A frame up to a tenth of a second late still advances by its real gap.
internal actual val frameDeltaCapS: Float? = 0.1f

internal actual fun recordFrameDelta(rawS: Float, usedS: Float) = com.tx24.spicyplayer.lyrics.spicy.canvas.FrameStats.record(rawS, usedS)
