package com.tx24.spicyplayer

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.LifecycleEventObserver
import android.content.pm.ActivityInfo
import com.tx24.spicyplayer.ui.settings.LocalAppIcon
import com.tx24.spicyplayer.ui.settings.AppIcon
import com.tx24.spicyplayer.ui.settings.SettingsPage
import com.tx24.spicyplayer.ui.components.LocalPopupPalette
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.tx24.spicyplayer.lyrics.NoticeKind
import com.tx24.spicyplayer.ui.spotifysearch.SpotifySearchModal
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Public
import com.tx24.spicyplayer.network.data.SourceDisclosure
import com.tx24.spicyplayer.network.data.SourceDisclosures
import com.tx24.spicyplayer.network.data.LyricsSourceSettings
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil.Coil
import coil.ImageLoader
import com.tx24.spicyplayer.network.data.counted
import okhttp3.OkHttpClient
import com.tx24.spicyplayer.haptics.HapticPlayer
import com.tx24.spicyplayer.haptics.ProvideTouchHaptics
import com.tx24.spicyplayer.haptics.playMusicHaptics
import com.tx24.spicyplayer.ui.components.SpicyButton
import com.tx24.spicyplayer.ui.components.SpicyButtonStyle
import com.tx24.spicyplayer.ui.components.SpicyModal
import com.tx24.spicyplayer.ui.components.SpicyToastHost
import com.tx24.spicyplayer.ui.lyricsmanager.LyricsManagerModal
import com.tx24.spicyplayer.ui.components.SpicyModalActions
import com.tx24.spicyplayer.ui.components.SpicyModalButton
import com.tx24.spicyplayer.ui.components.SpicyModalGap
import com.tx24.spicyplayer.ui.components.SpicyModalHeading
import com.tx24.spicyplayer.ui.components.SpicyModalMessage
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.os.SystemClock
import android.view.Window
import android.view.WindowManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import kotlinx.coroutines.coroutineScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.tx24.spicyplayer.ui.components.LyricsSkeleton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import com.tx24.spicyplayer.lyrics.spicy.RenderConfig
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.width
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.spicy.canvas.LyricsLayoutMetrics
import com.tx24.spicyplayer.lyrics.spicy.canvas.LyricsView
import com.tx24.spicyplayer.lyrics.spicy.canvas.CoilLyricsImageLoader
import com.tx24.spicyplayer.lyrics.spicy.canvas.LocalLyricsImageLoader
import com.tx24.spicyplayer.lyrics.spicy.canvas.LyricsViewState
import com.tx24.spicyplayer.lyrics.spicy.canvas.ActiveLineDirection
import com.tx24.spicyplayer.lyrics.spicy.canvas.PinnedFooterMode
import com.tx24.spicyplayer.ui.profile.ProfileModal
import com.tx24.spicyplayer.lyrics.spicy.canvas.PinnedLyricsFooter
import com.tx24.spicyplayer.ui.components.GlassButton
import com.tx24.spicyplayer.ui.settings.LyricsFont
import com.tx24.spicyplayer.ui.settings.LyricsFontFile
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import com.tx24.spicyplayer.lyrics.spicy.models.Line
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsCredit
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsFooter
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsProvenance
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsType
import com.tx24.spicyplayer.lyrics.spicy.models.Word
import com.tx24.spicyplayer.lyrics.spicy.models.buildDisplayTimeline
import com.tx24.spicyplayer.playback.ExternalPlaybackViewModel
import com.tx24.spicyplayer.playback.PlayerLimit
import com.tx24.spicyplayer.playback.PlayerUiState
import com.tx24.spicyplayer.playback.SessionAccessService
import com.tx24.spicyplayer.translation.TranslationResult
import com.tx24.spicyplayer.translation.romanizationCredit
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalFocusManager
import com.tx24.spicyplayer.translation.translateButtonHelps
import com.tx24.spicyplayer.translation.TranslationMode
import com.tx24.spicyplayer.translation.TranslationPresentation
import com.tx24.spicyplayer.lyrics.spicy.romanization.RomanizationMode
import com.tx24.spicyplayer.network.data.providers.CustomLyricsSource
import com.tx24.spicyplayer.ui.background.SpicySessionBackground
import com.tx24.spicyplayer.ui.components.LocalBackdrop
import com.tx24.spicyplayer.ui.components.SpicyModalNotes
import com.tx24.spicyplayer.ui.components.SpicyVersionRow
import com.tx24.spicyplayer.ui.controls.KeyInput
import com.tx24.spicyplayer.ui.controls.shownCustomActions
import com.tx24.spicyplayer.ui.controls.CoverControls
import com.tx24.spicyplayer.ui.controls.PanelTimeline
import com.tx24.spicyplayer.ui.controls.StageControls
import com.tx24.spicyplayer.ui.settings.QuickScreen
import com.tx24.spicyplayer.ui.settings.QuickSettingsModal
import com.tx24.spicyplayer.ui.settings.rememberAndroidSettingsHost
import com.tx24.spicyplayer.ui.settings.settingsState
import com.tx24.spicyplayer.ui.components.LocalUiAnimations
import com.tx24.spicyplayer.ui.controls.LyricsControls
import com.tx24.spicyplayer.ui.controls.controlsClearancePx
import com.tx24.spicyplayer.ui.nowplaying.LandscapeMetrics
import com.tx24.spicyplayer.ui.settings.PanelSide
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntOffset
import com.tx24.spicyplayer.ui.controls.PlaybackControlsState
import com.tx24.spicyplayer.ui.nowplaying.CompactHeaderMetrics
import com.tx24.spicyplayer.ui.nowplaying.CompactNowPlayingHeader
import com.tx24.spicyplayer.ui.nowplaying.LyricsNotice
import com.tx24.spicyplayer.ui.nowplaying.NoticeActions
import com.tx24.spicyplayer.ui.nowplaying.androidNoticeActions
import com.tx24.spicyplayer.ui.settings.renderConfig
import com.tx24.spicyplayer.ui.settings.romanizes
import com.tx24.spicyplayer.ui.nowplaying.AndroidNowPlayingPlatform
import com.tx24.spicyplayer.ui.nowplaying.LocalNowPlayingPlatform
import com.tx24.spicyplayer.ui.nowplaying.PipSongCard
import com.tx24.spicyplayer.ui.nowplaying.PipDock
import com.tx24.spicyplayer.ui.settings.PipWithoutLyrics
import com.tx24.spicyplayer.ui.settings.debugReport
import com.tx24.spicyplayer.ui.nowplaying.MotionCoverQuery
import com.tx24.spicyplayer.ui.nowplaying.ViewModelNowPlaying
import com.tx24.spicyplayer.ui.nowplaying.rememberViewModelLyricsActions
import com.tx24.spicyplayer.ui.nowplaying.AndroidBigScreenPage
import com.tx24.spicyplayer.ui.bigscreen.isBigScreenWindow
import com.tx24.spicyplayer.ui.nowplaying.nowPlayingState
import com.tx24.spicyplayer.ui.nowplaying.skip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.tx24.spicyplayer.ui.nowplaying.CoverStage
import com.tx24.spicyplayer.ui.nowplaying.CoverStageDock
import com.tx24.spicyplayer.ui.nowplaying.SlidingAlignment
import com.tx24.spicyplayer.ui.nowplaying.StageMetadata
import com.tx24.spicyplayer.ui.nowplaying.STAGE_FADE_MS
import com.tx24.spicyplayer.ui.nowplaying.forgetMotionCover
import com.tx24.spicyplayer.ui.nowplaying.rememberMotionCoverUrl
import com.tx24.spicyplayer.ui.nowplaying.rememberMotionCover
import com.tx24.spicyplayer.ui.nowplaying.ReleaseYear
import com.tx24.spicyplayer.ui.settings.BackgroundType
import com.tx24.spicyplayer.ui.settings.ReleaseYearPosition
import com.tx24.spicyplayer.playback.TrackExtrasWanted
import com.tx24.spicyplayer.ui.nowplaying.CanvasLookup
import com.tx24.spicyplayer.ui.nowplaying.StageArtChoice
import com.tx24.spicyplayer.ui.nowplaying.StagePicture
import com.tx24.spicyplayer.ui.nowplaying.PortraitCanvas
import com.tx24.spicyplayer.ui.nowplaying.PortraitCoverStage
import com.tx24.spicyplayer.ui.nowplaying.MotionCovers
import com.tx24.spicyplayer.ui.controls.controlsContentStartPx
import com.tx24.spicyplayer.ui.nowplaying.PortraitCanvasDock
import com.tx24.spicyplayer.ui.background.CanvasAmbient
import androidx.compose.animation.core.spring
import com.tx24.spicyplayer.ui.settings.AppSettings
import com.tx24.spicyplayer.ui.settings.SystemBarsMode
import com.tx24.spicyplayer.lyrics.spicy.canvas.LyricsLayoutCalculator
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import com.tx24.spicyplayer.ui.settings.AdaptiveSettings
import com.tx24.spicyplayer.ui.intro.IntroScreen
import com.tx24.spicyplayer.ui.theme.SpicyColors
import com.tx24.spicyplayer.ui.theme.SpicyMotion
import com.tx24.spicyplayer.ui.theme.SpicySpacing
import com.tx24.spicyplayer.ui.theme.SpicyType
import com.tx24.spicyplayer.update.UpdateStatus
import com.tx24.spicyplayer.update.UpdateUiState
import com.tx24.spicyplayer.update.UpdateViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import android.content.BroadcastReceiver
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalUriHandler
import com.tx24.spicyplayer.lyrics.spicy.models.FooterLine
import com.tx24.spicyplayer.analytics.UsageCounter
import com.tx24.spicyplayer.analytics.UsageStats
import com.tx24.spicyplayer.analytics.lyricsOutcome
import com.tx24.spicyplayer.ui.controls.ControlsRevealGuard
import com.tx24.spicyplayer.ui.controls.LocalControlsRevealGuard
import androidx.compose.runtime.rememberUpdatedState
import com.tx24.spicyplayer.ui.lyrics.LyricsOverlays
import com.tx24.spicyplayer.ui.lyrics.LyricsPanel
import com.tx24.spicyplayer.ui.lyrics.LyricsScreen
import com.tx24.spicyplayer.ui.lyrics.LyricsScreenContext
import com.tx24.spicyplayer.ui.lyrics.LyricsScreenHost
import com.tx24.spicyplayer.ui.lyrics.LyricsScreenState
import com.tx24.spicyplayer.ui.lyrics.ScreenCounter
import com.tx24.spicyplayer.ui.lyrics.rememberRomanizationCredit
import com.tx24.spicyplayer.ui.nowplaying.NowPlayingInfo


