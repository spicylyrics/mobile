package com.tx24.spicyplayer.lyrics.spicy.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextLayoutResult

/*
 * Lyric text moves by fractions of a pixel every frame (lift, scale, scroll). While the canvas is
 * only scaled and moved, Skia rounds a text run's baseline to a whole device pixel and puts
 * glyphs on a quarter-pixel grid, so moving words step instead of glide. `TextMotion.Animated`
 * avoids that on Android; on the desktop it does nothing. There the fills below draw each layout
 * once into a coverage mask and then draw the mask through the current transform with bilinear
 * filtering, so the text moves continuously.
 */

/** Draws [layout] at [topLeft] filled with [brush] (in text-local coordinates), without a shadow. */
internal expect fun DrawScope.drawLyricText(layout: TextLayoutResult, brush: Brush, topLeft: Offset)

/** Draws [layout] at [topLeft] in [color], times [alpha] unless NaN, without a shadow. */
internal expect fun DrawScope.drawLyricText(layout: TextLayoutResult, color: Color, topLeft: Offset, alpha: Float = Float.NaN)

/**
 * Degrees a text draw that can't use a mask (a shadow pass, emoji) turns by; 0 draws it as is.
 * A turn of about 1/1000 rad leaves the axes unaligned (Skia's tolerance is about 1/4096), which
 * puts the baseline on quarter pixels instead of whole ones.
 */
internal expect val UnsnapTextDegrees: Float

/** Runs [block], which draws [layout] at [topLeft], turned by [UnsnapTextDegrees] about its centre. */
internal inline fun DrawScope.unsnapped(layout: TextLayoutResult, topLeft: Offset, block: DrawScope.() -> Unit) {
    if (UnsnapTextDegrees == 0f) return block()
    rotate(UnsnapTextDegrees, Offset(topLeft.x + layout.size.width / 2f, topLeft.y + layout.size.height / 2f), block)
}
