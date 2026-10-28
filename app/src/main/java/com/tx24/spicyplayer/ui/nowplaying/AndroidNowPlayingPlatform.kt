package com.tx24.spicyplayer.ui.nowplaying

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

/**
 * The phone's now-playing services: covers through [rememberSessionArtwork] (Coil for addresses,
 * the app logo as the stand-in), animated covers and Canvas loops through media3, avatars through
 * Coil, and the cover's shadow as a canvas shadow layer.
 */
class AndroidNowPlayingPlatform(context: Context) : NowPlayingPlatform {
    private val context = context.applicationContext

    @Composable
    override fun rememberCover(artwork: ImageBitmap?, artworkUri: String?, maxDimension: Int, hasSession: Boolean): CoverImage? =
        rememberSessionArtwork(artwork?.asAndroidBitmap(), artworkUri, maxDimension = maxDimension, hasSession = hasSession)

    @Composable
    override fun rememberMotionCover(enabled: Boolean, query: MotionCoverQuery): MotionCoverLookup =
        com.tx24.spicyplayer.ui.nowplaying.rememberMotionCover(enabled, query)

    override suspend fun forgetMotionCover(query: MotionCoverQuery) =
        com.tx24.spicyplayer.ui.nowplaying.forgetMotionCover(context, query)

    @Composable
    override fun MotionCoverVideo(url: String, visible: Boolean, onFailed: () -> Unit, modifier: Modifier) =
        com.tx24.spicyplayer.ui.nowplaying.MotionCoverVideo(url = url, visible = visible, onFailed = onFailed, modifier = modifier)

    @Composable
    override fun CrossfadingMotionVideo(url: String?, onFailed: (String) -> Unit, modifier: Modifier, cropFromTop: Float?) =
        com.tx24.spicyplayer.ui.nowplaying.CrossfadingMotionVideo(url = url, onFailed = onFailed, modifier = modifier, cropFromTop = cropFromTop)

    @Composable
    override fun Avatar(url: String, modifier: Modifier) {
        AsyncImage(url, null, modifier, contentScale = ContentScale.Crop)
    }

    override fun drawCoverShadow(scope: DrawScope, cornerRadiusPx: Float, cssBlurPx: Float, offsetYPx: Float, color: Color) {
        // Hardware canvases only draw shadow layers for shapes from API 28.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        scope.drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.color = android.graphics.Color.TRANSPARENT
                setShadowLayer(cssBlurToShadowRadius(cssBlurPx), 0f, offsetYPx, color.toArgb())
            }
            canvas.nativeCanvas.drawRoundRect(0f, 0f, scope.size.width, scope.size.height, cornerRadiusPx, cornerRadiusPx, paint)
        }
    }

    override val blursLayers: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

/**
 * A CSS shadow's blur length is twice the Gaussian sigma; Android's shadow radius maps to
 * sigma = radius * 0.57735 + 0.5 (Skia's conversion), so invert that.
 */
private fun cssBlurToShadowRadius(cssBlurPx: Float): Float =
    ((cssBlurPx / 2f - 0.5f) / 0.57735f).coerceAtLeast(0.1f)
