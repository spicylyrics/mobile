package com.tx24.spicyplayer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo
import java.text.Normalizer

internal actual fun uptimeMillis(): Long = System.nanoTime() / 1_000_000L

internal actual fun decomposeAccents(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)

@Composable
internal actual fun windowHeightPx(): Int = LocalWindowInfo.current.containerSize.height
