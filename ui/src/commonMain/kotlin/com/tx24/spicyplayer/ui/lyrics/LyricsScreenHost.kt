package com.tx24.spicyplayer.ui.lyrics

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.spicy.RenderConfig
import com.tx24.spicyplayer.lyrics.spicy.canvas.LyricsViewState
import com.tx24.spicyplayer.translation.TranslationResult
import com.tx24.spicyplayer.ui.nowplaying.NoticeActions
import com.tx24.spicyplayer.ui.nowplaying.NowPlayingInfo
import com.tx24.spicyplayer.ui.nowplaying.NowPlayingState
import com.tx24.spicyplayer.ui.nowplaying.PlaybackTransport
import com.tx24.spicyplayer.ui.nowplaying.StagePicture
import com.tx24.spicyplayer.ui.settings.QuickScreen
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.StateFlow

/** What the lyrics screen shows of the player and its lyrics, apart from the settings. */
data class LyricsScreenState(
    /** The song and the player, as the header and controls show them. */
    val nowPlaying: NowPlayingState,
    val lyrics: LyricsState = LyricsState.Idle,
    /** The app may read the player; false shows the grant prompt (Android's notification access). */
    val accessGranted: Boolean = true,
    /** The song's Spotify Canvas video, when asked for ([LyricsScreenHost.setCanvasWanted]) and found. */
    val canvasUrl: String? = null,
    /** No canvas answer for this song yet, so the stage waits before falling back. */
    val canvasPending: Boolean = false,
    val releaseYear: String? = null,
    /** Still looking the year up: the header keeps room for it meanwhile. */
    val releaseYearPending: Boolean = false,
    /** The player shares a queue. */
    val hasQueue: Boolean = false,
    /** The song can be told apart for a source of its own (the Lyrics Manager's key). */
    val hasSongKey: Boolean = false,
    val translation: TranslationResult? = null,
    val translationEnabled: Boolean = false,
    /** A translation is being fetched for the lyrics on screen. */
    val translating: Boolean = false,
    /** The last translation failed: the button shows off, and a tap tries again. */
    val translationFailed: Boolean = false,
    /** Translating would help these lyrics (not already in the target language, not left out). */
    val translationHelps: Boolean = false,
    /** Lines of the lyrics on screen that carry a human romanization. */
    val humanRomanizedLines: Int = 0,
    /** A notice about the player is up over the lyrics (Spotify Free's limits). */
    val playerNotice: Boolean = false,
) {
    val isPlaying: Boolean get() = nowPlaying.isPlaying
    val durationMs: Long get() = nowPlaying.track.durationMs
    val hasSession: Boolean get() = nowPlaying.hasSession
}

/** What the screen counts for the usage report, where the platform keeps one. */
enum class ScreenCounter { ExpandButton, Romanize, PlayerAction }

/** Keys from a remote, keyboard or gamepad, which show the controls like a touch does. */
interface ScreenKeys {
    /** The time of the last key press that should show the controls; 0 before any. */
    val presses: StateFlow<Long>

    /** Asked by the keys whether the controls are up, so a hidden one only brings them back. */
    var controlsShown: () -> Boolean
}

/**
 * The pop-ups and pages over the lyrics screen that the screen opens and keeps clear of. The
 * platform draws them ([LyricsScreenHost.Overlays]); a platform without one of them opens its own
 * page instead by overriding its `open` function.
 */
open class LyricsOverlays {
    var settings by mutableStateOf(false)
    /** Settings are open but peeked through: the player's own settings button steps aside. */
    var settingsPeeking by mutableStateOf(false)
    var lyricsManager by mutableStateOf(false)
    var quickSettings by mutableStateOf(false)
    var quickStart by mutableStateOf(QuickScreen.Main)
    var spotifySearch by mutableStateOf(false)

    /** Runs [open] once it may: over Android's lock screen, after the phone is unlocked. */
    open fun whenAllowed(open: () -> Unit) = open()

    open fun openSettings() = whenAllowed { settings = true }
    open fun openLyricsManager() = whenAllowed { lyricsManager = true }
    open fun openQuickSettings(start: QuickScreen) = whenAllowed {
        quickStart = start
        quickSettings = true
    }
    open fun openSpotifySearch() = whenAllowed { spotifySearch = true }

    /** Closes the ones that don't wait for an answer (settings stay on their page). */
    fun closePanels() {
        lyricsManager = false
        quickSettings = false
        spotifySearch = false
    }
}

