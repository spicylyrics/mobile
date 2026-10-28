package com.tx24.spicyplayer.ui.bigscreen

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/** The page's own line glyphs that `:ui` has no match for, drawn on a 24-unit grid with 2-unit strokes. */
private fun DrawScope.grid(draw: DrawScope.(unit: Float, stroke: Stroke) -> Unit) {
    val unit = size.minDimension / 24f
    draw(unit, Stroke(width = 2f * unit, cap = StrokeCap.Round))
}

private val Ink = Color.White.copy(alpha = 0.92f)

/** Close: an X. */
@Composable
fun CloseGlyph(modifier: Modifier) = Canvas(modifier) {
    grid { u, s ->
        drawLine(Ink, Offset(6 * u, 6 * u), Offset(18 * u, 18 * u), s.width, StrokeCap.Round)
        drawLine(Ink, Offset(18 * u, 6 * u), Offset(6 * u, 18 * u), s.width, StrokeCap.Round)
    }
}

/** The Lyrics Manager: a stack of saved lyrics, drawn as a database. */
@Composable
fun LyricsManagerGlyph(modifier: Modifier) = Canvas(modifier) {
    grid { u, s ->
        drawOval(Ink, Offset(5 * u, 3.5f * u), Size(14 * u, 5 * u), style = s)
        drawLine(Ink, Offset(5 * u, 6 * u), Offset(5 * u, 18 * u), s.width, StrokeCap.Round)
        drawLine(Ink, Offset(19 * u, 6 * u), Offset(19 * u, 18 * u), s.width, StrokeCap.Round)
        drawArc(Ink, 0f, 180f, useCenter = false, topLeft = Offset(5 * u, 9.5f * u), size = Size(14 * u, 5 * u), style = s)
        drawArc(Ink, 0f, 180f, useCenter = false, topLeft = Offset(5 * u, 15.5f * u), size = Size(14 * u, 5 * u), style = s)
    }
}

/** The now-playing bar: a screen with the cover on the [onRight] or left side and lyric lines beside it. */
@Composable
fun NowBarGlyph(onRight: Boolean, modifier: Modifier) = Canvas(modifier) {
    grid { u, s ->
        drawRoundRect(Ink, Offset(3 * u, 4 * u), Size(18 * u, 16 * u), CornerRadius(3 * u), style = s)
        val coverX = if (onRight) 13 * u else 6.5f * u
        val linesX = if (onRight) 6.5f * u else 13.5f * u
        drawRoundRect(Ink, Offset(coverX, 9.5f * u), Size(4.5f * u, 4.5f * u), CornerRadius(u))
        drawLine(Ink, Offset(linesX, 10 * u), Offset(linesX + 4 * u, 10 * u), s.width, StrokeCap.Round)
        drawLine(Ink, Offset(linesX, 14 * u), Offset(linesX + 2.5f * u, 14 * u), s.width, StrokeCap.Round)
    }
}

/** Compact mode: the song as a bar over the lyrics, or ([compact]) back to the side. */
@Composable
fun CompactGlyph(compact: Boolean, modifier: Modifier) = Canvas(modifier) {
    grid { u, s ->
        drawRoundRect(Ink, Offset(3 * u, 4 * u), Size(18 * u, 16 * u), CornerRadius(3 * u), style = s)
        if (compact) {
            drawRoundRect(Ink, Offset(6.5f * u, 7.5f * u), Size(4.5f * u, 9 * u), CornerRadius(u))
        } else {
            drawRoundRect(Ink, Offset(6.5f * u, 7.5f * u), Size(11 * u, 3.5f * u), CornerRadius(u))
            drawLine(Ink, Offset(7 * u, 15 * u), Offset(17 * u, 15 * u), s.width, StrokeCap.Round)
        }
    }
}
