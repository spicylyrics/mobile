package com.tx24.spicyplayer.lyrics.spicy.canvas

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberUpdatedState

/** The refresh rate of the monitor the window is on, in Hz, which the window provides. */
val LocalDisplayRefreshRate = compositionLocalOf<Float?> { null }

@Composable
internal actual fun rememberRefreshRate(): () -> Float? {
    val rate = rememberUpdatedState(LocalDisplayRefreshRate.current)
    return { rate.value }
}
