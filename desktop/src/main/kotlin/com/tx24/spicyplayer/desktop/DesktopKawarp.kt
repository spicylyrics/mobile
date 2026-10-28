package com.tx24.spicyplayer.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.tx24.spicyplayer.lyrics.spicy.canvas.kawarp.KAWARP_FUSED_AGSL
import com.tx24.spicyplayer.lyrics.spicy.canvas.kawarp.KawarpBlurCore
import com.tx24.spicyplayer.lyrics.spicy.canvas.kawarp.KawarpEngine
import com.tx24.spicyplayer.lyrics.spicy.canvas.kawarp.KawarpOptions
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas as SkCanvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.SamplingMode

private const val CANVAS_WIDTH = 300
private const val CANVAS_HEIGHT = 150
/** Covers are scaled to this square before the blur. */
private const val COVER_SIZE = 256

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
 * 300x150 and stretched over the window. Each new [cover] crossfades in over the last; without
 * one, a made-up cover shows. [speed] is read every frame while playing; null (or returning null)
 * is the normal speed. Without [animate] it holds still, drawing only while a new cover fades in.
 * [blurPasses] is how much the cover is blurred (Kawarp's own is 8).
 */
@Composable
fun DesktopKawarp(
    cover: File? = null,
    isPlaying: Boolean = true,
    speed: (() -> Float?)? = null,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    blurPasses: Int = OPTIONS.blurPasses,
) {
    val playing by rememberUpdatedState(isPlaying)
    val latestSpeed by rememberUpdatedState(speed)
    val engine = remember { KawarpEngine(OPTIONS) }
    val effect = remember { RuntimeEffect.makeForShader(KAWARP_FUSED_AGSL) }
    val placeholder = remember { blurredTexture(SyntheticCover.pixels(COVER_SIZE), COVER_SIZE) }
    var current by remember { mutableStateOf(placeholder) }
    var next by remember { mutableStateOf(placeholder) }
    LaunchedEffect(cover, blurPasses) {
        val file = cover ?: return@LaunchedEffect
        val texture = withContext(Dispatchers.Default) {
            runCatching { decodeCover(file)?.let { blurredTexture(it, COVER_SIZE, blurPasses) } }.getOrNull()
        } ?: return@LaunchedEffect
        current = next
        next = texture
        engine.startTransition(System.currentTimeMillis())
    }
    val builder = remember(current, next) {
        RuntimeShaderBuilder(effect).apply {
            child("texCur", current)
            child("texNext", next)
        }
    }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(animate, next) {
        var last = 0L
        // Held still: frames only while a new cover fades in.
        while (animate || engine.blendFactor(System.currentTimeMillis()) < 1f) withFrameNanos { now ->
            // Playing: the song's beat-driven speed where there is one, else 1. Paused: a tenth.
            // The engine eases between them.
            engine.targetAnimationSpeed = if (playing) latestSpeed?.invoke() ?: 1f else 0.1f
            if (last != 0L) engine.tick((now - last) / 1e9f)
            last = now
            frame = now
        }
        // The crossfade's last, finished frame.
        frame = System.nanoTime()
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
            // The crossfade is eased with a half cosine, like the app's.
            builder.uniform("uBlend", 0.5f - 0.5f * cos(engine.blendFactor(System.currentTimeMillis()) * PI.toFloat()))
            builder.uniform("uIntensity", OPTIONS.warpIntensity)
            builder.uniform("uSaturation", OPTIONS.saturation)
            builder.uniform("uDithering", OPTIONS.dithering)
            builder.uniform("uScale", OPTIONS.scale)
            drawRect(brush = ShaderBrush(builder.makeShader().asComposeShader()))
        }
    }
}

/** A cover image file as [COVER_SIZE]-square RGBA floats, top row first; null if it doesn't decode. */
private fun decodeCover(file: File): FloatArray? {
    val image = Image.makeFromEncoded(file.readBytes())
    val info = ImageInfo(COVER_SIZE, COVER_SIZE, ColorType.RGBA_8888, ColorAlphaType.PREMUL)
    val bitmap = Bitmap().apply { allocPixels(info) }
    SkCanvas(bitmap).drawImageRect(
        image,
        Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
        Rect.makeWH(COVER_SIZE.toFloat(), COVER_SIZE.toFloat()),
        SamplingMode.LINEAR, null, true,
    )
    val bytes = bitmap.readPixels() ?: return null
    return FloatArray(bytes.size) { (bytes[it].toInt() and 0xFF) / 255f }
}

/** The cover through Kawarp's blur, as a premultiplied half-float texture, like the app's. */
private fun blurredTexture(cover: FloatArray, size: Int, passes: Int = OPTIONS.blurPasses): org.jetbrains.skia.Shader {
    val n = KawarpBlurCore.BLUR_SIZE
    val out = KawarpBlurCore.process(cover, size, size, passes, OPTIONS.tintColor, OPTIONS.tintIntensity)
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
