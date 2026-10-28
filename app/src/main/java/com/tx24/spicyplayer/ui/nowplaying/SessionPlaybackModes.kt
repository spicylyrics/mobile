package com.tx24.spicyplayer.ui.nowplaying

import android.media.session.MediaSession
import android.os.Handler
import android.os.Looper
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** The player's shuffle and repeat, each null where it can't set it or hasn't said where it stands. */
data class PlaybackModes(val shuffle: Boolean? = null, val repeat: RepeatState? = null)

/** A shuffle mode as on/off; null for an unknown one (the session hasn't connected, or doesn't say). */
fun shuffleOf(mode: Int): Boolean? = when (mode) {
    PlaybackStateCompat.SHUFFLE_MODE_NONE -> false
    PlaybackStateCompat.SHUFFLE_MODE_ALL, PlaybackStateCompat.SHUFFLE_MODE_GROUP -> true
    else -> null
}

/** A repeat mode as the page names it; a group repeats more than one song, so it reads as all. */
fun repeatOf(mode: Int): RepeatState? = when (mode) {
    PlaybackStateCompat.REPEAT_MODE_NONE -> RepeatState.Off
    PlaybackStateCompat.REPEAT_MODE_ONE -> RepeatState.One
    PlaybackStateCompat.REPEAT_MODE_ALL, PlaybackStateCompat.REPEAT_MODE_GROUP -> RepeatState.All
    else -> null
}

fun shuffleModeOf(on: Boolean): Int =
    if (on) PlaybackStateCompat.SHUFFLE_MODE_ALL else PlaybackStateCompat.SHUFFLE_MODE_NONE

fun repeatModeOf(state: RepeatState): Int = when (state) {
    RepeatState.Off -> PlaybackStateCompat.REPEAT_MODE_NONE
    RepeatState.All -> PlaybackStateCompat.REPEAT_MODE_ALL
    RepeatState.One -> PlaybackStateCompat.REPEAT_MODE_ONE
}

/**
 * What the page shows of a session with these [actions] and modes. A session reports a mode even
 * when it ignores requests to change it (the default is "none"), so each one counts only when the
 * session also says it takes the matching set action.
 */
fun playbackModesOf(actions: Long, shuffleMode: Int, repeatMode: Int): PlaybackModes = PlaybackModes(
    shuffle = shuffleOf(shuffleMode).takeIf { (actions and PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE) != 0L },
    repeat = repeatOf(repeatMode).takeIf { (actions and PlaybackStateCompat.ACTION_SET_REPEAT_MODE) != 0L },
)

/** [nowPlaying] with the session's shuffle and repeat added to its state, and the toggles sent to it. */
private class NowPlayingWithModes(
    private val base: NowPlaying,
    override val state: StateFlow<NowPlayingState>,
    private val controller: () -> MediaControllerCompat?,
) : NowPlaying by base {
    override fun setShuffle(on: Boolean) {
        controller()?.transportControls?.setShuffleMode(shuffleModeOf(on))
    }

    override fun setRepeat(mode: RepeatState) {
        controller()?.transportControls?.setRepeatMode(repeatModeOf(mode))
    }
}

/**
 * [nowPlaying] with shuffle and repeat read from the followed session ([token]), and the modes on
 * their own for the page's capabilities. The session is watched only while this is composed (the
 * big-screen page), and let go when it leaves or the session changes.
 */
@Composable
internal fun rememberSessionModes(nowPlaying: NowPlaying, token: MediaSession.Token?): Pair<NowPlaying, StateFlow<PlaybackModes>> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val modes = remember(token) { MutableStateFlow(PlaybackModes()) }
    val controller = remember(token) { arrayOfNulls<MediaControllerCompat>(1) }
    DisposableEffect(token) {
        val compat = token?.let { runCatching { MediaControllerCompat(context, MediaSessionCompat.Token.fromToken(it)) }.getOrNull() }
        if (compat == null) return@DisposableEffect onDispose {}
        fun refresh() {
            modes.value = playbackModesOf(compat.playbackState?.actions ?: 0L, compat.shuffleMode, compat.repeatMode)
        }
        val callback = object : MediaControllerCompat.Callback() {
            // The modes are only readable once the session has connected.
            override fun onSessionReady() = refresh()
            override fun onPlaybackStateChanged(state: PlaybackStateCompat?) = refresh()
            override fun onShuffleModeChanged(shuffleMode: Int) = refresh()
            override fun onRepeatModeChanged(repeatMode: Int) = refresh()
        }
        compat.registerCallback(callback, Handler(Looper.getMainLooper()))
        controller[0] = compat
        refresh()
        onDispose {
            compat.unregisterCallback(callback)
            controller[0] = null
            modes.value = PlaybackModes()
        }
    }
    val wrapped = remember(nowPlaying, modes) {
        val state = combine(nowPlaying.state, modes) { playing, m -> playing.copy(shuffle = m.shuffle, repeat = m.repeat) }
            .stateIn(scope, SharingStarted.WhileSubscribed(), nowPlaying.state.value)
        NowPlayingWithModes(nowPlaying, state, controller = { controller[0] })
    }
    return wrapped to modes
}