/** Whether Coil's loader has been swapped for the counted one ([LyricsActivity.countImageDownloads]). */
private var imageLoaderSet = false

/**
 * The lyrics screen. Not named MainActivity: that name belongs to the launcher entry (an alias in
 * the manifest), so home-screen icons pinned before the icon choice existed keep working.
 */
class LyricsActivity : ComponentActivity() {
    private val playbackViewModel: ExternalPlaybackViewModel by viewModels()
    private val updateViewModel: UpdateViewModel by viewModels()
    private val keyInput by lazy {
        KeyInput().apply {
            onPlayPause = playbackViewModel::playPause
            onPrevious = playbackViewModel::skipPrevious
            onNext = playbackViewModel::skipNext
            onResync = playbackViewModel::resync
        }
    }

    // Activity's own public method: androidx.core's ComponentActivity re-declares it restricted,
    // which lint reads as this override reaching into the library.
    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean =
        keyInput.handle(event) || super.dispatchKeyEvent(event)

    /** In the small floating window: the lyrics screen then shows the lyrics and nothing else. */
    private var inPip by mutableStateOf(false)
    /** Whether the screen in front is one it's fine to shrink (not settings, a pop-up, the intro). */
    private val pipAllowed = MutableStateFlow(false)
    /** Another screen was opened for an answer ([startActivityForResult]): no window until it's back. */
    private val pipHeld = MutableStateFlow(false)
    private var pipAutoEnter = false
    /** The window's shape from Settings (`AppSettings.pipShape`). */
    private val pipRatio = MutableStateFlow(Rational(16, 9))
    /**
     * The last shape the system took. It refuses shape changes that come too often ("Too many PiP
     * aspect ratio change requests"), and then refuses entering with the new shape too, so the
     * window enters with this one until the new shape is taken.
     */
    private var pipAcceptedRatio = Rational(16, 9)
    private var pipRatioApplied = false
    private val pipSupported by lazy { PictureInPicture.supported(this) }

