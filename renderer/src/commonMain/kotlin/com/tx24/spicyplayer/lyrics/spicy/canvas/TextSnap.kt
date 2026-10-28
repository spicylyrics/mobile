package com.tx24.spicyplayer.lyrics.spicy.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextLayoutResult

/**
 * Degrees each lyric text draw turns by so it can sit between pixel rows; 0 draws it as is.
 *
 * While the canvas is only scaled and moved, Skia rounds a text run's baseline to a whole device
 * pixel, so a word that lifts, scales or scrolls by a fraction of a pixel per frame jumps a row
 * at a time and looks shaky. `TextMotion.Animated` turns that off on Android; on the desktop it
 * does nothing. A turn of about 1/1000 rad leaves the axes unaligned (Skia's tolerance is about
 * 1/4096), and then glyphs land on quarter pixels both ways.
 */
internal expect val UnsnapTextDegrees: Float

/** Runs [block], which draws [layout] at [topLeft], turned by [UnsnapTextDegrees] about its centre. */
internal inline fun DrawScope.unsnapped(layout: TextLayoutResult, topLeft: Offset, block: DrawScope.() -> Unit) {
    if (UnsnapTextDegrees == 0f) return block()
    rotate(UnsnapTextDegrees, Offset(topLeft.x + layout.size.width / 2f, topLeft.y + layout.size.height / 2f), block)
}
