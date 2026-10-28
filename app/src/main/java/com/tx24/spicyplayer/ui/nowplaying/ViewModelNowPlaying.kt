package com.tx24.spicyplayer.ui.nowplaying

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.viewModelScope
import com.tx24.spicyplayer.playback.ExternalPlaybackViewModel
import com.tx24.spicyplayer.playback.PlayerUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * [NowPlaying] over the phone's [ExternalPlaybackViewModel]. The transport calls straight
 * through; [state] follows the view model's, and is only built while something collects it.
 */
class ViewModelNowPlaying(private val viewModel: ExternalPlaybackViewModel) : NowPlaying {
    override fun positionMs(): Long = viewModel.currentPositionMs()
    override fun playPause() { viewModel.playPause() }
    override fun skipNext() { viewModel.skipNext() }
    override fun skipPrevious() { viewModel.skipPrevious() }
    override fun seekTo(positionMs: Long) { viewModel.seekTo(positionMs) }
    override fun sendCustomAction(action: String) { viewModel.sendCustomAction(action) }

    override val state: StateFlow<NowPlayingState> by lazy {
        val covers = CoverConversion()
        viewModel.state
            .map { it.nowPlayingState(covers(it.artwork)) }
            .stateIn(
                viewModel.viewModelScope,
                SharingStarted.WhileSubscribed(),
                viewModel.state.value.let { it.nowPlayingState(covers(it.artwork)) },
            )
    }
}

/** What the shared screen needs of the view model's state, with the cover already converted. */
fun PlayerUiState.nowPlayingState(artwork: ImageBitmap?): NowPlayingState = NowPlayingState(
    track = NowPlayingTrack(title, artist, album, durationMs, spotifyId = manualSpotifyId ?: detectedSpotifyId),
    player = sourcePackage,
    artwork = artwork,
    artworkUri = artworkUri,
    isPlaying = isPlaying,
    canSeek = canSeek,
    direction = trackDirection,
    customActions = customActions,
)

/** One ImageBitmap per cover the player sends, so a state that keeps its cover keeps its picture. */
private class CoverConversion {
    private var bitmap: Bitmap? = null
    private var image: ImageBitmap? = null

    operator fun invoke(next: Bitmap?): ImageBitmap? {
        if (next !== bitmap) {
            bitmap = next
            image = next?.asImageBitmap()
        }
        return image
    }
}
