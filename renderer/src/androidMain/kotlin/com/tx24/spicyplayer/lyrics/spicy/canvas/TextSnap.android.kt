package com.tx24.spicyplayer.lyrics.spicy.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText

// Android draws moving text off the pixel grid already (`TextMotion.Animated`), so text draws as is.

internal actual fun DrawScope.drawLyricText(layout: TextLayoutResult, brush: Brush, topLeft: Offset) =
    drawText(layout, brush = brush, shadow = Shadow.None, topLeft = topLeft)

internal actual fun DrawScope.drawLyricText(layout: TextLayoutResult, color: Color, topLeft: Offset, alpha: Float) =
    drawText(layout, color = color, alpha = alpha, shadow = Shadow.None, topLeft = topLeft)

internal actual val UnsnapTextDegrees = 0f
