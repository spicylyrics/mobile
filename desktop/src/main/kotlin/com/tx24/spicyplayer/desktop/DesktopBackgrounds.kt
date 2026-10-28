package com.tx24.spicyplayer.desktop

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.tx24.spicyplayer.ui.background.BackgroundKind
import com.tx24.spicyplayer.ui.background.CoverColors
import com.tx24.spicyplayer.ui.background.HeaderLoad
import com.tx24.spicyplayer.ui.background.LEGACY_DRIFT_LOOP_MS
import com.tx24.spicyplayer.ui.background.LegacyLayerBoxes
import com.tx24.spicyplayer.ui.background.STILL_MAX_PX
import com.tx24.spicyplayer.ui.background.StillPick
import com.tx24.spicyplayer.ui.background.cssBlurSigmaToRadius
import com.tx24.spicyplayer.ui.background.gradeStillPixel
import com.tx24.spicyplayer.ui.background.kindOf
import com.tx24.spicyplayer.ui.background.legacyDrift
import com.tx24.spicyplayer.ui.background.pickStill
import com.tx24.spicyplayer.network.data.spotify.LocalTrackMetadata
import com.tx24.spicyplayer.network.data.spotify.SpotifyTrackExtras
import com.tx24.spicyplayer.ui.settings.BackgroundType
import java.io.File
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

/**
 * The page's background for the chosen [type]: Default is [kawarp]; Legacy, Color and the still
 * pictures (Cover Art, Artist Header, Auto) are drawn here from the [cover] file and, for the
 * header types, the artist's header photo ([findArtistHeader], fetched through [images]). A change
 * of type crossfades. Anything that has no picture to show, or fails, shows [kawarp] instead.
 *
 * [blurDp] is the type's blur setting; [animate] holds Legacy's drift still when off. [trackKey]
 * names the song, so the header is looked up again when it changes.
 */
@Composable
fun DesktopBackgrounds(
    type: BackgroundType,
    blurDp: Int,
    animate: Boolean,
    cover: File?,
    trackKey: String?,
    findArtistHeader: suspend () -> String?,
    images: DesktopImageCache,
    kawarp: @Composable () -> Unit,
) {
    val coverBytes by produceState<ByteArray?>(null, cover?.path, cover?.lastModified()) {
        val file = cover ?: return@produceState
        withContext(Dispatchers.IO) { runCatching { file.readBytes() }.getOrNull() }?.let { value = it }
    }
    Crossfade(kindOf(type), Modifier.fillMaxSize().background(Color.Black), animationSpec = tween(TYPE_FADE_MS), label = "backgroundType") { kind ->
        when (kind) {
            BackgroundKind.Kawarp -> kawarp()
            BackgroundKind.Legacy -> LegacyLayers(coverBytes, animate, blurDp, kawarp)
            BackgroundKind.Color -> ColorPage(coverBytes, kawarp)
            BackgroundKind.Still -> StillPage(type, coverBytes, blurDp, trackKey, findArtistHeader, images, kawarp)
        }
    }
}

/** A change of type fades in over `900ms`, like the phone's. */
private const val TYPE_FADE_MS = 900

/** A still picture's change: `850 ms`. */
private const val STILL_CHANGE_MS = 850

/** The colours' change: `1.5 s`. */
private const val COLOR_CHANGE_MS = 1_500

private class HeaderLookup(val pending: Boolean, val url: String?)

/**
 * A still picture filling the page: the cover, or the artist's header photo for Artist Header and
 * Auto (the cover where there's none), cropped to cover the page, graded `brightness(0.55)
 * contrast(1.05) saturate(1.7)`, blurred by [blurDp] and drawn at 1.25× so the blur's soft edges
 * fall outside. A new picture crossfades in; while a header loads, the last picture stays.
 */
