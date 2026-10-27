package com.tx24.spicyplayer.ui.nowplaying

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.StateFlow

/** The song the other app is playing, as the lyrics screen names it. */
data class NowPlayingTrack(
    val title: String,
    val artists: String,
    val album: String = "",
    /** 0 while the player hasn't said. */
    val durationMs: Long = 0L,
    /** The song's Spotify ID, picked by hand or read from the player, when known. */
    val spotifyId: String? = null,
)

/**
 * Everything the header and controls show about the player, apart from its position, which moves
 * every frame and is read through [PlaybackTransport.positionMs] instead.
 *
 * @param player the app being followed (a package name, an app ID); null with nothing playing.
 * @param artwork the cover the player sent as a picture; [artworkUri] when it sent an address.
 * @param direction which way the player last moved through its queue, for the cover change.
 * @param customActions the player's own buttons (shuffle, repeat, like, ...).
 */
data class NowPlayingState(
    val track: NowPlayingTrack,
    val player: String? = null,
    val artwork: ImageBitmap? = null,
    val artworkUri: String? = null,
    val isPlaying: Boolean = false,
    val canSeek: Boolean = false,
    val direction: TrackDirection = TrackDirection.Forward,
    val customActions: List<SessionCustomAction> = emptyList(),
) {
    val hasSession: Boolean get() = player != null

    /** What the song header shows. */
    val info: NowPlayingInfo
        get() = NowPlayingInfo(track.title, track.artists, track.album, artwork, artworkUri, direction, hasSession)
}

/** What the controls can ask of the player. */
interface PlaybackTransport {
    /**
     * Where the song is now, in ms. The timeline reads it every frame while drawing, so it is a
     * call rather than a state: a state would recompose the controls 60 times a second.
     */
    fun positionMs(): Long
    fun playPause()
    fun skipNext()
    fun skipPrevious()
    fun seekTo(positionMs: Long)
    /** Presses one of [NowPlayingState.customActions], by its [SessionCustomAction.action]. */
    fun sendCustomAction(action: String)
}

/** The player being followed: its state and its transport. Android and the desktop each adapt their own. */
interface NowPlaying : PlaybackTransport {
    val state: StateFlow<NowPlayingState>
}

/** Skips the way a cover swipe asks: [TrackDirection.Forward] to the next song, otherwise back. */
fun PlaybackTransport.skip(direction: TrackDirection) {
    if (direction == TrackDirection.Forward) skipNext() else skipPrevious()
}
