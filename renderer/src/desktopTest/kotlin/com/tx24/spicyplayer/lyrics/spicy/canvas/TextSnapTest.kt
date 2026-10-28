package com.tx24.spicyplayer.lyrics.spicy.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
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

/** Lyric text moves continuously instead of stepping between pixel rows and quarter-pixel slots. */
class TextSnapTest {
    private val layout = TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr)
        .measure("Shaky words", TextStyle(fontFamily = LyricFonts.bundled, fontWeight = FontWeight.Bold, fontSize = 48.sp, color = Color.White))
    private val topLeft = Offset(40f, 40f)
    private val width = layout.size.width + 80
    private val height = layout.size.height + 80

    private enum class Path(val draw: DrawScope.(androidx.compose.ui.text.TextLayoutResult, Offset) -> Unit) {
        Plain({ l, o -> drawText(l, color = Color.White, topLeft = o) }),
        Tilted({ l, o -> unsnapped(l, o) { drawText(l, color = Color.White, topLeft = o) } }),
        Mask({ l, o -> drawLyricText(l, Color.White, o) }),
    }

    private fun render(path: Path, dy: Float, scale: Float, content: DrawScope.() -> Unit = { path.draw(this, layout, topLeft) }): ImageBitmap {
        val image = ImageBitmap(width, height)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(width.toFloat(), height.toFloat())) {
            withTransform({ scale(scale, scale, Offset(width / 2f, height / 2f)); translate(top = dy) }) { content() }
        }
        return image
    }

    /** The ink's vertical centre of mass with the word lifted by [dy] and scaled by [scale]. */
    private fun inkY(path: Path, dy: Float, scale: Float): Double {
        val pixels = render(path, dy, scale).toPixelMap()
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
    private fun liftError(path: Path): Double {
        val rest = inkY(path, 0f, 1f)
        return (0..40).maxOf { abs(inkY(path, it * 0.05f, 1f) - rest - it * 0.05f) }
    }

    /** The largest frame-to-frame move of the ink centre while scaling 1.00..1.05 about it in 0.25% steps (ideally 0). */
    private fun scaleJump(path: Path) =
        (0..20).map { inkY(path, 0f, 1f + it * 0.0025f) }.zipWithNext { a, b -> abs(b - a) }.max()

    @Test fun `a lifting word moves by fractions of a pixel`() {
        val errors = Path.entries.associateWith(::liftError)
        println("TextSnap lift error px: " + errors.entries.joinToString { "%s %.3f".format(it.key, it.value) })
        assertTrue("mask lift error ${errors[Path.Mask]} px", errors.getValue(Path.Mask) < 0.05)
    }

    @Test fun `a growing word does not jump`() {
        val jumps = Path.entries.associateWith(::scaleJump)
        println("TextSnap scale step jump px: " + jumps.entries.joinToString { "%s %.3f".format(it.key, it.value) })
        assertTrue("mask scale jump ${jumps[Path.Mask]} px", jumps.getValue(Path.Mask) < 0.05)
    }

    @Test fun `the mask takes the brush`() {
        // Red on the left half of the word, blue on the right: the mask must show the gradient, not white.
        val w = layout.size.width.toFloat()
        val brush = Brush.horizontalGradient(0f to Color.Red, 0.5f to Color.Red, 0.5001f to Color.Blue, 1f to Color.Blue, startX = 0f, endX = w)
        val pixels = render(Path.Mask, 0f, 1f) { drawLyricText(layout, brush, topLeft) }.toPixelMap()
        fun tint(x0: Int, x1: Int): Pair<Double, Double> {
            var red = 0.0
            var blue = 0.0
            for (y in 0 until pixels.height) for (x in x0 until x1) { val c = pixels[x, y]; red += c.red * c.alpha; blue += c.blue * c.alpha }
            return red to blue
        }
        val (leftRed, leftBlue) = tint(topLeft.x.toInt(), (topLeft.x + w / 2 - 4).toInt())
        val (rightRed, rightBlue) = tint((topLeft.x + w / 2 + 4).toInt(), (topLeft.x + w).toInt())
        println("TextSnap brush: left red %.0f blue %.0f, right red %.0f blue %.0f".format(leftRed, leftBlue, rightRed, rightBlue))
        assertTrue("left half red", leftRed > 10 * leftBlue && leftRed > 50)
        assertTrue("right half blue", rightBlue > 10 * rightRed && rightBlue > 50)
    }
}
