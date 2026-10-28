package com.tx24.spicyplayer.ui.settings.panel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The panel's line icons, drawn on one stroke so they read as a set; tinted where they're drawn.
 * Circles and rounded rectangles are written out as paths.
 */
internal object PanelIcons {
    val Lyrics = icon("Lyrics", "M2.5 4h11M2.5 8h7.5M2.5 12h9")

    val Background = icon(
        "Background",
        roundRect(2f, 2.75f, 12f, 10.5f, 2f),
        "M2.5 11l3.25-3.25L9 11l1.75-1.75L13.5 12",
        circle(10.75f, 5.75f, 1f),
    )

    /** A stopwatch: the tab holds the scroll timing. */
    val Playback = icon("Playback", circle(8f, 9f, 5f), "M8 6.75V9l1.5 1M6.5 1.75h3M8 1.75V4")

    val Interface = icon("Interface", roundRect(2f, 2.75f, 12f, 10.5f, 2f), "M6 2.75v10.5")

    /** A type specimen: the tab is the font and how the lyrics draw. */
    val Appearance = icon("Appearance", "M1.75 13 5.5 3l3.75 10M3.1 9.5h4.8", circle(12f, 10.75f, 2.25f), "M14.25 8.5V13")

    val Experiments = icon(
        "Experiments",
        "M6.25 2h3.5M6.75 2v4.25L3 12.25A1.25 1.25 0 0 0 4.1 14h7.8a1.25 1.25 0 0 0 1.1-1.75L9.25 6.25V2",
        "M4.75 10h6.5",
    )

    val Advanced = icon("Advanced", "M3 4.5l3 3.5-3 3.5M8 11.5h5")

    val About = icon("About", circle(8f, 8f, 5.75f), "M8 7.25v3.5", circle(8f, 5f, 0.4f))

    val Search = icon("Search", circle(6f, 6f, 4.5f), "M9.5 9.5L13 13", size = 14f)

    val Clear = icon("Clear", "M1 1L9 9M9 1L1 9", size = 10f)

    val Close = icon("Close", "M1.5 1.5l9 9M10.5 1.5l-9 9", size = 12f, stroke = 1.6f)

    val Chevron = icon("Chevron", "M3.5 2l3 3-3 3", size = 10f)

    val ExternalArrow = icon("ExternalArrow", "M2.5 7.5L7.5 2.5M7.5 2.5H3.5M7.5 2.5V6.5", size = 10f, stroke = 1.4f)

    /** A stroked icon [size] across from SVG path data. */
    private fun icon(name: String, vararg paths: String, size: Float = 16f, stroke: Float = 1.5f): ImageVector =
        ImageVector.Builder(name, size.dp, size.dp, size, size).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    stroke = SolidColor(Color.White),
                    strokeLineWidth = stroke,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0Z"

    private fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) =
        "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 ${-r} ${r}h${-(w - 2 * r)}" +
            "a$r $r 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}Z"
}