@Composable
private fun StillPage(
    type: BackgroundType,
    coverBytes: ByteArray?,
    blurDp: Int,
    trackKey: String?,
    findArtistHeader: suspend () -> String?,
    images: DesktopImageCache,
    kawarp: @Composable () -> Unit,
) {
    val lookup by produceState(HeaderLookup(type.usesArtistHeader, null), trackKey, type.usesArtistHeader) {
        if (!type.usesArtistHeader || trackKey == null) {
            value = HeaderLookup(false, null)
            return@produceState
        }
        value = HeaderLookup(true, null)
        val url = runCatching { withContext(Dispatchers.IO) { findArtistHeader() } }.getOrNull()
        value = HeaderLookup(false, url)
    }
    // Which header has downloaded, and what it gave (null: it failed, so the cover shows).
    val header by produceState<Pair<String, ByteArray?>?>(null, lookup.url) {
        val url = lookup.url ?: return@produceState
        value = url to withContext(Dispatchers.IO) { images.bytes(url) }
    }
    val loaded = header?.takeIf { it.first == lookup.url }
    val load = when {
        loaded == null -> HeaderLoad.NotYet
        loaded.second != null -> HeaderLoad.Loaded
        else -> HeaderLoad.Failed
    }
    val bytes = when (pickStill(type, lookup.url, lookup.pending, load)) {
        StillPick.Header -> loaded?.second
        StillPick.Cover -> coverBytes
        StillPick.Wait -> null
    }
    val picture by produceState<ImageBitmap?>(null, bytes) {
        val source = bytes ?: return@produceState
        withContext(Dispatchers.Default) { runCatching { gradedStill(source) }.getOrNull() }?.let { value = it }
    }
    val shown = picture
    if (shown == null) {
        // Nothing to show yet: black while a header is on its way, the moving cover otherwise.
        if (bytes == null && lookup.pending) Box(Modifier.fillMaxSize().background(Color.Black)) else kawarp()
        return
    }
    val blurPx = blurDp.coerceAtLeast(0) * LocalDensity.current.density
    Crossfade(shown, Modifier.fillMaxSize().clipToBounds().background(Color.Black), animationSpec = tween(STILL_CHANGE_MS), label = "still") { image ->
        Image(
            image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.25f
                    scaleY = 1.25f
                    if (blurPx > 0f) {
                        val radius = cssBlurSigmaToRadius(blurPx)
                        renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
                    }
                },
        )
    }
}

/**
 * The cover's colours as a plain page: its lighter shade over all of it, its darker shade over
 * the top 90% fading out downwards, and a light black shade rising from the bottom. The colours
 * move to a new song's over 1.5 s.
 */
@Composable
private fun ColorPage(coverBytes: ByteArray?, kawarp: @Composable () -> Unit) {
    val colors by produceState<CoverColors?>(null, coverBytes) {
        val source = coverBytes ?: return@produceState
        withContext(Dispatchers.Default) { runCatching { coverColorsOf(source) }.getOrNull() }?.let { value = it }
    }
    val found = colors
    if (found == null) {
        kawarp()
        return
    }
    val base by animateColorAsState(Color(found.minContrast), tween(COLOR_CHANGE_MS), label = "colorBase")
    val top by animateColorAsState(Color(found.highContrast), tween(COLOR_CHANGE_MS), label = "colorTop")
    Canvas(Modifier.fillMaxSize()) {
        drawRect(base)
        // `height: 90%`, `mask-image: linear-gradient(to top, transparent, black 145%)`.
        val band = size.height * 0.9f
        drawRect(
            Brush.verticalGradient(0f to top.copy(alpha = top.alpha / 1.45f), 1f to top.copy(alpha = 0f), startY = 0f, endY = band),
            topLeft = Offset.Zero,
            size = Size(size.width, band),
        )
        // `opacity: 0.3; linear-gradient(to top, black, transparent)`.
        drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.3f)))
    }
}

/**
 * The Legacy background: three blurred, pill-shaped copies of the cover drifting on a 45 s loop
 * ([legacyDrift], [LegacyLayerBoxes]), the whole thing at `saturate(1.5) brightness(0.8)` over
 * black. Each layer is cut and blurred once per cover and window size at a small size; a frame
 * only moves them.
 */
