package com.tx24.spicyplayer.ui.bigscreen

import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.NoticeKind
import com.tx24.spicyplayer.ui.settings.CommonAppSettings
import com.tx24.spicyplayer.ui.settings.ViewControlsPosition

/**
 * The settings the big-screen page follows, read once per composition from [CommonAppSettings]
 * ([bigScreenOptions]). The defaults are the settings' own.
 *
 * @param idle when the controls fade in cinema and fullscreen ([IdleSettings]).
 * @param timelineOutsideCover in cinema and fullscreen, the timeline sits under the cover, above the
 * song, instead of on it with the playback row.
 * @param viewControlsAtBottom the page's view controls sit along its bottom, not its top.
 * @param showNoLyricsNotice a song without lyrics says why; off, cinema and fullscreen show the song alone.
 * @param hideHeader no now-playing bar on the page (and no song bar in the compact layout): just the lyrics.
 */
data class BigScreenOptions(
    val idle: IdleSettings = IdleSettings(),
    val timelineOutsideCover: Boolean = true,
    val viewControlsAtBottom: Boolean = false,
    val showNoLyricsNotice: Boolean = true,
    val hideHeader: Boolean = false,
)

/** The big-screen page's settings. */
fun CommonAppSettings.bigScreenOptions(): BigScreenOptions = BigScreenOptions(
    idle = IdleSettings(autoHide = autoHideControls, delayMs = controlsHideDelay.millis, hideWhilePaused = hideControlsWhilePaused),
    timelineOutsideCover = timelineOutsideCover,
    viewControlsAtBottom = viewControlsPosition == ViewControlsPosition.Bottom,
    showNoLyricsNotice = showNoLyricsNotice,
    hideHeader = hideHeader,
)

/**
 * Whether the lyrics side of the page stays empty and the cover moves to the middle: in cinema
 * and fullscreen, for a song that has no lyrics, with the notice turned off. Other notices
 * (offline, errors) still show.
 */
fun hidesLyrics(lyrics: LyricsState, immersive: Boolean, options: BigScreenOptions): Boolean =
    immersive && !options.showNoLyricsNotice && lyrics is LyricsState.Error && lyrics.kind == NoticeKind.NoLyrics

/**
 * Whether the page shows the now-playing bar: the view state wants it and, on the page itself,
 * the song header isn't hidden. In cinema and fullscreen the cover is the view, so it stays.
 */
fun showsNowBar(state: BigScreenState, options: BigScreenOptions): Boolean =
    state.nowBarOpen && !(options.hideHeader && !state.immersive)

/** The view controls the page offers: without the header there is no bar to open or move. */
fun pageViewControls(state: BigScreenState, capabilities: PlayerCapabilities, options: BigScreenOptions): List<ViewControl> =
    viewControls(state, capabilities).filterNot {
        options.hideHeader && !state.immersive && (it == ViewControl.NowBar || it == ViewControl.NowBarSide)
    }
