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

internal actual val resizeRelayoutDelayMs: Long = 150L