@Composable
private fun LegacyLayers(coverBytes: ByteArray?, animate: Boolean, blurDp: Int, kawarp: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds().background(Color.Black)) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val blurPx = blurDp.coerceAtLeast(0) * LocalDensity.current.density
        val layers by produceState<List<LegacyLayer>?>(null, coverBytes, w, h, blurPx) {
            val source = coverBytes ?: return@produceState
            if (w <= 0 || h <= 0) return@produceState
            withContext(Dispatchers.Default) { runCatching { legacyLayers(source, w, h, blurPx) }.getOrNull() }?.let { value = it }
        }
        var progress by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(animate) {
            if (!animate) {
                progress = 0f
                return@LaunchedEffect
            }
            val start = withFrameNanos { it }
            while (true) {
                withFrameNanos { now -> progress = (((now - start) / 1_000_000L) % LEGACY_DRIFT_LOOP_MS).toFloat() / LEGACY_DRIFT_LOOP_MS }
            }
        }
        val drawn = layers
        if (drawn == null) {
            kawarp()
            return@BoxWithConstraints
        }
        Canvas(Modifier.fillMaxSize()) { drawLegacy(drawn, progress) }
    }
}

private fun DrawScope.drawLegacy(layers: List<LegacyLayer>, progress: Float) {
    val drift = legacyDrift(progress)
    for (layer in layers) {
        withTransform({
            // translate3d(%, %) of the layer's own box, then scale about its centre.
            translate(layer.boxWidth * drift.translateX, layer.boxHeight * drift.translateY)
            scale(drift.scale, drift.scale, Offset(layer.centerX, layer.centerY))
        }) {
            drawImage(
                image = layer.image,
                dstOffset = layer.dstOffset,
                dstSize = layer.dstSize,
                colorFilter = LegacyFilter,
                filterQuality = FilterQuality.Low,
            )
        }
    }
}

/** `saturate(1.5) brightness(0.8)`: no offsets, so the matrix means the same on every platform. */
private val LegacyFilter: ColorFilter = ColorFilter.colorMatrix(
    ColorMatrix().apply {
        setToSaturation(1.5f)
        timesAssign(ColorMatrix().apply { setToScale(0.8f, 0.8f, 0.8f, 1f) })
    },
)

private class LegacyLayer(
    val image: ImageBitmap,
    val boxWidth: Float,
    val boxHeight: Float,
    val centerX: Float,
    val centerY: Float,
    val dstOffset: IntOffset,
    val dstSize: IntSize,
)

/** Blurred to 40px, the layers carry no detail; they're cut at this height. */
private const val LEGACY_LAYER_HEIGHT_PX = 128f

private fun legacyLayers(coverBytes: ByteArray, w: Int, h: Int, blurPx: Float): List<LegacyLayer> {
    val cover = Image.makeFromEncoded(coverBytes)
    val scale = LEGACY_LAYER_HEIGHT_PX / h
    val sigma = blurPx * scale
    val margin = ceil(sigma * 3f).toInt()
    return LegacyLayerBoxes.map { (l, t, bw, bh) ->
        val left = l * w
        val top = t * h
        val boxW = bw * w
        val boxH = bh * h
        val sw = max(1, (boxW * scale).roundToInt())
        val sh = max(1, (boxH * scale).roundToInt())
        val surface = Surface.makeRasterN32Premul(sw + margin * 2, sh + margin * 2)
        val canvas = surface.canvas
        val blur = Paint().apply { if (sigma > 0f) imageFilter = ImageFilter.makeBlur(sigma, sigma, FilterTileMode.DECAL) }
        canvas.saveLayer(null, blur)
        canvas.clipRRect(RRect.makeXYWH(margin.toFloat(), margin.toFloat(), sw.toFloat(), sh.toFloat(), min(sw, sh) / 2f), true)
        val crop = coverCrop(cover.width, cover.height, sw.toFloat(), sh.toFloat())
        canvas.drawImageRect(
            cover,
            Rect.makeXYWH(crop[0].toFloat(), crop[1].toFloat(), crop[2].toFloat(), crop[3].toFloat()),
            Rect.makeXYWH(margin.toFloat(), margin.toFloat(), sw.toFloat(), sh.toFloat()),
        )
        canvas.restore()
        val pad = margin / scale
        LegacyLayer(
            image = surface.makeImageSnapshot().toComposeImageBitmap(),
            boxWidth = boxW,
            boxHeight = boxH,
            centerX = left + boxW / 2f,
            centerY = top + boxH / 2f,
            dstOffset = IntOffset((left - pad).roundToInt(), (top - pad).roundToInt()),
            dstSize = IntSize((boxW + 2 * pad).roundToInt(), (boxH + 2 * pad).roundToInt()),
        )
    }
}

