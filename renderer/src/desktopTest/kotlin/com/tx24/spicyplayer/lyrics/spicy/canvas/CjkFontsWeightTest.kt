package com.tx24.spicyplayer.lyrics.spicy.canvas

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.Paragraph
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Each Windows CJK font draws lyric lines in its Bold face, through Compose's own text path. */
class CjkFontsWeightTest {
    private val resolver = createFontFamilyResolver()
    private val text = "我們的愛說不出口"

    // Total alpha of the painted text: a Bold face lays down far more ink than Regular.
    private fun ink(family: FontFamily, weight: FontWeight): Long {
        val paragraph = Paragraph(text, TextStyle(fontFamily = family, fontWeight = weight, fontSize = 40.sp, color = Color.White),
            Constraints(maxWidth = 4000), Density(1f), resolver)
        val image = ImageBitmap(paragraph.width.toInt(), paragraph.height.toInt())
        paragraph.paint(Canvas(image))
        val pixels = image.toPixelMap()
        var sum = 0L
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) sum += (pixels[x, y].alpha * 255).toLong()
        return sum
    }

    @Test fun `lyric weights draw bold in every CJK font`() {
        for (name in listOf("Microsoft JhengHei", "Microsoft YaHei", "Yu Gothic", "Malgun Gothic")) {
            val regular = CjkFonts.family(name, FontWeight.Normal)
            assumeTrue("$name is not installed", regular != null)
            val bold = ink(CjkFonts.family(name, FontWeight.Bold)!!, FontWeight.Bold)
            val semiBold = ink(CjkFonts.family(name, FontWeight.SemiBold)!!, FontWeight.SemiBold)
            val normal = ink(regular!!, FontWeight.Normal)
            assertTrue("$name Bold drew $bold ink against Regular's $normal", bold > normal * 1.3)
            // None of these fonts has a SemiBold, so background vocals get the Bold face too.
            assertEquals("$name SemiBold", bold, semiBold)
        }
    }
}
