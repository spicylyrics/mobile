package com.tx24.spicyplayer.lyrics.spicy.canvas

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

@Composable
internal actual fun rememberRefreshRate(): () -> Float? {
    val view = LocalView.current
    return remember(view) { { view.display?.refreshRate } }
}
