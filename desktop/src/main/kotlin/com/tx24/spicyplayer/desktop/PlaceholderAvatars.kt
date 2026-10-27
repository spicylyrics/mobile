package com.tx24.spicyplayer.desktop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.tx24.spicyplayer.lyrics.spicy.canvas.LyricsImageLoader

/** No image loading yet: every credits avatar is the same flat orange square (the credits clip it round). */
object PlaceholderAvatars : LyricsImageLoader {
    private val square by lazy {
        ImageBitmap(128, 128).also { image ->
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(128f, 128f)) {
                drawRect(Color(0xFFE08A3C))
            }
        }
    }

    override suspend fun load(url: String, sizePx: Int): ImageBitmap = square
}
