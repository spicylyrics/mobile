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
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface

/** 1/1000 rad: twice Skia's axis tolerance, and under 0.15 px of tilt across a 300 px word. */
internal actual val UnsnapTextDegrees = (0.001 * 180.0 / kotlin.math.PI).toFloat()

internal actual fun DrawScope.drawLyricText(layout: TextLayoutResult, brush: Brush, topLeft: Offset) {
    if (!drawMask(layout, topLeft) { brush.applyTo(Size(layout.size.width.toFloat(), layout.size.height.toFloat()), it, 1f) }) {
        drawText(layout, brush = brush, topLeft = topLeft, shadow = Shadow.None)
    }
}

internal actual fun DrawScope.drawLyricText(layout: TextLayoutResult, color: Color, topLeft: Offset, alpha: Float) {
    if (!drawMask(layout, topLeft) { it.color = if (alpha.isNaN()) color else color.copy(alpha = color.alpha * alpha) }) {
        drawText(layout, color = color, topLeft = topLeft, alpha = if (alpha.isNaN()) 1f else alpha, shadow = Shadow.None)
    }
}

/**
 * Desktop switches for comparing ways of drawing, set as JVM system properties (in the installed
 * app, a `java-options=-D...` line in its launcher's `.cfg`):
 * - `spicy.textBlur`: `gpu` (default) blurs a glyph mask with an image filter on the GPU; `cpu`
 *   draws Compose's text-shadow, which Skia blurs on the CPU; `off` draws blurred text sharp.
 * - `spicy.textMasks=off`: words drawn as plain text, without glyph masks.
 */
internal object DesktopDrawFlags {
    val textBlur: String = System.getProperty("spicy.textBlur") ?: "gpu"
    val textMasks: Boolean = System.getProperty("spicy.textMasks") != "off"
}

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

/**
 * How long a frame may spend making new masks. Fresh layouts (a resize, a new text size) need one
 * per word on screen at once; past this, the rest are drawn as plain text for a frame or two and
 * get their masks over the next frames, rather than holding one frame for all of them.
 */
private const val MASK_BUDGET_NS = 6_000_000L
private const val FRAME_NS = 16_000_000L
private var budgetSince = 0L
private var budgetUsed = 0L

/** [layout]'s mask, made now if this frame's budget allows; null when it has to wait. */
private fun maskOf(layout: TextLayoutResult): Mask? {
    if (!DesktopDrawFlags.textMasks) return null
    val key = LayoutKey(layout)
    masks[key]?.let { return it }
    val now = System.nanoTime()
    if (now - budgetSince > FRAME_NS) {
        budgetSince = now
        budgetUsed = 0L
    }
    if (budgetUsed >= MASK_BUDGET_NS) return null
    return makeMask(layout).also {
        masks[key] = it
        budgetUsed += System.nanoTime() - now
    }
}

private fun makeMask(layout: TextLayoutResult): Mask {
    val pad = ceil(layout.size.height / 2f).toInt()
    val width = layout.size.width + 2 * pad
    val height = layout.size.height + 2 * pad
    val surface = Surface.makeRaster(ImageInfo.makeA8(width, height))
    CanvasDrawScope().draw(layout.layoutInput.density, layout.layoutInput.layoutDirection,
        surface.canvas.asComposeCanvas(), Size(width.toFloat(), height.toFloat())) {
        drawText(layout, color = Color.White, shadow = Shadow.None, topLeft = Offset(pad.toFloat(), pad.toFloat()))
    }
    return Mask(surface.makeImageSnapshot(), pad).also { surface.close() }
}

/**
 * Draws [layout]'s mask at [topLeft], coloured by the paint [setUp] gives it (an alpha-only image
 * takes the paint's colour or shader). False when its mask isn't made yet, for the caller to draw
 * the text as it is this frame.
 */
private inline fun DrawScope.drawMask(layout: TextLayoutResult, topLeft: Offset, setUp: (Paint) -> Unit): Boolean {
    if (layout.size.width <= 0 || layout.size.height <= 0) return true
    val mask = maskOf(layout) ?: return false
    val paint = maskPaint
    paint.shader = null
    paint.skiaPaint.shader = null
    paint.skiaPaint.imageFilter = null
    paint.color = Color.White
    paint.alpha = 1f
    setUp(paint)
    // The image sits at the canvas origin and the text a pad in from it; the brush is in text coordinates.
    val pad = mask.pad.toFloat()
    val skiaPaint = paint.skiaPaint
    skiaPaint.shader = skiaPaint.shader?.makeWithLocalMatrix(Matrix33.makeTranslate(pad, pad))
    drawIntoCanvas { canvas ->
        val skia = canvas.skiaCanvas
        val w = mask.image.width.toFloat()
        val h = mask.image.height.toFloat()
        skia.save()
        skia.translate(topLeft.x - pad, topLeft.y - pad)
        skia.drawImageRect(mask.image, Rect.makeWH(w, h), Rect.makeWH(w, h), SamplingMode.LINEAR, skiaPaint, true)
        skia.restore()
    }
    return true
}

/**
 * The text-shadow as the glyph mask blurred by an image filter, which the GPU does. Compose's own
 * text-shadow is a blur mask filter that Skia works out on the CPU for every glyph, at every new
 * text size and every new blur radius, inside the frame's flush: after a resize, seconds of it.
 * The radius is the shadow's blur sigma, as Compose hands it to Skia, so the look is the same.
 */
internal actual fun DrawScope.drawLyricShadow(layout: TextLayoutResult, shadow: Shadow, topLeft: Offset): Boolean {
    if (DesktopDrawFlags.textBlur == "cpu") return false
    val sigma = if (DesktopDrawFlags.textBlur == "off") 0f else shadow.blurRadius
    val at = topLeft + shadow.offset
    val drawn = drawMask(layout, at) { paint ->
        paint.color = shadow.color
        if (sigma > 0f) paint.skiaPaint.imageFilter = blurFilter(sigma)
    }
    // Its mask waits for a later frame's budget: sharp for now, never the CPU blur.
    if (!drawn) drawText(layout, color = shadow.color, topLeft = at, shadow = Shadow.None)
    return true
}

// Blur filters by sigma, rounded to a quarter: radii animate, and each new filter is a native object.
private val blurFilters = object : LinkedHashMap<Int, ImageFilter>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ImageFilter>) = size > 64
}

private fun blurFilter(sigma: Float): ImageFilter {
    val quarters = (sigma * 4f).toInt().coerceAtLeast(1)
    return blurFilters.getOrPut(quarters) { ImageFilter.makeBlur(quarters / 4f, quarters / 4f, FilterTileMode.DECAL) }
}
