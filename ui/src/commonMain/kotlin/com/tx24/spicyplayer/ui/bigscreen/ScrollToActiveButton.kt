package com.tx24.spicyplayer.ui.bigscreen

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.tx24.spicyplayer.lyrics.spicy.canvas.ActiveLineDirection
import com.tx24.spicyplayer.lyrics.spicy.canvas.LyricsViewState
import com.tx24.spicyplayer.ui.components.GlassButton
import com.tx24.spicyplayer.ui.theme.SpicyColors

/** Whether the back-to-the-active-line button shows: the setting is on and the lyrics are scrolled away from it. */
fun showsScrollToActive(settingOn: Boolean, direction: ActiveLineDirection?): Boolean = settingOn && direction != null

/**
 * The round glass button that scrolls the lyrics back to the line being sung, at the bottom centre
 * of the lyrics, [bottomPx] above their bottom edge (clear of pinned credits). It shows while
 * [viewState] says the active line is scrolled out of view and [settingOn] (Scroll to Active
 * Button), its arrow pointing the line's way, and fades in over 0.2 s, growing from 0.92.
 */
@Composable
fun ScrollToActiveButton(viewState: LyricsViewState, settingOn: Boolean, bottomPx: () -> Float, modifier: Modifier = Modifier) {
    val direction = viewState.activeLineDirection
    val visible = showsScrollToActive(settingOn, direction)
    // Keeps its arrow while it fades out.
    var shownDirection by remember { mutableStateOf(ActiveLineDirection.Below) }
    if (visible && direction != null) shownDirection = direction
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(200), label = "scrollToActiveAlpha")
    val scale by animateFloatAsState(
        if (visible) 1f else 0.92f,
        tween(160, easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)),
        label = "scrollToActiveScale",
    )
    val above = shownDirection == ActiveLineDirection.Above
    if (alpha == 0f && !visible) return
    Box(modifier.fillMaxSize()) {
        GlassButton(
            onClick = { if (visible) viewState.scrollToActive() },
            contentDescription = if (above) "Scroll up to active lyric" else "Scroll down to active lyric",
            size = 48.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    translationY = -(bottomPx() + 24.dp.toPx())
                    this.alpha = alpha
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            val ink = SpicyColors.TextPrimary
            Canvas(Modifier.size(20.dp).graphicsLayer { rotationZ = if (above) 180f else 0f }) {
                val w = size.width
                val h = size.height
                val stroke = w * 0.12f
                drawLine(ink, Offset(w * 0.2f, h * 0.35f), Offset(w * 0.5f, h * 0.65f), stroke, StrokeCap.Round)
                drawLine(ink, Offset(w * 0.5f, h * 0.65f), Offset(w * 0.8f, h * 0.35f), stroke, StrokeCap.Round)
            }
        }
    }
}