/**
 * `background-size: cover; background-position: center`: the part of a [cw]×[ch] picture that
 * fills a [bw]×[bh] box, as left, top, width, height.
 */
fun coverCrop(cw: Int, ch: Int, bw: Float, bh: Float): IntArray {
    val fit = max(bw / cw, bh / ch)
    val sw = bw / fit
    val sh = bh / fit
    return intArrayOf(((cw - sw) / 2f).roundToInt(), ((ch - sh) / 2f).roundToInt(), sw.roundToInt(), sh.roundToInt())
}

/** [bytes] decoded, no longer than [maxSide] on its long side. */
internal fun decodeScaled(bytes: ByteArray, maxSide: Int): Image {
    val image = Image.makeFromEncoded(bytes)
    val longest = max(image.width, image.height)
    if (longest <= maxSide) return image
    val w = (image.width * maxSide.toFloat() / longest).roundToInt().coerceAtLeast(1)
    val h = (image.height * maxSide.toFloat() / longest).roundToInt().coerceAtLeast(1)
    return resized(image, w, h)
}

private fun resized(image: Image, w: Int, h: Int): Image {
    val surface = Surface.makeRasterN32Premul(w, h)
    surface.canvas.drawImageRect(image, Rect.makeWH(w.toFloat(), h.toFloat()))
    return surface.makeImageSnapshot()
}

/** [image]'s pixels as ARGB ints. */
private fun argbPixels(image: Image): IntArray {
    val info = ImageInfo(image.width, image.height, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
    val bitmap = Bitmap().apply { allocPixels(info) }
    image.readPixels(bitmap)
    val bytes = bitmap.readPixels(info, info.minRowBytes, 0, 0) ?: return IntArray(0)
    return IntArray(image.width * image.height) { i ->
        val o = i * 4
        ((bytes[o + 3].toInt() and 0xFF) shl 24) or ((bytes[o + 2].toInt() and 0xFF) shl 16) or
            ((bytes[o + 1].toInt() and 0xFF) shl 8) or (bytes[o].toInt() and 0xFF)
    }
}

/** The still background's picture: [bytes] decoded, at most [STILL_MAX_PX], graded ([gradeStillPixel]). */
private fun gradedStill(bytes: ByteArray): ImageBitmap {
    val image = decodeScaled(bytes, STILL_MAX_PX)
    val pixels = argbPixels(image)
    val out = ByteArray(pixels.size * 4)
    for (i in pixels.indices) {
        val p = gradeStillPixel(pixels[i])
        val o = i * 4
        out[o] = (p and 0xFF).toByte()
        out[o + 1] = ((p shr 8) and 0xFF).toByte()
        out[o + 2] = ((p shr 16) and 0xFF).toByte()
        out[o + 3] = 0xFF.toByte()
    }
    val info = ImageInfo(image.width, image.height, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
    return Image.makeRaster(info, out, info.minRowBytes).toComposeImageBitmap()
}

/** The Color background's shades, from a 48×48 copy of the cover. */
private fun coverColorsOf(bytes: ByteArray): CoverColors =
    CoverColors.of(argbPixels(resized(Image.makeFromEncoded(bytes), 48, 48)))

/** The artist's header photo for [track], through Spotify: null where there's none or the song can't be found. */
suspend fun artistHeaderUrl(spotify: SpotifyTrackExtras, track: Track): String? {
    val local = LocalTrackMetadata(track.title, track.artist, track.album, track.durationMs)
    val trackId = spotify.trackIds(local, null).firstOrNull() ?: return null
    val artistId = spotify.details(trackId)?.artistIds?.firstOrNull() ?: return null
    return spotify.artistHeaderUrl(artistId, trackId)
}
