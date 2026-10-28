package com.tx24.spicyplayer.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import com.tx24.spicyplayer.ui.lyrics.LyricsOverlays
import com.tx24.spicyplayer.ui.lyrics.LyricsScreenContext
import com.tx24.spicyplayer.ui.lyrics.LyricsScreenHost
import com.tx24.spicyplayer.ui.nowplaying.PlaybackTransport
import com.tx24.spicyplayer.ui.settings.QuickScreen

/**
 * The desktop's side of the shared lyrics screen: the session's lyric clock, the desktop
 * background, and the mini window in place of the phone's floating one.
 */
class DesktopLyricsHost(
    override val transport: PlaybackTransport,
    private val positionMs: () -> Long,
    private val seek: (Long) -> Unit,
    private val openMiniWindow: () -> Unit,
    private val background: @Composable (animate: Boolean, modifier: Modifier) -> Unit,
) : LyricsScreenHost() {
    override fun lyricPositionMs(): Long = positionMs()
    override fun seekToLyric(positionMs: Long) { seek(positionMs) }

    override val pictureInPicture: Boolean get() = true
    override fun enterPip() { openMiniWindow() }

    @Composable
    override fun Background(
        animate: Boolean,
        beatReactive: Boolean,
        followMotionUrl: String?,
        onFollowMotionFailed: () -> Unit,
        motionCoverUrl: String?,
        onMotionCoverFailed: () -> Unit,
        modifier: Modifier,
    ) = background(animate, modifier)

    /** A credit's profile opens in the browser; there's no pop-up to ask first. */
    @Composable
    override fun Overlays(context: LyricsScreenContext) {
        val request = context.viewState.profileRequest
        val uriHandler = LocalUriHandler.current
        LaunchedEffect(request) {
            if (request == null) return@LaunchedEffect
            request.profileUrl?.let { url -> runCatching { uriHandler.openUri(url) } }
            context.viewState.dismissProfile()
        }
    }
}

/**
 * The screen's pop-ups as the window's own pages: settings and the Lyrics Manager open in the
 * window, and quick settings opens the sync delay, its closest page here. The rest have no page yet.
 */
class DesktopOverlays(private val nav: DesktopNav) : LyricsOverlays() {
    override fun openSettings() = nav.open(Destination.Settings)
    override fun openLyricsManager() = nav.open(Destination.LyricsManager)
    override fun openQuickSettings(start: QuickScreen) = when (start) {
        QuickScreen.Main -> nav.open(Destination.SyncDelay)
        QuickScreen.LyricsManager -> nav.open(Destination.LyricsManager)
        QuickScreen.Queue, QuickScreen.Source -> Unit
    }
    override fun openSpotifySearch() = Unit
}
