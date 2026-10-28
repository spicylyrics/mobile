package com.tx24.spicyplayer.lyrics.spicy.canvas

/** 1/1000 rad: twice Skia's axis tolerance, and under 0.15 px of tilt across a 300 px word. */
internal actual val UnsnapTextDegrees = (0.001 * 180.0 / kotlin.math.PI).toFloat()
