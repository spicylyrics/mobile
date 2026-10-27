package com.tx24.spicyplayer.lyrics.spicy.canvas

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap

/** Loads the credits' profile pictures for the renderer, which has no image loader of its own. */
fun interface LyricsImageLoader {
    /** The picture at [url], about [sizePx] square, or null if it can't be had. */
    suspend fun load(url: String, sizePx: Int): ImageBitmap?
}

/** The host's image loader. Without one, the credits draw no profile pictures. */
val LocalLyricsImageLoader = staticCompositionLocalOf<LyricsImageLoader?> { null }
