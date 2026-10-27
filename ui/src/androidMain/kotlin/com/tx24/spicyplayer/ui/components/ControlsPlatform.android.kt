package com.tx24.spicyplayer.ui.components

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView
import java.text.Normalizer

internal actual fun uptimeMillis(): Long = SystemClock.uptimeMillis()

internal actual fun decomposeAccents(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)

@Composable
internal actual fun windowHeightPx(): Int = LocalView.current.rootView.height
