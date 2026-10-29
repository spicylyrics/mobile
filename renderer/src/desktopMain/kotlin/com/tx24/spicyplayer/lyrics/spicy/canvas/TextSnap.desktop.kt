package com.tx24.spicyplayer.lyrics.spicy.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.ShaderBrush
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
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface

/** 1/1000 rad: twice Skia's axis tolerance, and under 0.15 px of tilt across a 300 px word. */
internal actual val UnsnapTextDegrees = (0.001 * 180.0 / kotlin.math.PI).toFloat()

internal actual fun DrawScope.drawLyricText(layout: TextLayoutResult, brush: Brush, topLeft: Offset) {
    val size = Size(layout.size.width.toFloat(), layout.size.height.toFloat())
    // The wipe's brush is new every frame. A shader of our own, closed once drawn, frees its
    // native memory now instead of whenever a GC gets round to the brush's.
    val shader = (brush as? ShaderBrush)?.createShader(size)
    val drawn = drawMask(layout, topLeft) { if (shader != null) it.shader = shader else brush.applyTo(size, it, 1f) }
    shader?.close()
    if (!drawn) {
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
 * - `spicy.maskStats=<file>`: every 2 s, writes how the mask cache is doing to that file.
 */
internal object DesktopDrawFlags {
    val textBlur: String = System.getProperty("spicy.textBlur") ?: "gpu"
    val textMasks: Boolean = System.getProperty("spicy.textMasks") != "off"
    val maskStats: String? = System.getProperty("spicy.maskStats")
}

/** A layout's glyph coverage, [pad] px of room on every side for glyphs that reach past its box. */
private class Mask(val image: Image, val pad: Int) {
    val bytes: Long get() = image.width.toLong() * image.height
}

/** Keyed by the layout itself: a re-measured layout is a new one and gets a new mask. */
private class LayoutKey(val layout: TextLayoutResult) {
    override fun equals(other: Any?) = other is LayoutKey && other.layout === layout
    override fun hashCode() = System.identityHashCode(layout)
}

/**
 * Masks of the layouts drawn most recently, up to [MASK_BYTES] of pixels. Drawing happens on the
 * UI thread only. A budget in bytes rather than a count of masks: one frame can draw well over a
 * few hundred layouts (a translation and a romanization under each line, held words split into
 * letters), and a cache smaller than one frame's worth makes, uploads and drops every mask on
 * every frame.
 */
private const val MASK_BYTES = 32L * 1024 * 1024
private var maskBytes = 0L
private val masks = object : LinkedHashMap<LayoutKey, Mask>(256, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LayoutKey, Mask>): Boolean {
        if (maskBytes <= MASK_BYTES) return false
        maskBytes -= eldest.value.bytes
        MaskStats.evicted++
        // A picture recorded this frame holds its own reference to the image.
        eldest.value.image.close()
        return true
    }
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
    if (budgetUsed >= MASK_BUDGET_NS) {
        MaskStats.deferred++
        return null
    }
    return makeMask(layout).also {
        maskBytes += it.bytes
        MaskStats.created++
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
    // The canvas origin is the text's, so the brush (in text coordinates) needs no shifting, and
    // the image starts a pad up and left of it.
    val pad = mask.pad.toFloat()
    drawIntoCanvas { canvas ->
        val skia = canvas.skiaCanvas
        val w = mask.image.width.toFloat()
        val h = mask.image.height.toFloat()
        skia.save()
        skia.translate(topLeft.x, topLeft.y)
        // Not strict: the mask's transparent border already keeps the filter inside it.
        skia.drawImageRect(mask.image, Rect.makeWH(w, h), Rect.makeXYWH(-pad, -pad, w, h), SamplingMode.LINEAR, paint.skiaPaint, false)
        MaskStats.drawn++
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
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ImageFilter>): Boolean {
        if (size <= 64) return false
        eldest.value.close()
        return true
    }
}

private fun blurFilter(sigma: Float): ImageFilter {
    val quarters = (sigma * 4f).toInt().coerceAtLeast(1)
    return blurFilters.getOrPut(quarters) { ImageFilter.makeBlur(quarters / 4f, quarters / 4f, FilterTileMode.DECAL) }
}

/** Counts for `spicy.maskStats`: what the mask cache did since the last report. Written on the UI thread. */
private object MaskStats {
    @Volatile var created = 0L
    @Volatile var evicted = 0L
    @Volatile var deferred = 0L
    @Volatile var drawn = 0L

    init {
        DesktopDrawFlags.maskStats?.let { path ->
            Thread({
                val file = java.io.File(path)
                while (true) {
                    Thread.sleep(2_000)
                    val line = "${java.time.LocalTime.now()} drawn=$drawn created=$created evicted=$evicted deferred=$deferred " +
                        "cached=${masks.size} bytes=${maskBytes / 1024}KB"
                    drawn = 0; created = 0; evicted = 0; deferred = 0
                    runCatching { file.appendText(line + System.lineSeparator()) }
                }
            }, "spicy-mask-stats").apply { isDaemon = true }.start()
        }
    }
}
