package com.tx24.spicyplayer.lyrics.spicy

internal actual fun monotonicNanos(): Long = System.nanoTime()

internal actual fun identityHash(any: Any): Int = System.identityHashCode(any)

internal actual fun String.codePointAtIndex(index: Int): Int = codePointAt(index)

internal actual fun String.codePointBeforeIndex(index: Int): Int = codePointBefore(index)

internal actual fun charCount(codePoint: Int): Int = Character.charCount(codePoint)

internal actual fun roundHalfUp(value: Float): Int = Math.round(value)

internal actual val resizeSnapsToLine: Boolean = false
