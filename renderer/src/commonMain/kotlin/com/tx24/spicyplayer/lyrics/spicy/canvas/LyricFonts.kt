package com.tx24.spicyplayer.lyrics.spicy.canvas

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * The bundled lyric fonts: the lyrics font at the four weights the renderer uses, and the
 * variable Vazirmatn (Arabic script) and Noto Sans Georgian.
 */
internal expect object LyricFonts {
    val bundled: FontFamily
    val vazirmatn: FontFamily
    val georgian: FontFamily

    /**
     * The platform's own font for a lyric line's script at [weight], ahead of ours; null leaves
     * the line to ours. Android's font fallback already picks well, so it has none.
     */
    fun scriptFamily(text: String, weight: FontWeight): FontFamily?
}
