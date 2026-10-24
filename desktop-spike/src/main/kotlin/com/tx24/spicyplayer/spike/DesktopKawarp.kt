package com.tx24.spicyplayer.spike

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeShader
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.tx24.spicyplayer.lyrics.spicy.canvas.kawarp.KawarpBlurCore
import com.tx24.spicyplayer.lyrics.spicy.canvas.kawarp.KawarpEngine
import com.tx24.spicyplayer.lyrics.spicy.canvas.kawarp.KawarpOptions
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.hypot
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.SamplingMode

private const val CANVAS_WIDTH = 300
private const val CANVAS_HEIGHT = 150

private val OPTIONS = KawarpOptions(
    warpIntensity = 1f,
    blurPasses = 8,
    animationSpeed = 0.1f,
    saturation = 1.5f,
    dithering = 0.008f,
    transitionDuration = 500f,
    tintIntensity = 0f,
    scale = 1f,
)

/**
 * The app's Kawarp background on Skia: the same AGSL, compiled as SkSL, drawn at the reference's
 * 300x150 and stretched over the window. The cover is a made-up one; there is no crossfade.
 */
@Composable
fun DesktopKawarp(modifier: Modifier = Modifier) {
    val engine = remember { KawarpEngine(OPTIONS) }
    val builder = remember {
        val texture = blurredTexture(SyntheticCover.pixels(256), 256)
        RuntimeShaderBuilder(RuntimeEffect.makeForShader(KAWARP_FUSED_AGSL)).apply {
            child("texCur", texture)
            child("texNext", texture)
        }
    }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { now ->
            if (last != 0L) engine.tick((now - last) / 1e9f)
            last = now
            frame = now
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val stretchX = constraints.maxWidth / CANVAS_WIDTH.toFloat()
        val stretchY = constraints.maxHeight / CANVAS_HEIGHT.toFloat()
        Canvas(
            Modifier
                .layout { measurable, _ ->
                    val canvas = measurable.measure(Constraints.fixed(CANVAS_WIDTH, CANVAS_HEIGHT))
                    layout(canvas.width, canvas.height) { canvas.place(0, 0) }
                }
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = stretchX
                    scaleY = stretchY
                    compositingStrategy = CompositingStrategy.Offscreen
                },
        ) {
            @Suppress("UNUSED_EXPRESSION") frame
            builder.uniform("uResolution", size.width, size.height)
            builder.uniform("uTime", engine.accumulatedTime)
            builder.uniform("uBlend", 0f)
            builder.uniform("uIntensity", OPTIONS.warpIntensity)
            builder.uniform("uSaturation", OPTIONS.saturation)
            builder.uniform("uDithering", OPTIONS.dithering)
            builder.uniform("uScale", OPTIONS.scale)
            drawRect(brush = ShaderBrush(builder.makeShader().asComposeShader()))
        }
    }
}

/** The cover through Kawarp's blur, as a premultiplied half-float texture, like the app's. */
private fun blurredTexture(cover: FloatArray, size: Int): org.jetbrains.skia.Shader {
    val n = KawarpBlurCore.BLUR_SIZE
    val out = KawarpBlurCore.process(cover, size, size, OPTIONS.blurPasses, OPTIONS.tintColor, OPTIONS.tintIntensity)
    val bytes = ByteBuffer.allocate(n * n * 8).order(ByteOrder.LITTLE_ENDIAN)
    for (i in 0 until n * n * 4 step 4) {
        val a = out[i + 3].coerceIn(0f, 1f)
        repeat(3) { c -> bytes.putShort(half(out[i + c].coerceIn(0f, 1f) * a)) }
        bytes.putShort(half(a))
    }
    val info = ImageInfo(n, n, ColorType.RGBA_F16, ColorAlphaType.PREMUL, ColorSpace.sRGB)
    return Image.makeRaster(info, bytes.array(), n * 8)
        .makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, SamplingMode.LINEAR)
}

/** IEEE half from a float in 0..1 (no infinities or NaNs to handle); JDK 17 has no Float.floatToFloat16. */
private fun half(value: Float): Short {
    if (value <= 0f) return 0
    val bits = java.lang.Float.floatToIntBits(value)
    val exp = ((bits ushr 23) and 0xFF) - 127 + 15
    if (exp <= 0) return ((bits and 0x7FFFFF or 0x800000) ushr (14 - exp)).let { (it + 0x1000) ushr 13 }.toShort()
    return ((exp shl 10) + (((bits and 0x7FFFFF) + 0x1000) ushr 13)).toShort()
}

/** A stand-in album cover: a few soft colour blobs, RGBA floats, top row first. */
private object SyntheticCover {
    private val blobs = listOf(
        floatArrayOf(0.25f, 0.3f, 0.95f, 0.35f, 0.55f),
        floatArrayOf(0.75f, 0.25f, 0.2f, 0.6f, 0.95f),
        floatArrayOf(0.5f, 0.8f, 0.98f, 0.75f, 0.2f),
        floatArrayOf(0.15f, 0.85f, 0.3f, 0.9f, 0.6f),
    )

    fun pixels(size: Int) = FloatArray(size * size * 4).also { px ->
        for (y in 0 until size) for (x in 0 until size) {
            val i = (y * size + x) * 4
            var r = 0.05f; var g = 0.04f; var b = 0.1f
            for ((bx, by, br, bg, bb) in blobs) {
                val d = hypot(x / size.toFloat() - bx, y / size.toFloat() - by)
                val w = exp(-d * d * 18f)
                r += br * w; g += bg * w; b += bb * w
            }
            px[i] = r.coerceAtMost(1f); px[i + 1] = g.coerceAtMost(1f); px[i + 2] = b.coerceAtMost(1f); px[i + 3] = 1f
        }
    }
}