    // The window's own buttons come back as broadcasts.
    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                PictureInPicture.ACTION_PREVIOUS -> playbackViewModel.skipPrevious()
                PictureInPicture.ACTION_PLAY_PAUSE -> playbackViewModel.playPause()
                PictureInPicture.ACTION_NEXT -> playbackViewModel.skipNext()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.tx24.spicyplayer.playback.CrashLog.install(applicationContext)
        UsageStats.init(this)
        countImageDownloads()
        enableEdgeToEdge()
        applySystemBars()
        if (pipSupported) {
            // Recreated inside the window (the system's call, not a resize), no change is reported:
            // the window's own state says where it starts.
            inPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode
            addOnPictureInPictureModeChangedListener { inPip = it.isInPictureInPictureMode }
            // Keeps the window's play/pause icon and shape right, and whether leaving the app enters it.
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    combine(
                        pipAllowed,
                        pipHeld,
                        playbackViewModel.state.map { it.isPlaying to (it.sourcePackage != null) }.distinctUntilChanged(),
                        pipRatio,
                    ) { allowed, held, (playing, hasSession), ratio -> Triple(playing, allowed && !held && hasSession, ratio) }
                        .collectLatest { (playing, autoEnter, ratio) ->
                            pipAutoEnter = autoEnter
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return@collectLatest
                            // A refused shape is asked for again a little later, with the shape the
                            // system already took in the meantime.
                            while (true) {
                                val sent = runCatching {
                                    setPictureInPictureParams(PictureInPicture.params(this@LyricsActivity, playing, autoEnter, ratio))
                                }
                                if (sent.isSuccess) {
                                    pipAcceptedRatio = ratio
                                    break
                                }
                                runCatching {
                                    setPictureInPictureParams(PictureInPicture.params(this@LyricsActivity, playing, autoEnter, pipAcceptedRatio))
                                }
                                delay(PIP_RATIO_RETRY_MS)
                            }
                        }
                }
            }
        }
        setContent {
            val settings = remember { AppSettings(getSharedPreferences("ui", Context.MODE_PRIVATE)) }
            // A change in Settings shows or hides the bars at once.
            LaunchedEffect(settings.systemBars) { applySystemBars(settings.systemBars) }
            LaunchedEffect(settings.pipShape, settings.pipCustomRatio) {
                // Dragging the custom shape's slider changes it many times a second; only the shape
                // it settles on goes to the system, which refuses changes that come too often.
                if (pipRatioApplied) delay(PIP_RATIO_SETTLE_MS)
                pipRatioApplied = true
                pipRatio.value = PictureInPicture.ratio(settings.pipShape, settings.pipCustomRatio)
            }
            val context = LocalContext.current
            val lyricsImages = remember(context) { CoilLyricsImageLoader(context) }
            val nowPlayingPlatform = remember(context) { AndroidNowPlayingPlatform(context) }
            MaterialTheme(colorScheme = darkColorScheme()) {
                ProvideTouchHaptics(settings.touchHaptics) {
                    CompositionLocalProvider(
                        LocalLyricsImageLoader provides lyricsImages,
                        LocalNowPlayingPlatform provides nowPlayingPlatform,
                        LocalUiAnimations provides (settings.uiAnimations && !settings.lowPerformance),
                        LocalPopupPalette provides settings.popupTheme.palette,
                        LocalAppIcon provides settings.appIcon,
                    ) {
                    LyricsApp(
                        viewModel = playbackViewModel,
                        updater = updateViewModel,
                        settings = settings,
                        openNotificationAccess = {
                            // Many TVs have no notification access screen; there it's granted over adb.
                            try {
                                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            } catch (missing: android.content.ActivityNotFoundException) {
                                playbackViewModel.showMessage("This device has no notification access screen. Allow it from a computer: " +
                                    "adb shell cmd notification allow_listener $packageName/${SessionAccessService::class.java.name}")
                            }
                        },
                        inPip = inPip,
                        onEnterPip = ::enterPip.takeIf { pipSupported },
                        onPipAllowed = { pipAllowed.value = it && pipSupported },
                        keyInput = keyInput,
                    )
                    }
                }
            }
        }
    }

    /** Covers and artist images go through Coil's own client: counted like the rest. Once per process. */
    private fun countImageDownloads() {
        if (imageLoaderSet) return
        imageLoaderSet = true
        val context = applicationContext
        Coil.setImageLoader { ImageLoader.Builder(context).okHttpClient { OkHttpClient.Builder().counted().build() }.build() }
    }

    /** Shrinks the lyrics into the floating window (the button's way in). */
    private fun enterPip() {
        if (!pipSupported || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val playing = playbackViewModel.state.value.isPlaying
        runCatching { enterPictureInPictureMode(PictureInPicture.params(this, playing, pipAutoEnter, pipRatio.value)) }
            .recoverCatching { enterPictureInPictureMode(PictureInPicture.params(this, playing, pipAutoEnter, pipAcceptedRatio)) }
    }

    // Android 12+ enters the window on its own (see PictureInPicture.params); before that, here.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (pipAutoEnter && Build.VERSION.SDK_INT in Build.VERSION_CODES.O until Build.VERSION_CODES.S) enterPip()
    }

    override fun onStart() {
        super.onStart()
        if (pipSupported) {
            ContextCompat.registerReceiver(
                this, pipReceiver, IntentFilter().apply { PictureInPicture.actions.forEach(::addAction) },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
        playbackViewModel.setUiStarted(true)
        UsageStats.appOpened(this) { lookupSnapshot(playbackViewModel.state.value) }
    }

    override fun onResume() {
        super.onResume()
        pipHeld.value = false
        playbackViewModel.refresh()
    }

    /**
     * Opening a file picker (or anything else that answers back) is leaving the app as far as
     * Android's concerned, so the lyrics would shrink and the screen waiting for the answer would
     * close and miss it. The window stays shut until the app is back.
     */
    @Deprecated("Deprecated in Java")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        if (requestCode >= 0 && pipSupported) {
            pipHeld.value = true
            pipAutoEnter = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runCatching {
                    setPictureInPictureParams(
                        PictureInPicture.params(this, playbackViewModel.state.value.isPlaying, false, pipAcceptedRatio),
                    )
                }
            }
        }
        @Suppress("DEPRECATION")
        super.startActivityForResult(intent, requestCode, options)
    }

    override fun onStop() {
        if (pipSupported) runCatching { unregisterReceiver(pipReceiver) }
        playbackViewModel.setUiStarted(false)
        // Switching the launcher icon disables the alias this screen was opened through, and
        // Android closes it then: so the switch waits until the app is left. Also picks up a
        // restore that changed the setting.
        if (!isChangingConfigurations) {
            val icon = runCatching { AppIcon.valueOf(getSharedPreferences("ui", Context.MODE_PRIVATE).getString("appIcon", null)!!) }
                .getOrDefault(AppIcon.Purple)
            AppIcon.apply(this, icon)
        }
        super.onStop()
    }

    // Dialogs and the keyboard can bring the bars back; hide them again once the window is ours.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applySystemBars()
    }

    /** The System bars setting: a swipe from the edge shows a hidden bar for a moment over the page. */
    internal fun applySystemBars(mode: SystemBarsMode = AppSettings(getSharedPreferences("ui", Context.MODE_PRIVATE)).systemBars) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (mode.showStatusBar) show(WindowInsetsCompat.Type.statusBars()) else hide(WindowInsetsCompat.Type.statusBars())
            if (mode.showNavigationBar) show(WindowInsetsCompat.Type.navigationBars()) else hide(WindowInsetsCompat.Type.navigationBars())
        }
    }
}

/** The lookup settings for the daily settings snapshot: which sources are on and first, never a key. */
private fun lookupSnapshot(state: PlayerUiState): Map<String, Any> = buildMap {
    // A custom source's ID and name are the user's own: only that it's custom is said.
    val ranking = state.sourceRanking
    val on = ranking.origins.map { it.id }.filter { LyricsSourceSettings.isOn(ranking, it) }
    on.firstOrNull()?.let { put("first_source", if (CustomLyricsSource.isCustom(it)) "custom" else it) }
    put("sources_off", ranking.origins.size - on.size)
    // The built-in sources that are on, by id, in a fixed order; custom ones are only counted.
    put("sources_on", on.filterNot(CustomLyricsSource::isCustom).sorted().joinToString(","))
    put("blends_on", ranking.liveBlends.size)
    put("human_romanizations", state.humanRomanizations)
    // Which translation is set up; never what was translated.
    state.translationPreferences.let { translation ->
        put("translate_automatically", translation.automatic)
        put("translation_provider", translation.provider.name)
        put("translation_target", translation.targetLanguage)
        put("human_translations", translation.humanTranslations)
        put("translation_excluded", translation.excludedLanguages.size)
    }
    put("own_key", state.ownKeyHint != null)
    put("custom_sources", state.customSources.size)
}

