package com.tx24.spicyplayer.lyrics.spicy.canvas

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/** The font files from the classpath. Variable fonts draw at their default instance here. */
internal actual object LyricFonts {
    actual val bundled = FontFamily(
        font("lyrics_regular", FontWeight.Normal),
        font("lyrics_medium", FontWeight.Medium),
        font("lyrics_semibold", FontWeight.SemiBold),
        font("lyrics_bold", FontWeight.Bold),
    )
    actual val vazirmatn = variableFamily("vazirmatn_variable")
    actual val georgian = variableFamily("noto_sans_georgian_variable")

    /** Windows' CJK fonts for Chinese, Japanese and Korean lines ([CjkFonts]). */
    actual fun scriptFamily(text: String, weight: FontWeight): FontFamily? = CjkFonts.familyFor(text, weight)

    private fun variableFamily(name: String) = FontFamily(
        listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { font(name, it) },
    )

    private fun font(name: String, weight: FontWeight): Font {
        val bytes = LyricFonts::class.java.getResourceAsStream("/font/$name.ttf")!!.use { it.readBytes() }
        return androidx.compose.ui.text.platform.Font("$name-${weight.weight}", bytes, weight)
    }
}
