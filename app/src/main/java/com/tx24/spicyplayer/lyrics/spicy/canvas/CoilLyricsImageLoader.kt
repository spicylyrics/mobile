package com.tx24.spicyplayer.lyrics.spicy.canvas

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult

/** The credits' profile pictures through Coil, for the renderer. */
class CoilLyricsImageLoader(private val context: Context) : LyricsImageLoader {
    override suspend fun load(url: String, sizePx: Int): ImageBitmap? {
        val request = ImageRequest.Builder(context).data(url).size(sizePx).allowHardware(false).build()
        return (Coil.imageLoader(context).execute(request) as? SuccessResult)?.drawable?.toBitmap()?.asImageBitmap()
    }
}
