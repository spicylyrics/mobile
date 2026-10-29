package com.tx24.spicyplayer.lyrics.spicy

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers

internal actual fun monotonicNanos(): Long = System.nanoTime()

internal actual fun identityHash(any: Any): Int = System.identityHashCode(any)

internal actual fun String.codePointAtIndex(index: Int): Int = codePointAt(index)

internal actual fun String.codePointBeforeIndex(index: Int): Int = codePointBefore(index)

internal actual fun charCount(codePoint: Int): Int = Character.charCount(codePoint)

internal actual fun roundHalfUp(value: Float): Int = Math.round(value)

internal actual val resizeSnapsToLine: Boolean = false

internal actual val textLayoutContext: CoroutineContext = Dispatchers.Default

internal actual val resizeRelayoutDelayMs: Long = 0L

internal actual val slicesTextLayout: Boolean = false

internal actual val frameDeltaCapS: Float? = null

internal actual fun recordFrameDelta(rawS: Float, usedS: Float) {}
