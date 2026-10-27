package com.tx24.spicyplayer.lyrics.spicy.canvas

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.tx24.spicyplayer.renderer.R

internal actual object LyricFonts {
    actual val bundled = FontFamily(
        Font(R.font.lyrics_regular, FontWeight.Normal),
        Font(R.font.lyrics_medium, FontWeight.Medium),
        Font(R.font.lyrics_semibold, FontWeight.SemiBold),
        Font(R.font.lyrics_bold, FontWeight.Bold)
    )
    actual val vazirmatn = variableFamily(R.font.vazirmatn_variable)
    actual val georgian = variableFamily(R.font.noto_sans_georgian_variable)

    actual fun scriptFamily(text: String, weight: FontWeight): FontFamily? = null

    /**
     * A variable font at each weight the renderer asks for. The weight has to be set on the
     * font's `wght` axis: declared alone, every weight drew the file's default instance (400),
     * so bold Arabic and Georgian lyrics came out Regular.
     */
    @OptIn(ExperimentalTextApi::class)
    private fun variableFamily(resId: Int) = FontFamily(
        listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
            Font(resId, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
        },
    )
}
