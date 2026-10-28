package com.tx24.spicyplayer.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.tx24.spicyplayer.lyrics.spicy.canvas.LyricsImageLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The credits' profile pictures: fetched through [fetch] (a [DesktopImageCache] in the app),
 * decoded and shrunk to about the asked size off the main thread, and kept in memory, the last
 * [MAX_AVATARS] of them, so a redraw doesn't decode again. A picture that can't be fetched or
 * decoded is [PlaceholderAvatars]' square; a failure isn't remembered, so it's tried again next time.
 */
class DesktopAvatars(
    private val fetch: (String) -> ByteArray?,
    private val decode: (ByteArray, Int) -> ImageBitmap = { bytes, sizePx -> decodeScaled(bytes, sizePx).toComposeImageBitmap() },
) : LyricsImageLoader {
    private val memory = object : LinkedHashMap<String, ImageBitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > MAX_AVATARS
    }

    override suspend fun load(url: String, sizePx: Int): ImageBitmap? {
        val key = "$sizePx $url"
        synchronized(memory) { memory[key] }?.let { return it }
        val image = try {
            withContext(Dispatchers.IO) { fetch(url)?.let { decode(it, sizePx) } }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        } ?: return PlaceholderAvatars.load(url, sizePx)
        synchronized(memory) { memory[key] = image }
        return image
    }
}

/** How many decoded pictures [DesktopAvatars] keeps: a song's credits are a handful. */
const val MAX_AVATARS = 64
