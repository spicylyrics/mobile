package com.tx24.spicyplayer.lyrics.spicy.canvas

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

/*
 * The lyrics' scrollbar: a thin thumb down the view's right edge, shown while the mouse is over
 * the lyrics or holds it. In dp (times density): it is chrome, not lyric text.
 */

/** From the view's right edge to the thumb. */
private const val EDGE_DP = 4f
private const val WIDTH_DP = 6f
/** The track's distance from the view's top and bottom. */
private const val END_DP = 8f
/** The thumb is never shorter than this. */
internal const val SCROLLBAR_MIN_THUMB_DP = 25f
/** A press this close beside the thumb still takes it. */
private const val GRAB_SLACK_DP = 6f

/** How far the thumb moves from top to end in a view [heightPx] tall. */
internal fun scrollbarTravelPx(span: ScrollbarSpan, heightPx: Float, density: Float): Float {
    val track = heightPx - 2f * END_DP * density
    return (track - thumbLengthPx(span, track, density)).coerceAtLeast(0f)
}

private fun thumbLengthPx(span: ScrollbarSpan, trackPx: Float, density: Float): Float =
    maxOf(SCROLLBAR_MIN_THUMB_DP * density, trackPx * span.visible).coerceAtMost(trackPx)

/** The thumb in a view of [viewSize] for [span], or null when there is nothing to scroll or no room. */
internal fun scrollbarThumb(span: ScrollbarSpan?, viewSize: Size, density: Float): Rect? {
    if (span == null) return null
    val track = viewSize.height - 2f * END_DP * density
    val length = thumbLengthPx(span, track, density)
    if (track <= 0f || track - length <= 0f) return null
    val top = END_DP * density + span.position * (track - length)
    val right = viewSize.width - EDGE_DP * density
    return Rect(right - WIDTH_DP * density, top, right, top + length)
}

/** Whether a press at ([x], [y]) takes [thumb]: on it, or just beside it out to the view's edge. */
internal fun scrollbarHit(thumb: Rect, x: Float, y: Float, density: Float): Boolean {
    val slack = GRAB_SLACK_DP * density
    return x >= thumb.left - slack && x <= thumb.right + EDGE_DP * density && y >= thumb.top - slack && y <= thumb.bottom + slack
}
