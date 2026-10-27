package com.tx24.spicyplayer.desktop

import androidx.compose.ui.graphics.ImageBitmap
import com.tx24.spicyplayer.ui.nowplaying.NowPlaying
import com.tx24.spicyplayer.ui.nowplaying.NowPlayingState
import com.tx24.spicyplayer.ui.nowplaying.NowPlayingTrack
import com.tx24.spicyplayer.ui.nowplaying.TrackDirection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the shared controls ask a desktop player to do. */
enum class NowPlayingCommand { PLAY, PAUSE, NEXT, PREVIOUS, SEEK }

/**
 * [NowPlaying] for the desktop: whoever reads the operating system's media session calls [update]
 * with each report, and the shared screen sees it as the phone's does. The position comes from
 * [positionMs] (the lyric clock); commands go to the followed app through [control]. Not wired into
 * the window yet.
 */
class DesktopNowPlaying(
    private val positionMs: () -> Long,
    private val control: (app: String, command: NowPlayingCommand, positionMs: Long) -> Unit,
) : NowPlaying {
    private val mutableState = MutableStateFlow(NOTHING_PLAYING)
    override val state: StateFlow<NowPlayingState> = mutableState.asStateFlow()

    /** The skip the controls last asked for, so the cover change can slide the right way. */
    private var pendingDirection: TrackDirection? = null

    /**
     * The followed player's latest report; a null [app] means nothing is playing. A new track
     * takes the direction of the last skip pressed here, forward otherwise.
     */
    fun update(
        app: String?,
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        playing: Boolean,
        canSeek: Boolean = true,
        cover: ImageBitmap? = null,
    ) {
        if (app == null) {
            mutableState.value = NOTHING_PLAYING
            return
        }
        val before = mutableState.value
        val track = NowPlayingTrack(title, artist, album, durationMs)
        val changed = before.player != app || before.track.title != title || before.track.artists != artist
        val direction = if (changed) (pendingDirection ?: TrackDirection.Forward).also { pendingDirection = null } else before.direction
        mutableState.value = NowPlayingState(
            track = track,
            player = app,
            artwork = cover,
            isPlaying = playing,
            canSeek = canSeek,
            direction = direction,
        )
    }

    override fun positionMs(): Long = positionMs.invoke()

    override fun playPause() {
        val now = mutableState.value
        val app = now.player ?: return
        control(app, if (now.isPlaying) NowPlayingCommand.PAUSE else NowPlayingCommand.PLAY, 0L)
        // The button changes now; the player's report confirms it.
        mutableState.value = now.copy(isPlaying = !now.isPlaying)
    }

    override fun skipNext() = sendSkip(NowPlayingCommand.NEXT, TrackDirection.Forward)

    override fun skipPrevious() = sendSkip(NowPlayingCommand.PREVIOUS, TrackDirection.Backward)

    private fun sendSkip(command: NowPlayingCommand, direction: TrackDirection) {
        val app = mutableState.value.player ?: return
        pendingDirection = direction
        control(app, command, 0L)
    }

    override fun seekTo(positionMs: Long) {
        val now = mutableState.value
        val app = now.player ?: return
        if (!now.canSeek) return
        control(app, NowPlayingCommand.SEEK, positionMs.coerceAtLeast(0L))
    }

    /** Windows' media sessions publish no custom actions, so there are none to press. */
    override fun sendCustomAction(action: String) = Unit

    private companion object {
        val NOTHING_PLAYING = NowPlayingState(NowPlayingTrack("Nothing playing", "Start playback in another app"))
    }
}
