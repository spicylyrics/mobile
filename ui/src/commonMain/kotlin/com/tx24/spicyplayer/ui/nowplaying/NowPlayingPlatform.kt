package com.tx24.spicyplayer.ui.nowplaying

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import com.tx24.spicyplayer.lyrics.spicy.canvas.LocalLyricsImageLoader
import kotlinx.coroutines.CancellationException

/** What the animated cover is looked up by. */
data class MotionCoverQuery(val artist: String, val album: String, val title: String)

/**
 * The animated cover's lookup. While a new song's is under way, it holds the last answer, not
 * [settled], so a loop on screen can fade out or give way to the next one instead of vanishing
 * the moment the song changes.
 */
data class MotionCoverLookup(val url: String?, val settled: Boolean)

/** A cover ready to draw. */
interface CoverImage {
    val image: ImageBitmap

    /** Changes when the picture does, even if the player sent the same one again as a new object. */
    val fingerprint: Int

    /** Whether [other] is the same picture, maybe at another size or encoding. */
    fun sameCover(other: CoverImage): Boolean
}

/**
 * What the now-playing views need from the platform: covers as the player sends them, the
 * animated covers and Canvas loops, credit avatars, and the cover's drop shadow. Provided through
 * [LocalNowPlayingPlatform]; [BasicNowPlayingPlatform] draws the still cover the player sent and
 * no loops.
 */
interface NowPlayingPlatform {
    /**
     * The cover to draw from what the player sent ([artwork], or the address [artworkUri]), no
     * larger than [maxDimension] on its long side. Null until there is one. Without a session
     * ([hasSession] false) a stand-in may show at once.
     */
    @Composable
    fun rememberCover(artwork: ImageBitmap?, artworkUri: String?, maxDimension: Int, hasSession: Boolean): CoverImage?

    /** The record's animated cover, looked up while [enabled]. */
    @Composable
    fun rememberMotionCover(enabled: Boolean, query: MotionCoverQuery): MotionCoverLookup

    /** Drops [query]'s animated cover after its stream failed, so it isn't offered again. */
    suspend fun forgetMotionCover(query: MotionCoverQuery)

    /** The animated cover's loop at [url], fading in once it has a frame and while [visible]. */
    @Composable
    fun MotionCoverVideo(url: String, visible: Boolean, onFailed: () -> Unit, modifier: Modifier)

    /**
     * A loop that crossfades from one [url] to the next as songs change, covering its box at its
     * own shape, cropped [cropFromTop] of the way down (null: centred). [onFailed] gets the URL
     * that wouldn't play.
     */
    @Composable
    fun CrossfadingMotionVideo(url: String?, onFailed: (String) -> Unit, modifier: Modifier, cropFromTop: Float?)

    /** The picture at [url], cropped to fill [modifier]'s box; nothing while it loads. */
    @Composable
    fun Avatar(url: String, modifier: Modifier)

    /**
     * Draws a rounded rectangle's shadow under the cover, filling [scope]: a CSS box-shadow of
     * [cssBlurPx] blur, [offsetYPx] down, in [color]. Does nothing where the platform can't.
     */
    fun drawCoverShadow(scope: DrawScope, cornerRadiusPx: Float, cssBlurPx: Float, offsetYPx: Float, color: Color)

    /** Whether a layer can be blurred (a render effect). */
    val blursLayers: Boolean
}

/**
 * The still cover the player sent and nothing more: no loops, no shadow. Avatars come through the
 * renderer's [LocalLyricsImageLoader], when there is one.
 */
open class BasicNowPlayingPlatform : NowPlayingPlatform {
    @Composable
    override fun rememberCover(artwork: ImageBitmap?, artworkUri: String?, maxDimension: Int, hasSession: Boolean): CoverImage? =
        remember(artwork) { artwork?.let(::PlainCover) }

    @Composable
    override fun rememberMotionCover(enabled: Boolean, query: MotionCoverQuery): MotionCoverLookup = NoMotionCover

    override suspend fun forgetMotionCover(query: MotionCoverQuery) = Unit

    @Composable
    override fun MotionCoverVideo(url: String, visible: Boolean, onFailed: () -> Unit, modifier: Modifier) = Unit

    @Composable
    override fun CrossfadingMotionVideo(url: String?, onFailed: (String) -> Unit, modifier: Modifier, cropFromTop: Float?) = Unit

    @Composable
    override fun Avatar(url: String, modifier: Modifier) {
        val loader = LocalLyricsImageLoader.current ?: return
        val image by produceState<ImageBitmap?>(null, url, loader) {
            value = try {
                loader.load(url, AVATAR_PX)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
        image?.let { Image(it, null, modifier, contentScale = ContentScale.Crop) }
    }

    override fun drawCoverShadow(scope: DrawScope, cornerRadiusPx: Float, cssBlurPx: Float, offsetYPx: Float, color: Color) = Unit

    override val blursLayers: Boolean get() = true
}

/** A cover that is the player's picture as it came: the same object is the same cover. */
class PlainCover(override val image: ImageBitmap) : CoverImage {
    override val fingerprint: Int get() = image.hashCode()
    override fun sameCover(other: CoverImage): Boolean = other.image === image
}

/** The platform's now-playing services; Android provides its own at the top of the screen. */
val LocalNowPlayingPlatform = staticCompositionLocalOf<NowPlayingPlatform> { BasicNowPlayingPlatform() }

private val NoMotionCover = MotionCoverLookup(null, settled = true)
private const val AVATAR_PX = 64