/** Counts, for the usage report, what each song's lookup ended with and which pop-ups get opened. */
@Composable
private fun CountUsage(state: PlayerUiState, vararg opened: Pair<Boolean, String>) {
    val outcome = when (val lyrics = state.lyrics) {
        // A custom source counts as "Custom", never by the name the user gave it.
        is LyricsState.Ready -> lyricsOutcome(lyrics.lyricsType) to
            if (state.customSources.any { it.name == lyrics.provider }) "Custom" else lyrics.provider
        is LyricsState.Error -> lyricsOutcome(null) to null
        else -> null
    }
    val song = state.title to state.artist
    var counted by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(song, outcome) {
        if (outcome != null && counted != song) {
            counted = song
            UsageStats.countSong(outcome.first, outcome.second, state.sourcePackage)
        }
    }
    opened.forEach { (shown, counter) ->
        LaunchedEffect(shown) { if (shown) UsageStats.count(counter) }
    }
}

/** What the shared lyrics screen shows of the view model's state, with the cover already converted. */
fun PlayerUiState.lyricsScreenState(artwork: ImageBitmap?): LyricsScreenState = LyricsScreenState(
    nowPlaying = nowPlayingState(artwork),
    lyrics = lyrics,
    accessGranted = accessGranted,
    canvasUrl = canvasUrl,
    canvasPending = canvasPending,
    releaseYear = releaseYear,
    releaseYearPending = releaseYearPending,
    hasQueue = queue.isNotEmpty(),
    hasSongKey = localLyricsKey != null,
    translation = translation,
    translationEnabled = translationEnabled,
    translating = translating,
    translationFailed = translationFailed,
    // Going by what the phone guesses before anything is sent.
    translationHelps = translateButtonHelps(
        translationEnabled,
        lyricsLanguageOverride ?: lyricsLanguage ?: guessedLanguage,
        translationPreferences,
    ),
    humanRomanizedLines = humanRomanizedLines,
    playerNotice = limitNotice != null,
)

/**
 * The phone's lyrics screen: the shared [LyricsScreen] over the view model, with what only the
 * phone has around it (the floating window, the lock screen, haptics, its pop-ups and settings).
 */
@Composable
private fun LyricsApp(
    openNotificationAccess: () -> Unit,
    viewModel: ExternalPlaybackViewModel,
    updater: UpdateViewModel,
    settings: AppSettings,
    inPip: Boolean,
    /** Shrinks the lyrics into the floating window; null where the phone has none. */
    onEnterPip: (() -> Unit)?,
    onPipAllowed: (Boolean) -> Unit,
    keyInput: KeyInput = remember { KeyInput() },
) {
    val state by viewModel.state.collectAsState()
    // The player as the shared screen sees it: the cover converted once per picture, so the
    // header's cover loader only restarts when the player sends a new one.
    val transport = remember(viewModel) { ViewModelNowPlaying(viewModel) }
    val artwork = remember(state.artwork) { state.artwork?.asImageBitmap() }
    val context = LocalContext.current
    // The lyrics font: ours, the phone's, or the file picked in settings (ours again if it's gone).
    val lyricsFont = settings.lyricsFont
    val customFontFile = settings.customFontFile
    val fontFamily = remember(lyricsFont, customFontFile) {
        when (lyricsFont) {
            LyricsFont.Default -> null
            LyricsFont.System -> FontFamily.Default
            LyricsFont.Custom -> LyricsFontFile.family(context, customFontFile)
        }
    }
    SideEffect {
        LyricsLayoutCalculator.setFont(fontFamily, if (fontFamily == null) "" else "$lyricsFont:$customFontFile")
    }
    // New installs and cleared apps only: someone updating already knows their way around.
    val freshStart = remember {
        settings.startedEmpty || runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }
            .getOrNull()?.let { it.firstInstallTime == it.lastUpdateTime } == true
    }
    val showIntro = (freshStart || settings.introReplay) && !settings.introDone
    val answerUsageStats: (Boolean) -> Unit = { keep ->
        settings.usageStats = keep
        settings.usageStatsAsked = true
        UsageStats.onSettingChanged(context, keep)
        UsageStats.appOpened(context) { lookupSnapshot(viewModel.state.value) }
    }
    val activity = LocalActivity.current
    // Over the lock screen the lyrics can be read and played without unlocking. Anything opened
    // over them asks to unlock first and opens once unlocked; whatever was open when the phone
    // locked is closed before the lock screen shows it.
    LaunchedEffect(activity, settings.showOverLockScreen) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) activity?.setShowWhenLocked(settings.showOverLockScreen)
    }
    val overlays = remember(activity) {
        object : LyricsOverlays() {
            override fun whenAllowed(open: () -> Unit) {
                val keyguard = activity?.getSystemService(KeyguardManager::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && keyguard?.isKeyguardLocked == true) {
                    keyguard.requestDismissKeyguard(activity, object : KeyguardManager.KeyguardDismissCallback() {
                        override fun onDismissSucceeded() = open()
                    })
                } else open()
            }
        }
    }
    val lockLifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lockLifecycle, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START &&
                activity?.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
            ) {
                overlays.settings = false
                overlays.closePanels()
            }
        }
        lockLifecycle.addObserver(observer)
        onDispose { lockLifecycle.removeObserver(observer) }
    }
    val noticeActions = remember(viewModel, context) {
        androidNoticeActions(
            context = context,
            onRetry = { viewModel.loadLyrics(force = true) },
            report = { debugReport(context, viewModel.state.value) },
            onSearchSpotify = overlays::openSpotifySearch,
        )
    }
    CountUsage(
        state,
        overlays.settings to UsageCounter.SETTINGS,
        overlays.lyricsManager to UsageCounter.LYRICS_MANAGER,
        overlays.quickSettings to UsageCounter.QUICK_SETTINGS,
        overlays.spotifySearch to UsageCounter.SPOTIFY_SEARCH,
    )
    LaunchedEffect(Unit) { updater.checkOnLaunch(settings.includePrereleases) }
    val host = remember(viewModel, settings, updater, transport, keyInput, overlays) {
        AndroidLyricsHost(
            viewModel, settings, updater, transport, keyInput, overlays,
            onEnterPip = onEnterPip,
            onPipAllowed = onPipAllowed,
            openNotificationAccess = openNotificationAccess,
            answerUsageStats = answerUsageStats,
        )
    }
    val configuration = LocalConfiguration.current
    LyricsScreen(
        state = remember(state, artwork) { state.lyricsScreenState(artwork) },
        settings = settings,
        host = host,
        overlays = overlays,
        noticeActions = noticeActions,
        screenWidthDp = configuration.screenWidthDp,
        screenHeightDp = configuration.screenHeightDp,
        messages = viewModel.messages,
        showIntro = showIntro,
        inPip = inPip,
    )
}

