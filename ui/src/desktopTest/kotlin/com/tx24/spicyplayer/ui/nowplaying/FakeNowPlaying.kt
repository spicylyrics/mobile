package com.tx24.spicyplayer.ui.nowplaying

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A player for tests: it keeps its own position and play state, and writes down every command, so
 * a test can drive what the screen shows and check what the controls asked for.
 */
class FakeNowPlaying(initial: NowPlayingState = NowPlayingState(NowPlayingTrack("Song", "Artist"))) : NowPlaying {
    override val state = MutableStateFlow(initial)
    var position = 0L
    val commands = mutableListOf<String>()

    override fun positionMs(): Long = position

    override fun playPause() {
        commands += "playPause"
        state.value = state.value.copy(isPlaying = !state.value.isPlaying)
    }

    override fun skipNext() {
        commands += "next"
        state.value = state.value.copy(direction = TrackDirection.Forward)
    }

    override fun skipPrevious() {
        commands += "previous"
        state.value = state.value.copy(direction = TrackDirection.Backward)
    }

    override fun seekTo(positionMs: Long) {
        commands += "seek:$positionMs"
        if (state.value.canSeek) position = positionMs
    }

    override fun sendCustomAction(action: String) {
        commands += "action:$action"
    }
}
