package com.tx24.spicyplayer.lyrics.spicy.canvas

import androidx.compose.runtime.Composable

/** Reads the refresh rate of the display the lyrics are on, in Hz; null if unknown. */
@Composable
internal expect fun rememberRefreshRate(): () -> Float?