/** The phone's side of the lyrics screen ([LyricsScreenHost]). */
private class AndroidLyricsHost(
    private val viewModel: ExternalPlaybackViewModel,
    private val settings: AppSettings,
    private val updater: UpdateViewModel,
    override val transport: ViewModelNowPlaying,
    override val keys: KeyInput,
    private val overlays: LyricsOverlays,
    private val onEnterPip: (() -> Unit)?,
    private val onPipAllowed: (Boolean) -> Unit,
    private val openNotificationAccess: () -> Unit,
    private val answerUsageStats: (Boolean) -> Unit,
) : LyricsScreenHost() {
    /** The settings pages left open, so coming back from the floating window lands on the same one. */
    private var settingsPages by mutableStateOf(emptyList<SettingsPage>())

    override fun lyricPositionMs(): Long = viewModel.currentLyricPositionMs()
    override fun seekToLyric(positionMs: Long) { viewModel.seekTo(positionMs) }
    override fun resync() { viewModel.resync() }
    override fun toggleTranslation() { viewModel.toggleTranslation() }
    override fun setCanvasWanted(wanted: Boolean) { viewModel.setCanvasWanted(wanted) }
    override fun canvasFailed(url: String) { viewModel.canvasFailed(url) }

    override fun count(counter: ScreenCounter) {
        UsageStats.count(
            when (counter) {
                ScreenCounter.ExpandButton -> UsageCounter.EXPAND_BUTTON
                ScreenCounter.Romanize -> UsageCounter.ROMANIZE
                ScreenCounter.PlayerAction -> UsageCounter.PLAYER_ACTION
            },
        )
    }

    override val pictureInPicture: Boolean get() = onEnterPip != null
    override fun enterPip() { onEnterPip?.invoke() }
    override fun setPipAllowed(allowed: Boolean) { onPipAllowed(allowed) }
    override val canRotate: Boolean get() = true
    // The moving background's beats and the animated cover it follows need Android 13's shaders.
    override val backgroundExtras: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    override fun isBigScreen(widthDp: Float, heightDp: Float): Boolean = isBigScreenWindow(widthDp, heightDp)

    @Composable
    override fun WindowEffects(keepAwake: Boolean, highRefreshRate: Boolean, ambient: Boolean) {
        val view = LocalView.current
        DisposableEffect(view, keepAwake) {
            view.keepScreenOn = keepAwake
            onDispose { view.keepScreenOn = false }
        }
        // 60 Hz unless asked otherwise: word sweeps and the background look the same, and a 90/120 Hz
        // screen would otherwise draw (and composite) half again or twice as many frames.
        val activity = LocalActivity.current
        DisposableEffect(activity, highRefreshRate) {
            val window = activity?.window
            window?.let { setPreferredRefreshRate(it, if (highRefreshRate) null else 60f) }
            onDispose { window?.let { setPreferredRefreshRate(it, null) } }
        }
        // Ambient mode: never brighter than the phone already is.
        val context = LocalContext.current
        DisposableEffect(activity, ambient) {
            val window = activity?.window
            if (ambient && window != null) {
                val system = runCatching {
                    Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
                }.getOrDefault(1f)
                window.attributes = window.attributes.apply { screenBrightness = minOf(system, AMBIENT_BRIGHTNESS) }
            }
            onDispose {
                window?.let { it.attributes = it.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } }
            }
        }
    }

    @Composable
    override fun RotationEffect(rotated: Boolean, onReset: () -> Unit) {
        // The rotate button turns the screen to landscape even with rotation locked, and back to
        // the phone's own rotation. Leaving the app hands rotation back too.
        val activity = LocalActivity.current
        LaunchedEffect(rotated) {
            activity?.requestedOrientation =
                if (rotated) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        val reset by rememberUpdatedState(onReset)
        val rotationLifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(rotationLifecycle) {
            val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) reset() }
            rotationLifecycle.addObserver(observer)
            onDispose { rotationLifecycle.removeObserver(observer) }
        }
    }

    @Composable
    override fun PlaybackEffects(beatReactive: Boolean) {
        val state by viewModel.state.collectAsState()
        val context = LocalContext.current
        // The loops picked to pause with the music hold their frame while it's paused.
        LaunchedEffect(state.isPlaying, settings.pauseWithMusic) {
            MotionCovers.pausedKinds = settings.pauseWithMusic
            MotionCovers.musicPaused = !state.isPlaying
        }
        LaunchedEffect(Unit) { viewModel.refresh() }
        val hapticPlayer = remember { HapticPlayer(context) }
        val musicHapticsOn = settings.musicHaptics && hapticPlayer.canPlayMusic
        val extrasWanted = TrackExtrasWanted(
            releaseYear = settings.releaseYearPosition != ReleaseYearPosition.Off,
            artistHeader = settings.backgroundType.usesArtistHeader,
            beats = beatReactive || musicHapticsOn,
        )
        LaunchedEffect(extrasWanted) { viewModel.setTrackExtrasWanted(extrasWanted) }
        // Vibrates with the beats while the song plays and the app is in front with the screen on.
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        // They rest while the delay is tapped out: tapping to them would only find their own timing.
        LaunchedEffect(musicHapticsOn, state.isPlaying, state.calibratingDelay) {
            if (!musicHapticsOn || !state.isPlaying || state.calibratingDelay) return@LaunchedEffect
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                playMusicHaptics(
                    hapticPlayer,
                    score = { viewModel.musicHaptics(settings.musicHapticsStyle) },
                    positionMs = viewModel::currentLyricPositionMs,
                    strength = { settings.musicHapticsStrength / 100f },
                )
            }
        }
    }

    @Composable
    override fun Background(
        animate: Boolean,
        beatReactive: Boolean,
        followMotionUrl: String?,
        onFollowMotionFailed: () -> Unit,
        motionCoverUrl: String?,
        onMotionCoverFailed: () -> Unit,
        modifier: Modifier,
    ) {
        val state by viewModel.state.collectAsState()
        val backgroundType = settings.backgroundType
        SpicySessionBackground(
            artwork = state.artwork,
            artworkUri = state.artworkUri,
            isPlaying = state.isPlaying,
            hasSession = state.sourcePackage != null,
            modifier = modifier,
            type = backgroundType,
            animate = animate,
            blurDp = settings.backgroundBlurFor(backgroundType),
            artistHeaderUrl = state.artistHeaderUrl,
            artistHeaderPending = state.artistHeaderPending,
            speed = if (beatReactive) viewModel::backgroundSpeed else null,
            refined = settings.refinedBackground,
            followMotionUrl = followMotionUrl,
            onFollowMotionFailed = onFollowMotionFailed,
            motionCoverUrl = motionCoverUrl,
            onMotionCoverFailed = onMotionCoverFailed,
        )
    }

    @Composable
    override fun CanvasAmbient(url: String, onFailed: () -> Unit, modifier: Modifier) {
        com.tx24.spicyplayer.ui.background.CanvasAmbient(url = url, onFailed = onFailed, modifier = modifier)
    }

    @Composable
    override fun PortraitCoverStage(info: NowPlayingInfo, picture: StagePicture, modifier: Modifier) {
        com.tx24.spicyplayer.ui.nowplaying.PortraitCoverStage(info = info, picture = picture, modifier = modifier)
    }

    @Composable
    override fun Pip(context: LyricsScreenContext) {
        val state by viewModel.state.collectAsState()
        PipLyrics(
            state, viewModel, settings, context.renderConfig, context.viewState, context.romanize,
            settings.lowPerformance, context.canvasUrl, context.artwork,
        )
    }

    @Composable
    override fun BigScreen(context: LyricsScreenContext) {
        val state by viewModel.state.collectAsState()
        val lowPerformance = settings.lowPerformance
        val backgroundType = settings.backgroundType
        AndroidBigScreenPage(
            nowPlaying = transport,
            sessionToken = remember(state.sourcePackage) { viewModel.followedSessionToken() },
            canSeek = state.canSeek,
            pictureInPicture = onEnterPip != null,
            lyrics = state.lyrics,
            renderConfig = context.renderConfig,
            noticeActions = context.noticeActions,
            controlsShown = context.controlsShown,
            onTouch = context.onTouch,
            onOpenSettings = overlays::openSettings,
            onEnterPip = { onEnterPip?.invoke() },
            romanized = context.romanize,
            onToggleRomanize = { settings.romanize = !settings.romanize },
            background = {
                SpicySessionBackground(
                    artwork = state.artwork,
                    artworkUri = state.artworkUri,
                    isPlaying = state.isPlaying,
                    hasSession = state.sourcePackage != null,
                    modifier = Modifier.fillMaxSize(),
                    type = backgroundType,
                    animate = !lowPerformance && !settings.staticBackground && !context.ambient,
                    blurDp = settings.backgroundBlurFor(backgroundType),
                    artistHeaderUrl = state.artistHeaderUrl,
                    artistHeaderPending = state.artistHeaderPending,
                    speed = if (context.beatReactive) viewModel::backgroundSpeed else null,
                    refined = settings.refinedBackground,
                )
            },
            lyricsView = { lyricsModifier, pageRomanize, activeLineTopPx, pageConfig ->
                LyricsPanel(
                    lyrics = state.lyrics,
                    translation = state.translation.takeIf { state.translationEnabled },
                    translationMode = settings.translationMode,
                    textCredits = settings.textCredits,
                    romanizationCredit = rememberRomanizationCredit(state.lyrics, state.humanRomanizedLines, pageRomanize, settings.textCredits),
                    romanizationMode = settings.romanizationMode,
                    currentTimeMs = viewModel::currentLyricPositionMs,
                    onSeek = viewModel::seekTo,
                    romanize = pageRomanize,
                    isPlaying = state.isPlaying,
                    activeLineTopPx = activeLineTopPx,
                    centredLiftPx = 0f,
                    noticeBottomPx = { 0f },
                    viewState = context.viewState,
                    pinnedFooter = PinnedFooterMode.Off,
                    maskBottomPx = { 0f },
                    config = pageConfig,
                    fontSizeScale = settings.lyricsScale(landscape = true),
                    noticeActions = context.noticeActions,
                    modifier = lyricsModifier,
                )
            },
            // The More menu's sources, translation and romanization, made only here.
            lyricsActions = rememberViewModelLyricsActions(viewModel, settings),
        )
    }

    @Composable
    override fun Overlays(context: LyricsScreenContext) {
        val state by viewModel.state.collectAsState()
        val backdrop = context.backdrop
        val lyricsViewState = context.viewState
        val showIntro = context.showIntro
        val update by updater.state.collectAsState()
        // Coming back from the system's settings refreshes the grant (onResume), which closes it.
        SpicyModal(
            visible = !state.accessGranted && !showIntro,
            onDismissRequest = null,
            backdrop = backdrop,
        ) {
            NotificationAccessMessage(openNotificationAccess)
        }

        // Once, after access is sorted, for new installs and updates alike; nothing is sent before it's answered.
        SpicyModal(
            visible = state.accessGranted && UsageStats.available && !settings.usageStatsAsked && !showIntro,
            onDismissRequest = null,
            backdrop = backdrop,
        ) {
            UsageStatsMessage(answerUsageStats)
        }

        // Once, after the update that switched sources off; after the stats question. Kept through
        // the closing animation.
        val switchedOff = SourceDisclosures.switchedOffNotice(state.switchedOffSources)
        var lastSwitchedOff by remember { mutableStateOf<Pair<String, String>?>(null) }
        switchedOff?.let { lastSwitchedOff = it }
        SpicyModal(
            visible = state.accessGranted && switchedOff != null && !showIntro &&
                (!UsageStats.available || settings.usageStatsAsked),
            onDismissRequest = viewModel::dismissSwitchedOff,
            backdrop = backdrop,
        ) {
            lastSwitchedOff?.let { (title, text) ->
                SwitchedOffMessage(
                    title, text,
                    onDismiss = viewModel::dismissSwitchedOff,
                    onOpenSettings = { viewModel.dismissSwitchedOff(); overlays.openSettings() },
                )
            }
        }

        QuickSettingsModal(
            visible = overlays.quickSettings,
            state = settingsState(state),
            host = rememberAndroidSettingsHost(state, viewModel, updater, settings),
            backdrop = backdrop,
            startOn = overlays.quickStart,
            onDismiss = { overlays.quickSettings = false },
        )

        // Kept through the closing animation, after the view model has cleared it.
        var lastLimit by remember { mutableStateOf<PlayerLimit?>(null) }
        state.limitNotice?.let { lastLimit = it }
        SpicyModal(
            visible = state.limitNotice != null,
            onDismissRequest = viewModel::dismissLimitNotice,
            backdrop = backdrop,
        ) {
            lastLimit?.let { SpotifyLimitMessage(it, viewModel::dismissLimitNotice) }
        }

        // Kept through the closing animation, after it's been answered.
        var lastProfile by remember { mutableStateOf<FooterLine?>(null) }
        lyricsViewState.profileRequest?.let { lastProfile = it }
        if (!settings.profilesInBrowser) {
            ProfileModal(lastProfile, lyricsViewState.profileRequest != null, backdrop, lyricsViewState::dismissProfile)
        } else SpicyModal(
            visible = lyricsViewState.profileRequest != null,
            onDismissRequest = lyricsViewState::dismissProfile,
            backdrop = backdrop,
        ) {
            lastProfile?.let { OpenProfileMessage(it, lyricsViewState::dismissProfile) }
        }

        // While something is open over settings, a remote's arrows stay in it.
        val overSettings = overlays.spotifySearch || overlays.lyricsManager
        if (overlays.settings) Box(
            Modifier
                .focusProperties { onEnter = { if (overSettings) cancelFocusChange() } }
                .focusGroup(),
        ) {
            AdaptiveSettings(
                state = state,
                viewModel = viewModel,
                updater = updater,
                settings = settings,
                backdrop = backdrop,
                contentPadding = context.padding,
                onClosed = { overlays.settings = false; settingsPages = emptyList() },
                onOpenLyricsManager = overlays::openLyricsManager,
                onOpenSpotifySearch = overlays::openSpotifySearch,
                onPeekChange = { overlays.settingsPeeking = it },
                restorePages = settingsPages,
                onPagesChange = { settingsPages = it },
            )
        }

        // Over settings, since it opens from there too.
        LyricsManagerModal(
            visible = overlays.lyricsManager,
            state = state,
            viewModel = viewModel,
            settings = settings,
            backdrop = backdrop,
            onDismissRequest = { overlays.lyricsManager = false },
        )

        // Over settings, since it opens from there too.
        SpotifySearchModal(
            visible = overlays.spotifySearch,
            state = state,
            viewModel = viewModel,
            backdrop = backdrop,
            onDismissRequest = { overlays.spotifySearch = false },
        )

        // Over settings, where sources are switched on. Kept through the closing animation.
        var lastDisclosure by remember { mutableStateOf<SourceDisclosure?>(null) }
        state.sourceDisclosure?.let { lastDisclosure = it }
        SpicyModal(
            visible = state.sourceDisclosure != null,
            onDismissRequest = viewModel::dismissSourceDisclosure,
            backdrop = backdrop,
        ) {
            lastDisclosure?.let { SourceDisclosureMessage(it, viewModel::acceptSourceDisclosure, viewModel::dismissSourceDisclosure) }
        }

        // Over settings, so a check from there answers in place.
        UpdatePopup(update, updater, backdrop)

        // Asked for from Settings: it goes, so the intro ends on the lyrics.
        LaunchedEffect(showIntro) { if (showIntro) overlays.settings = false }
        if (showIntro) {
            IntroScreen(
                settings = settings,
                accessGranted = state.accessGranted,
                askUsageStats = UsageStats.available && !settings.usageStatsAsked,
                openNotificationAccess = openNotificationAccess,
                onUsageStats = { keep -> answerUsageStats(keep) },
                onDone = {
                    settings.introDone = true
                    settings.introReplay = false
                },
            )
        }
    }
}