/** What the screen hands the platform's own parts of it: the floating window, the big-screen page, the pop-ups. */
class LyricsScreenContext(
    /** The screen's insets (the camera cutout, the keyboard in settings). */
    val padding: PaddingValues,
    /** What the glass and pop-ups blur; null where nothing can. */
    val backdrop: HazeState?,
    val viewState: LyricsViewState,
    val renderConfig: RenderConfig,
    val romanize: Boolean,
    /** The song's canvas, held while the next song's is looked up. */
    val canvasUrl: String?,
    val noticeActions: NoticeActions,
    val controlsShown: Boolean,
    /** A touch the platform's page saw: shows the controls and restarts their timer. */
    val onTouch: () -> Unit,
    /** Ambient mode is on: the page is dark and still. */
    val ambient: Boolean,
    /** The moving background follows the song's beats. */
    val beatReactive: Boolean,
    /** The song's cover as the header draws it. */
    val artwork: ImageBitmap?,
    /** The first-run intro is up: the prompts wait for it. */
    val showIntro: Boolean,
)

/**
 * What the lyrics screen needs from the platform around it: the player's lyric clock and the
 * lookup's switches, the background, and the parts only a phone has (the floating window,
 * haptics, the screen's refresh rate and brightness, the rotate button). Everything open does
 * nothing until a platform overrides it.
 */
abstract class LyricsScreenHost {
    /** The player's buttons. */
    abstract val transport: PlaybackTransport

    /** Where the lyrics are now, in ms, delays included. Read every frame. */
    abstract fun lyricPositionMs(): Long

    /** A tapped line or word: plays from [positionMs]. */
    abstract fun seekToLyric(positionMs: Long)

    open fun resync() {}
    open fun toggleTranslation() {}

    /** Whether anything on screen may show the song's Canvas, so it's only looked up then. */
    open fun setCanvasWanted(wanted: Boolean) {}

    /** The Canvas at [url] wouldn't play. */
    open fun canvasFailed(url: String) {}

    open fun count(counter: ScreenCounter) {}

    /** Keys that show the controls; null where there are none to listen to. */
    open val keys: ScreenKeys? get() = null

    /** The lyrics can shrink into a floating window ([enterPip]). */
    open val pictureInPicture: Boolean get() = false
    open fun enterPip() {}

    /** Whether leaving the app may shrink the lyrics into the floating window now. */
    open fun setPipAllowed(allowed: Boolean) {}

    /** The rotate button can turn the screen. */
    open val canRotate: Boolean get() = false

    /** The moving background can follow the beats and the animated cover (a shader that needs a newer Android). */
    open val backgroundExtras: Boolean get() = true

    /** A window wide enough for the big-screen page ([BigScreen]) instead of this one. */
    open fun isBigScreen(widthDp: Float, heightDp: Float): Boolean = false

    /** Keeps the screen awake while [keepAwake], at the refresh rate asked for, dimmed while [ambient]. */
    @Composable
    open fun WindowEffects(keepAwake: Boolean, highRefreshRate: Boolean, ambient: Boolean) {}

    /** Turns the screen to landscape while [rotated]; [onReset] when leaving the app hands rotation back. */
    @Composable
    open fun RotationEffect(rotated: Boolean, onReset: () -> Unit) {}

    /** What follows the song beyond the screen: its extras, the music haptics. */
    @Composable
    open fun PlaybackEffects(beatReactive: Boolean) {}

    /**
     * The page's background, filling [modifier]. [followMotionUrl] is the animated cover the moving
     * background follows, [motionCoverUrl] the Cover Art background's own loop; each reports a
     * stream that fails.
     */
    @Composable
    abstract fun Background(
        animate: Boolean,
        beatReactive: Boolean,
        followMotionUrl: String?,
        onFollowMotionFailed: () -> Unit,
        motionCoverUrl: String?,
        onMotionCoverFailed: () -> Unit,
        modifier: Modifier,
    )

    /** The Canvas at [url], blurred behind the page. */
    @Composable
    open fun CanvasAmbient(url: String, onFailed: () -> Unit, modifier: Modifier) {}

    /** The record's cover spread edge to edge in portrait, with the big cover up. */
    @Composable
    open fun PortraitCoverStage(info: NowPlayingInfo, picture: StagePicture, modifier: Modifier) {}

    /** The lyrics in the floating window, in place of the screen. */
    @Composable
    open fun Pip(context: LyricsScreenContext) {}

    /** The big-screen page, in place of the screen ([isBigScreen]). */
    @Composable
    open fun BigScreen(context: LyricsScreenContext) {}

    /** The pop-ups and pages over the screen ([LyricsOverlays]), the grant prompt, the toasts. */
    @Composable
    open fun Overlays(context: LyricsScreenContext) {}
}
