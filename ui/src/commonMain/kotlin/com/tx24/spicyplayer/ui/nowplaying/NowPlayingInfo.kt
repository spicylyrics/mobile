package com.tx24.spicyplayer.ui.nowplaying

import androidx.compose.ui.graphics.ImageBitmap

/**
 * What the song header shows. Kept apart from the player's view-model state so the header only
 * depends on this and survives the rework of the playback layer.
 *
 * @param artists the session's artist string as published; several are joined with ", ".
 * @param album the session's album name, empty when it has none.
 * @param artwork the cover the player sent as a picture; [artworkUri] when it sent an address instead.
 * The platform's [CoverLoader] turns either into what is drawn.
 * @param direction which way the player moved to reach this track, for the cover change.
 * @param hasSession false when no player is around, so the logo shows without waiting for a cover.
 */
data class NowPlayingInfo(
    val title: String,
    val artists: String,
    val album: String = "",
    val artwork: ImageBitmap? = null,
    val artworkUri: String? = null,
    val direction: TrackDirection = TrackDirection.Forward,
    val hasSession: Boolean = true,
)

/** Whether a track change went on through the queue or back to an earlier track. */
enum class TrackDirection { Forward, Backward }

/**
 * One of the player's own buttons, published in its PlaybackState: shuffle, repeat, like and the
 * like, as the system media controls show them. Players swap the icon to show the state.
 */
data class SessionCustomAction(val action: String, val name: String, val icon: ImageBitmap?)

/**
 * The release year beside the artists, [before] them or after. While [pending] (still looked up)
 * the header keeps room for it, so the artists don't jump when it arrives.
 */
data class ReleaseYear(
    val year: String?,
    val pending: Boolean,
    val before: Boolean,
)
