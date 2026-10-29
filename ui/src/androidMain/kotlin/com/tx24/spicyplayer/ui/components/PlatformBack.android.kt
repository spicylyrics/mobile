package com.tx24.spicyplayer.ui.components

import android.os.Build
import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.tx24.spicyplayer.ui.theme.SpicyRadii
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)

@Composable
actual fun PlatformPredictiveBackHandler(enabled: Boolean, onBack: suspend (Flow<BackProgress>) -> Unit) =
    PredictiveBackHandler(enabled) { progress ->
        onBack(progress.map { BackProgress(it.progress, it.swipeEdge == BackEventCompat.EDGE_LEFT) })
    }

/**
 * `0 24px 64px -12px rgba(0,0,0,.45)` as an Android shadow layer: the plate shrunk by the spread,
 * pushed down, blurred. Hardware canvases only draw shadow layers for shapes from API 28.
 */
actual fun Modifier.modalShadow(): Modifier = drawBehind {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return@drawBehind
    val spread = 12.dp.toPx()
    // A CSS blur length is twice the Gaussian sigma; Skia's sigma = radius * 0.57735 + 0.5.
    val radius = ((32.dp.toPx() - 0.5f) / 0.57735f).coerceAtLeast(0.1f)
    val corner = SpicyRadii.Lg.toPx()
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.TRANSPARENT
            setShadowLayer(radius, 0f, 24.dp.toPx(), Color.Black.copy(alpha = 0.45f).toArgb())
        }
        canvas.nativeCanvas.drawRoundRect(spread, spread, size.width - spread, size.height - spread, corner, corner, paint)
    }
}

@Composable
actual fun rootHeightPx(): Int = LocalView.current.rootView.height
