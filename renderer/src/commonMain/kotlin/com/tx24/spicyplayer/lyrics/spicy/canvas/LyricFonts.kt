package com.tx24.spicyplayer.lyrics.spicy.canvas

import androidx.compose.ui.text.font.FontFamily

/**
 * The bundled lyric fonts: the lyrics font at the four weights the renderer uses, and the
 * variable Vazirmatn (Arabic script) and Noto Sans Georgian.
 */
internal expect object LyricFonts {
    val bundled: FontFamily
    val vazirmatn: FontFamily
    val georgian: FontFamily
}
