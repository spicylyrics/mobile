package com.tx24.spicyplayer.lyrics.spicy

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
