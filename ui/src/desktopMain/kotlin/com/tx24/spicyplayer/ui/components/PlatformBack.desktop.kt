package com.tx24.spicyplayer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.tx24.spicyplayer.ui.theme.SpicyRadii
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect

/**
 * The back handlers on screen, for Esc: the window hands the key to [dispatch], which asks the
 * last one added that's enabled, as Android's back does.
 */
object DesktopBack {
    class Entry(var enabled: Boolean, var onBack: () -> Unit)

    private val entries = mutableListOf<Entry>()

    fun add(entry: Entry) { entries += entry }
    fun remove(entry: Entry) { entries -= entry }

    /** True if a handler took it. */
    fun dispatch(): Boolean {
        val entry = entries.lastOrNull { it.enabled } ?: return false
        entry.onBack()
        return true
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val entry = remember { DesktopBack.Entry(enabled, onBack) }
    SideEffect {
        entry.enabled = enabled
        entry.onBack = onBack
    }
    DisposableEffect(entry) {
        DesktopBack.add(entry)
        onDispose { DesktopBack.remove(entry) }
    }
}

@Composable
actual fun PlatformPredictiveBackHandler(enabled: Boolean, onBack: suspend (Flow<BackProgress>) -> Unit) {
    val scope = rememberCoroutineScope()
    PlatformBackHandler(enabled) { scope.launch { onBack(emptyFlow()) } }
}

/**
 * The plate's shadow as Skia's own blur: the plate shrunk by the spread, pushed down, blurred.
 * The paint is made once per size, not per frame.
 */
actual fun Modifier.modalShadow(): Modifier = drawWithCache {
    val spread = 12.dp.toPx()
    // A CSS blur length is twice the Gaussian sigma.
    val sigma = 16.dp.toPx()
    val corner = SpicyRadii.Lg.toPx()
    val dy = 24.dp.toPx()
    val paint = Paint().apply {
        color = Color.Black.copy(alpha = 0.45f).toArgb()
        maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, sigma)
    }
    val plate = RRect.makeLTRB(spread, spread + dy, size.width - spread, size.height - spread + dy, corner)
    onDrawBehind { drawIntoCanvas { canvas -> canvas.nativeCanvas.drawRRect(plate, paint) } }
}

@Composable
actual fun rootHeightPx(): Int = LocalWindowInfo.current.containerSize.height