/**
 * The lyrics in the floating window: the background and the lyrics, centred, with the text sized
 * to the window (the 29.6sp minimum at 300x190dp, shrinking with it) so a few rows still fit.
 * With the lyrics turned off for the window, or none for the song and the song card picked for
 * that, the song's card stands in. A square or tall window names the song along its bottom.
 *
 * With the Canvas background, the song's [canvasUrl] is blurred behind the lyrics, as on the full screen.
 */
@Composable
private fun PipLyrics(
    state: PlayerUiState,
    viewModel: ExternalPlaybackViewModel,
    settings: AppSettings,
    config: RenderConfig,
    viewState: LyricsViewState,
    romanize: Boolean,
    lowPerformance: Boolean,
    canvasUrl: String?,
    /** [state]'s cover, converted once by the caller. */
    artwork: ImageBitmap?,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // A short window fits few rows, so the height limits the text as much as the width does.
        val windowScale = minOf(maxWidth.value / 300f, maxHeight.value / 190f).coerceIn(0.4f, 1f)
        // Unsynced lyrics are drawn smaller to fit a page of text, which in a window this small
        // leaves them unreadable: they get the synced lyrics' size here.
        val staticBoost = if ((state.lyrics as? LyricsState.Ready)?.lyricsType == LyricsType.Static)
            LyricsLayoutMetrics.baseFontSizeSp(LyricsType.Syllable, maxWidth.value) / LyricsLayoutMetrics.baseFontSizeSp(LyricsType.Static, maxWidth.value)
            else 1f
        // The floating window has its own lyrics size, apart from the full screen's.
        val fontScale = windowScale * (settings.pipLyricsSize / 100f) * staticBoost
        SpicySessionBackground(
            artwork = state.artwork,
            artworkUri = state.artworkUri,
            isPlaying = state.isPlaying,
            hasSession = state.sourcePackage != null,
            modifier = Modifier.fillMaxSize(),
            type = settings.backgroundType,
            animate = !lowPerformance && !settings.staticBackground,
            blurDp = settings.backgroundBlurFor(settings.backgroundType),
            artistHeaderUrl = state.artistHeaderUrl,
            artistHeaderPending = state.artistHeaderPending,
            refined = settings.refinedBackground,
        )
        // The Canvas background, as on the full screen.
        val canvasShown by animateFloatAsState(
            if (settings.canvasBackground && !lowPerformance && canvasUrl != null) 1f else 0f,
            tween(STAGE_FADE_MS, easing = StageEaseOut),
            label = "pipCanvas",
        )
        var shownCanvasUrl by remember { mutableStateOf<String?>(null) }
        canvasUrl?.let { shownCanvasUrl = it }
        shownCanvasUrl?.takeIf { canvasShown > 0f }?.let { url ->
            CanvasAmbient(url, onFailed = { viewModel.canvasFailed(url) }, modifier = Modifier.fillMaxSize().graphicsLayer { alpha = canvasShown })
        }
        val songCard = !settings.pipShowLyrics ||
            (state.lyrics is LyricsState.Error && settings.pipWithoutLyrics == PipWithoutLyrics.SongCard)
        if (songCard) {
            PipSongCard(state.title, state.artist, artwork, state.artworkUri, hasSession = state.sourcePackage != null)
            return@BoxWithConstraints
        }
        // Square or taller, the song docks along the bottom, under the lyrics.
        val dock = settings.pipDock && settings.pipAspect <= 1f
        Column(Modifier.fillMaxSize()) {
        LyricsPanel(
            lyrics = state.lyrics,
            translation = state.translation.takeIf { state.translationEnabled },
            translationMode = settings.translationMode,
            textCredits = settings.textCredits,
            romanizationCredit = rememberRomanizationCredit(state.lyrics, state.humanRomanizedLines, romanize, settings.textCredits),
            romanizationMode = settings.romanizationMode,
            currentTimeMs = viewModel::currentLyricPositionMs,
            onSeek = {},
            romanize = romanize,
            isPlaying = state.isPlaying,
            activeLineTopPx = null,
            centredLiftPx = 0f,
            noticeBottomPx = { 0f },
            viewState = viewState,
            pinnedFooter = PinnedFooterMode.Off,
            maskBottomPx = { 0f },
            config = config,
            fontSizeScale = fontScale,
            noticeActions = null,
            modifier = Modifier.fillMaxWidth().weight(1f),
            // The page's edge fade is 64dp deep: all of a window this small.
            maskScale = 0.25f,
        )
        if (dock) {
            PipDock(
                state.title, state.artist, viewModel::currentPositionMs, state.durationMs, state.isPlaying,
                cover = settings.pipDockCover, artwork = artwork, artworkUri = state.artworkUri,
                hasSession = state.sourcePackage != null,
                credit = viewState.shownFooter?.miniCredit()?.takeIf { settings.pinnedFooter != PinnedFooterMode.Off },
            )
        }
        }
    }
}

