package com.tx24.spicyplayer.lyrics.spicy.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lyric text moves by fractions of a pixel instead of jumping a whole pixel row at a time. */
class TextSnapTest {
    private val layout = TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr)
        .measure("Shaky words", TextStyle(fontFamily = LyricFonts.bundled, fontWeight = FontWeight.Bold, fontSize = 48.sp, color = Color.White))

    /** The ink's vertical centre of mass with the word lifted by [dy] and scaled by [scale]. */
    private fun inkY(dy: Float, scale: Float, unsnap: Boolean): Double {
        val w = layout.size.width + 80
        val h = layout.size.height + 80
        val image = ImageBitmap(w, h)
        val topLeft = Offset(40f, 40f)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(w.toFloat(), h.toFloat())) {
            withTransform({ scale(scale, scale, Offset(w / 2f, h / 2f)); translate(top = dy) }) {
                if (unsnap) unsnapped(layout, topLeft) { drawText(layout, topLeft = topLeft) } else drawText(layout, topLeft = topLeft)
            }
        }
        val pixels = image.toPixelMap()
        var ink = 0.0
        var moment = 0.0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val a = pixels[x, y].alpha.toDouble()
            ink += a
            moment += a * y
        }
        return moment / ink
    }

    /** The worst gap between where the word should be and where it is, lifting 0..2 px in 0.05 px steps. */
    private fun liftError(unsnap: Boolean): Double {
        val rest = inkY(0f, 1f, unsnap)
        return (0..40).maxOf { abs(inkY(it * 0.05f, 1f, unsnap) - rest - it * 0.05f) }
    }

    @Test fun `a lifting word moves by fractions of a pixel`() {
        // Drawn straight, the baseline snaps to whole pixels: up to half a pixel off, then a jump.
        val snapped = liftError(unsnap = false)
        val unsnapped = liftError(unsnap = true)
        println("lift error: snapped %.3f px, unsnapped %.3f px".format(snapped, unsnapped))
        assertTrue("unsnapped lift error $unsnapped px", unsnapped < 0.1)
    }

    @Test fun `a growing word does not jump a pixel row`() {
        // Scaling 1.00..1.05 about the centre should leave the ink centre put. Snapped, it jumps a
        // whole pixel row partway; unsnapped, glyphs still sit on quarter pixels, so steps stay under 0.3 px.
        fun jump(unsnap: Boolean) = (0..20).map { inkY(0f, 1f + it * 0.0025f, unsnap) }.zipWithNext { a, b -> abs(b - a) }.max()
        val snapped = jump(unsnap = false)
        val unsnapped = jump(unsnap = true)
        println("scale step jump: snapped %.3f px, unsnapped %.3f px".format(snapped, unsnapped))
        assertTrue("largest frame-to-frame jump $unsnapped px", unsnapped < 0.3)
    }
}
