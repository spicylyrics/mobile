package com.tx24.spicyplayer.ui.bigscreen

import com.tx24.spicyplayer.ui.nowplaying.CompactHeaderMetrics
import com.tx24.spicyplayer.ui.nowplaying.LandscapeMetrics
import com.tx24.spicyplayer.ui.nowplaying.LyricsBox

/**
 * Where everything on the (side layout) big-screen lyrics page goes for one window size, in px from the
 * page's top left. Written as CSS container units against the page (`cqw`/`cqh` are percentages
 * of its width and height), and against the cover for what sits on it.
 *
 * On the page the now-playing bar keeps a little closer to the edge than in cinema and
 * fullscreen (`:not(.Fullscreen)` scales its gap by .7 and the lyrics' start by .9).
 */
class BigScreenMetrics(
    val widthPx: Float,
    val heightPx: Float,
    val density: Float,
    val mode: ViewMode,
) {
    private val landscape = LandscapeMetrics(widthPx, heightPx, density)
    private val onPage = mode == ViewMode.Page

    /** `--NowBarWidth: calc(min(30cqw, 52cqh) * 1.1)`; the cover is as wide as the bar. */
    val coverSizePx: Float = landscape.coverSizePx

    /** `--NowBarRightSpacing: calc(var(--NowBarWidth) / 4)`. */
    private val innerSpacingPx = coverSizePx / 4f

    /** `--NowBarLeftSpacing: calc(50cqw - var(--NowBarWidth) - var(--NowBarRightSpacing))`, times .7 on the page. */
    val edgeSpacingPx: Float = (widthPx * 0.5f - coverSizePx - innerSpacingPx) * if (onPage) 0.7f else 1f

    /** `.NowBar { padding-top: calc(50cqh - var(--NowBarWidth) * .6) }`. */
    val coverTopPx: Float = landscape.coverTopPx

    /** `.MediaImageContainer { border-radius: 2cqh }` against the square cover. */
    val coverCornerPx: Float = coverSizePx * CompactHeaderMetrics.NOWBAR_CORNER_FRACTION

    /** The cover's left edge with the bar on the right or left, or centred when there are no lyrics beside it. */
    fun coverLeftPx(onRight: Boolean, lyricsHidden: Boolean = false): Float = when {
        lyricsHidden -> (widthPx - coverSizePx) / 2f
        onRight -> widthPx - coverSizePx - edgeSpacingPx
        else -> edgeSpacingPx
    }

    /** `.Header:not(:has(> .Timeline)) .Metadata { margin: 5cqw 0 0 }` under the cover. */
    val textTopPx: Float = coverTopPx + coverSizePx + coverSizePx * 0.05f

    /** The song name's and the artists' sizes and line heights, measured against the cover. */
    val titleSizeSp: Float = landscape.panel.titleSizeSp
    val titleLineHeightSp: Float = landscape.panel.titleLineHeightSp
    val artistsSizeSp: Float = landscape.panel.artistsSizeSp
    val artistsLineHeightSp: Float = landscape.panel.artistsLineHeightSp

    /** `--default-font-size` against the cover: the timeline's times. */
    val baseFontSizeSp: Float = landscape.baseFontSizeSp

    /** The lyrics' size the skeleton matches. */
    val lyricFontSizeSp: Float = landscape.lyricFontSizeSp

    /**
     * Where the lyrics go: next to the bar the text runs from `(50cqw - NowBarRightSpacing * .3)`
     * (times .9 on the page) to `NowBarLeftSpacing * .35` from the far edge; without it, from
     * 18cqw to 15.5cqw from the edges. The box's own 5% column inset puts the text there.
     */
    fun lyricsBox(withBar: Boolean, barOnRight: Boolean): LyricsBox {
        val textStart: Float
        val textEnd: Float
        if (withBar) {
            textStart = (widthPx * 0.5f - innerSpacingPx * 0.3f) * if (onPage) 0.9f else 1f
            textEnd = widthPx - edgeSpacingPx * 0.35f
        } else {
            textStart = widthPx * 0.18f
            textEnd = widthPx * (1f - 0.155f)
        }
        val inset = CompactHeaderMetrics.LYRICS_SIDE_INSET
        val width = (textEnd - textStart) / (1f - 2f * inset)
        val left = textStart - width * inset
        return LyricsBox(if (withBar && barOnRight) widthPx - left - width else left, width)
    }

    /**
     * The view controls on the page: `.ViewControls { height: 5cqh; top: 24px }`, each button
     * `--ViewControlSize: 110cqh` of that row.
     */
    val pageControlSizePx: Float = heightPx * 0.05f * 1.1f
    val pageControlsTopPx: Float = 24f * density

    /** On the cover: `.MediaContent .ViewControls { top: 7cqh; height: 9cqh }`. */
    val coverControlSizePx: Float = coverSizePx * 0.09f * 1.1f
    val coverControlsTopPx: Float = coverSizePx * 0.07f

    /** `.ViewControls { gap: 6px }`. */
    val controlGapPx: Float = 6f * density

    /**
     * The playback row on the cover: `.PlaybackControls { height: 10cqh; padding: 0 20cqh;
     * bottom: 6.5cqh }`, a skip button 12cqw wide and play/pause 9.25cqw.
     */
    val playbackRowHeightPx: Float = coverSizePx * 0.10f
    val playbackRowInsetPx: Float = coverSizePx * 0.20f
    val playbackRowBottomPx: Float = coverSizePx * 0.065f
    val skipWidthPx: Float = coverSizePx * 0.12f
    val playPauseWidthPx: Float = coverSizePx * 0.0925f

    /** `.MediaContent .Timeline { bottom: 0; padding: 5cqw 7cqw }`, its bar `height: 1.3cqh`. */
    val timelineInsetXPx: Float = coverSizePx * 0.07f
    val timelineInsetYPx: Float = coverSizePx * 0.05f
    val timelineBarHeightPx: Float = coverSizePx * 0.013f
}