/** Asks for the display mode nearest [hz] at the current resolution, or the system's choice for null. */
private fun setPreferredRefreshRate(window: Window, hz: Float?) {
    @Suppress("DEPRECATION")
    val display = window.windowManager.defaultDisplay
    val current = display.mode
    val modeId = hz?.let {
        display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .minByOrNull { mode -> abs(mode.refreshRate - hz) }?.modeId
    } ?: 0
    if (window.attributes.preferredDisplayModeId != modeId) {
        window.attributes = window.attributes.apply { preferredDisplayModeId = modeId }
    }
}


/** Asks for the notification-listener grant, which is how Android hands out media sessions. */
@Composable
private fun NotificationAccessMessage(openNotificationAccess: () -> Unit) {
    SpicyModalMessage(
        title = "Allow notification access",
        description = "Spicy Lyrics Mobile reads the song playing in your music app, and controls it, through its media " +
            "notification. Android files that under notification access. You can turn it off again in system settings.",
        icon = {
            Icon(Icons.Rounded.NotificationsActive, null, Modifier.size(24.dp), tint = SpicyColors.TextPrimary)
        },
    )
    SpicyModalGap()
    SpicyModalButton("Open notification access", openNotificationAccess, style = SpicyButtonStyle.Primary, fill = true)
}

