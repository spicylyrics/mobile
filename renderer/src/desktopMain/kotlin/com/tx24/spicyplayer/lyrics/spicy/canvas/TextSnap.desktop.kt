package com.tx24.spicyplayer.lyrics.spicy.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.graphics.skiaPaint
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import kotlin.math.ceil
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface

/** 1/1000 rad: twice Skia's axis tolerance, and under 0.15 px of tilt across a 300 px word. */
internal actual val UnsnapTextDegrees = (0.001 * 180.0 / kotlin.math.PI).toFloat()

internal actual fun DrawScope.drawLyricText(layout: TextLayoutResult, brush: Brush, topLeft: Offset) =
    drawMask(layout, topLeft) { brush.applyTo(Size(layout.size.width.toFloat(), layout.size.height.toFloat()), it, 1f) }

internal actual fun DrawScope.drawLyricText(layout: TextLayoutResult, color: Color, topLeft: Offset, alpha: Float) =
    drawMask(layout, topLeft) { it.color = if (alpha.isNaN()) color else color.copy(alpha = color.alpha * alpha) }

/** A layout's glyph coverage, [pad] px of room on every side for glyphs that reach past its box. */
private class Mask(val image: Image, val pad: Int)

/** Keyed by the layout itself: a re-measured layout is a new one and gets a new mask. */
private class LayoutKey(val layout: TextLayoutResult) {
    override fun equals(other: Any?) = other is LayoutKey && other.layout === layout
    override fun hashCode() = System.identityHashCode(layout)
}

// Masks of the layouts drawn most recently. Drawing happens on the UI thread only.
private const val MAX_MASKS = 400
private val masks = object : LinkedHashMap<LayoutKey, Mask>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LayoutKey, Mask>) = size > MAX_MASKS
}
private val maskPaint = Paint()

private fun maskOf(layout: TextLayoutResult): Mask = masks.getOrPut(LayoutKey(layout)) {
    val pad = ceil(layout.size.height / 2f).toInt()
    val width = layout.size.width + 2 * pad
    val height = layout.size.height + 2 * pad
    val surface = Surface.makeRaster(ImageInfo.makeA8(width, height))
    CanvasDrawScope().draw(layout.layoutInput.density, layout.layoutInput.layoutDirection,
        surface.canvas.asComposeCanvas(), Size(width.toFloat(), height.toFloat())) {
        drawText(layout, color = Color.White, shadow = Shadow.None, topLeft = Offset(pad.toFloat(), pad.toFloat()))
    }
    Mask(surface.makeImageSnapshot(), pad).also { surface.close() }
}

/** Draws [layout]'s mask at [topLeft], coloured by the paint [setUp] gives it (an alpha-only image takes the paint's colour or shader). */
private inline fun DrawScope.drawMask(layout: TextLayoutResult, topLeft: Offset, setUp: (Paint) -> Unit) {
    if (layout.size.width <= 0 || layout.size.height <= 0) return
    val mask = maskOf(layout)
    val paint = maskPaint
    paint.shader = null
    paint.color = Color.White
    paint.alpha = 1f
    setUp(paint)
    drawIntoCanvas { canvas ->
        val skia = canvas.skiaCanvas
        val skiaPaint = paint.skiaPaint
        val w = mask.image.width.toFloat()
        val h = mask.image.height.toFloat()
        val pad = mask.pad.toFloat()
        skia.save()
        skia.translate(topLeft.x, topLeft.y)
        skia.drawImageRect(mask.image, Rect.makeWH(w, h), Rect.makeXYWH(-pad, -pad, w, h), SamplingMode.LINEAR, skiaPaint, true)
        skia.restore()
    }
}