/** The one-time notice for the anonymous usage stats, which are on unless turned off here. */
@Composable
private fun UsageStatsMessage(onAnswer: (keepOn: Boolean) -> Unit) {
    SpicyModalMessage(
        title = "Anonymous usage stats",
        description = "Spicy Lyrics Mobile sends a small anonymous report a few times a day: the app and Android version, " +
            "your phone model and country, and which features you use. Never what you listen to. It shows what's " +
            "worth working on. You can change this in Settings → Advanced.",
        icon = { Icon(Icons.Rounded.Insights, null, Modifier.size(24.dp), tint = SpicyColors.TextPrimary) },
    )
    SpicyModalActions {
        SpicyModalButton("Turn off", { onAnswer(false) })
        SpicyModalButton("Keep on", { onAnswer(true) }, style = SpicyButtonStyle.Primary)
    }
}

@Composable
private fun SwitchedOffMessage(title: String, text: String, onDismiss: () -> Unit, onOpenSettings: () -> Unit) {
    SpicyModalMessage(
        title = title,
        description = text,
        icon = { Icon(Icons.Rounded.Info, null, Modifier.size(24.dp), tint = SpicyColors.TextPrimary) },
    )
    SpicyModalActions {
        SpicyModalButton("Open settings", onOpenSettings)
        SpicyModalButton("Got it", onDismiss, style = SpicyButtonStyle.Primary)
    }
}

@Composable
private fun SourceDisclosureMessage(disclosure: SourceDisclosure, onAccept: () -> Unit, onCancel: () -> Unit) {
    SpicyModalMessage(
        title = "Turn on ${disclosure.name}?",
        description = disclosure.description,
        icon = { Icon(Icons.Rounded.Public, null, Modifier.size(24.dp), tint = SpicyColors.TextPrimary) },
    )
    SpicyModalActions {
        SpicyModalButton("Cancel", onCancel)
        SpicyModalButton("Turn on", onAccept, style = SpicyButtonStyle.Primary)
    }
}

@Composable
private fun UpdatePopup(update: UpdateUiState, updater: UpdateViewModel, backdrop: HazeState?) {
    // Kept through the closing animation.
    var shown by remember { mutableStateOf(update.status) }
    if (update.prompt) shown = update.status
    val release = when (val status = shown) {
        is UpdateStatus.Available -> status.release
        is UpdateStatus.Downloading -> status.release
        is UpdateStatus.Installing -> status.release
        is UpdateStatus.Failed -> status.release
        else -> null
    }
    SpicyModal(
        visible = update.prompt && release != null,
        onDismissRequest = updater::later,
        backdrop = backdrop,
    ) {
        if (release == null) return@SpicyModal
        SpicyModalHeading("Update available", release.name.takeIf { it != release.tag && it != release.version })
        SpicyVersionRow(BuildConfig.VERSION_NAME, release.version)
        val status = when (val current = shown) {
            is UpdateStatus.Installing -> "Confirm the install in Android's prompt."
            is UpdateStatus.Failed -> current.message
            else -> null
        }
        (shown as? UpdateStatus.Downloading)?.let { DownloadProgress(it.progress) }
            ?: SpicyModalNotes(release.notes, status ?: "No notes for this version.".takeIf { release.notes.isEmpty() })
        SpicyModalGap()
        val busy = shown is UpdateStatus.Downloading || shown is UpdateStatus.Installing
        SpicyModalActions {
            if (!busy) SpicyModalButton("Skip this version", updater::skip, style = SpicyButtonStyle.Quiet)
            SpicyModalButton(if (busy) "Hide" else "Later", updater::later)
            if (!busy) {
                SpicyModalButton(if (shown is UpdateStatus.Failed) "Try again" else "Update", updater::install, style = SpicyButtonStyle.Primary)
            }
        }
    }
}

/** The download as a capsule filling with white, like the timeline, with the percentage under it. */
@Composable
private fun DownloadProgress(progress: Float) {
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), tween(200), label = "downloadProgress")
    Column(verticalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(SpicyColors.TintBg)
                .border(1.dp, SpicyColors.Hairline, CircleShape),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(shown).clip(CircleShape).background(Color.White))
        }
        Text(
            "Downloading… ${(progress * 100).roundToInt()}%",
            style = SpicyType.Footnote.copy(color = SpicyColors.TextSecondary, fontFeatureSettings = "tnum"),
        )
    }
}

/** Asks before leaving the app for a credit's profile, since the links sit where thumbs land. */
@Composable
private fun OpenProfileMessage(line: FooterLine, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val url = line.profileUrl ?: return
    SpicyModalMessage(
        title = line.name?.let { "Open @$it's profile?" } ?: "Open this profile?",
        description = url.removePrefix("https://").substringBefore('/'),
        icon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(24.dp), tint = SpicyColors.TextPrimary) },
    )
    SpicyModalGap()
    SpicyModalActions {
        SpicyModalButton("Cancel", onDismiss)
        SpicyModalButton("Open", {
            onDismiss()
            runCatching { uriHandler.openUri(url) }
        }, style = SpicyButtonStyle.Primary)
    }
}

@Composable
private fun SpotifyLimitMessage(limit: PlayerLimit, onDismiss: () -> Unit) {
    val (title, description) = when (limit) {
        PlayerLimit.Seek -> "Seeking needs Spotify Premium" to
            "Spotify Free doesn't let other apps move around in a song, so tapping a line or dragging " +
            "the timeline can't jump there. Playback carries on as normal."
        PlayerLimit.Previous -> "Going back needs Spotify Premium" to
            "Spotify Free doesn't allow going back to the previous song from here."
        PlayerLimit.Skips -> "Spotify isn't skipping" to
            "Spotify Free stops allowing skips for a while after a few. Skipping works again later."
    }
    SpicyModalMessage(
        title = title,
        description = description,
        icon = { Icon(Icons.Rounded.Info, null, Modifier.size(24.dp), tint = SpicyColors.TextPrimary) },
    )
    SpicyModalGap()
    SpicyModalButton("Got it", onDismiss, style = SpicyButtonStyle.Primary, fill = true)
}

private val StageEaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
/** Ambient mode's screen brightness, as a window override (0-1). */
private const val AMBIENT_BRIGHTNESS = 0.1f

/** How long the custom PiP shape has to stay put before it's sent. */
private const val PIP_RATIO_SETTLE_MS = 600L
/** How long to wait before asking again for a PiP shape the system refused. */
private const val PIP_RATIO_RETRY_MS = 5_000L