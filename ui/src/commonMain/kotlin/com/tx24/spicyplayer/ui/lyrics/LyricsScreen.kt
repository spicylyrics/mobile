package com.tx24.spicyplayer.ui.lyrics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.NoticeKind
import com.tx24.spicyplayer.lyrics.spicy.RenderConfig
import com.tx24.spicyplayer.lyrics.spicy.canvas.ActiveLineDirection
import com.tx24.spicyplayer.lyrics.spicy.canvas.LyricsLayoutMetrics
import com.tx24.spicyplayer.lyrics.spicy.canvas.LyricsView
import com.tx24.spicyplayer.lyrics.spicy.canvas.LyricsViewState
import com.tx24.spicyplayer.lyrics.spicy.canvas.PinnedFooterMode
import com.tx24.spicyplayer.lyrics.spicy.canvas.PinnedLyricsFooter
import com.tx24.spicyplayer.lyrics.spicy.models.Line
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsCredit
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsFooter
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsProvenance
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsType
import com.tx24.spicyplayer.lyrics.spicy.models.Word
import com.tx24.spicyplayer.lyrics.spicy.models.buildDisplayTimeline
import com.tx24.spicyplayer.lyrics.spicy.romanization.RomanizationMode
import com.tx24.spicyplayer.translation.TranslationMode
import com.tx24.spicyplayer.translation.TranslationPresentation
import com.tx24.spicyplayer.translation.TranslationResult
import com.tx24.spicyplayer.translation.romanizationCredit
import com.tx24.spicyplayer.ui.components.GlassButton
import com.tx24.spicyplayer.ui.components.LocalBackdrop
import com.tx24.spicyplayer.ui.components.LocalUiAnimations
import com.tx24.spicyplayer.ui.components.LyricsSkeleton
import com.tx24.spicyplayer.ui.components.SpicyToastHost
import com.tx24.spicyplayer.ui.controls.ControlsRevealGuard
import com.tx24.spicyplayer.ui.controls.CoverControls
import com.tx24.spicyplayer.ui.controls.LocalControlsRevealGuard
import com.tx24.spicyplayer.ui.controls.LyricsControls
import com.tx24.spicyplayer.ui.controls.PanelTimeline
import com.tx24.spicyplayer.ui.controls.PlaybackControlsState
import com.tx24.spicyplayer.ui.controls.StageControls
import com.tx24.spicyplayer.ui.controls.controlsClearancePx
import com.tx24.spicyplayer.ui.controls.controlsContentStartPx
import com.tx24.spicyplayer.ui.controls.shownCustomActions
import com.tx24.spicyplayer.ui.nowplaying.CanvasLookup
import com.tx24.spicyplayer.ui.nowplaying.CompactHeaderMetrics
import com.tx24.spicyplayer.ui.nowplaying.CompactNowPlayingHeader
import com.tx24.spicyplayer.ui.nowplaying.CoverStage
import com.tx24.spicyplayer.ui.nowplaying.CoverStageDock
import com.tx24.spicyplayer.ui.nowplaying.LandscapeMetrics
import com.tx24.spicyplayer.ui.nowplaying.LocalNowPlayingPlatform
import com.tx24.spicyplayer.ui.nowplaying.LyricsNotice
import com.tx24.spicyplayer.ui.nowplaying.MotionCoverQuery
import com.tx24.spicyplayer.ui.nowplaying.NoticeActions
import com.tx24.spicyplayer.ui.nowplaying.NowPlayingPlatform
import com.tx24.spicyplayer.ui.nowplaying.PortraitCanvas
import com.tx24.spicyplayer.ui.nowplaying.PortraitCanvasDock
import com.tx24.spicyplayer.ui.nowplaying.ReleaseYear
import com.tx24.spicyplayer.ui.nowplaying.STAGE_FADE_MS
import com.tx24.spicyplayer.ui.nowplaying.SlidingAlignment
import com.tx24.spicyplayer.ui.nowplaying.StageArtChoice
import com.tx24.spicyplayer.ui.nowplaying.StageMetadata
import com.tx24.spicyplayer.ui.nowplaying.StagePicture
import com.tx24.spicyplayer.ui.nowplaying.skip
import com.tx24.spicyplayer.ui.settings.BackgroundType
import com.tx24.spicyplayer.ui.settings.CommonAppSettings
import com.tx24.spicyplayer.ui.settings.PanelSide
import com.tx24.spicyplayer.ui.settings.QuickScreen
import com.tx24.spicyplayer.ui.settings.ReleaseYearPosition
import com.tx24.spicyplayer.ui.settings.renderConfig
import com.tx24.spicyplayer.ui.settings.romanizes
import com.tx24.spicyplayer.ui.theme.SpicyColors
import com.tx24.spicyplayer.ui.theme.SpicyMotion
import com.tx24.spicyplayer.ui.theme.SpicySpacing
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.TimeSource
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The lyrics screen: the song's header or cover panel, the word-synced lyrics, the controls, and
 * the cover stage and ambient mode, in portrait or (wider than tall) landscape. The platform
 * around it ([host]) supplies the lyric clock, the background and its own parts: on a phone the
 * floating window, haptics and pop-ups; on the desktop, its pages.
 *
 * @param screenWidthDp the window's width; with [screenHeightDp], it picks portrait or landscape.
 * @param messages short notices for the toast.
 * @param showIntro the first-run intro is up (the platform draws it): nothing else asks for attention.
 * @param inPip the screen is the small floating window, which shows the lyrics and nothing else.
 * @param lyricsViewState the lyrics' scroll, shared with the scroll-to-active button and the pinned credits.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsScreen(
    state: LyricsScreenState,
    settings: CommonAppSettings,
    host: LyricsScreenHost,
    overlays: LyricsOverlays,
    noticeActions: NoticeActions,
    screenWidthDp: Int,
    screenHeightDp: Int,
    messages: Flow<String>,
    showIntro: Boolean = false,
    inPip: Boolean = false,
    lyricsViewState: LyricsViewState = remember { LyricsViewState() },
) {
    val transport = host.transport
    val nowPlaying = state.nowPlaying
    val lowPerformance = settings.lowPerformance
    val hideHeader = settings.hideHeader
    val romanizationAvailable = (state.lyrics as? LyricsState.Ready)?.let { ready ->
        ready.lines.any { line -> line.words.any { it.romanized != null } }
    } == true
    val romanize = settings.romanizes(romanizationAvailable)
    val pinnedFooter = settings.pinnedFooter
    // The big cover: the button (or a tap on the cover) asks for it; the setting brings it up on
    // its own while a song has no lyrics, until it's put away for that song. Errors stay in view.
    var userExpanded by remember { mutableStateOf(false) }
    var dismissedForSong by remember(nowPlaying.track.title, nowPlaying.track.artists) { mutableStateOf(false) }
    val autoExpanded = settings.expandWithoutLyrics && (state.lyrics as? LyricsState.Error)?.kind == NoticeKind.NoLyrics &&
        !dismissedForSong
    val headerExpanded = userExpanded || autoExpanded
    val toggleExpanded = {
        if (headerExpanded) {
            userExpanded = false
            if (autoExpanded) dismissedForSong = true
        } else {
            userExpanded = true
        }
    }
    // The floating button's own way in, counted apart from a tap on the cover.
    val expandButtonTapped = {
        host.count(ScreenCounter.ExpandButton)
        toggleExpanded()
    }
    // The header expands over 0.4 s with CSS's `ease`.
    val expansion by animateFloatAsState(
        if (headerExpanded) 1f else 0f,
        tween(400, easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)),
        label = "headerExpansion",
    )
    // Landscape whenever the window is wider than tall: the song sits in a panel beside the lyrics.
    val landscape = screenWidthDp > screenHeightDp
    // In landscape the controls live on the cover, unless there's no panel and no big cover:
    // then they're the bottom bar, as in portrait.
    val controlsOnCover = landscape && (!hideHeader || headerExpanded)
    val bottomBar = !landscape || hideHeader
    // The rotate button turns the screen to landscape even with rotation locked, and back to the
    // phone's own rotation. Leaving the app hands rotation back too.
    var rotated by rememberSaveable { mutableStateOf(false) }
    host.RotationEffect(rotated, onReset = { rotated = false })
    // The panel slides to the other side over 0.4 s (`transition: left .4s`).
    val panelSide = animateFloatAsState(
        if (settings.panelSide == PanelSide.Right) 1f else 0f,
        tween(400, easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)),
        label = "panelSide",
    )
    // The cover stage: beside the lyrics, the cover fills its side of the screen; with the big
    // cover up it stands in the middle in its place. Only over the moving background, which
    // already takes its colours from the cover.
    val stageAllowed = settings.stageArt.isNotEmpty() && landscape && (!hideHeader || headerExpanded) && !inPip &&
        settings.backgroundType == BackgroundType.Default && !lowPerformance
    // Portrait, with the big cover up: what takes its place ([AppSettings.bigCoverArt]).
    val portraitAllowed = !landscape && !inPip && settings.bigCoverArt.isNotEmpty() && !lowPerformance
    // The song's Spotify Canvas is looked up only while something may show it: the stage, or the
    // big cover in portrait, where it's picked; the Canvas background.
    val stageCanvas = stageAllowed && StageArtChoice.wantsCanvas(settings.stageArt, lowPerformance)
    val portraitCanvas = portraitAllowed && StageArtChoice.wantsCanvas(settings.bigCoverArt, lowPerformance)
    val canvasBackground = settings.canvasBackground && !lowPerformance
    val canvasWanted = stageCanvas || portraitCanvas || canvasBackground
    LaunchedEffect(canvasWanted) { host.setCanvasWanted(canvasWanted) }
    // The song's canvas, held while the next song's is looked up, so it gives way to the next one
    // instead of vanishing with the song change.
    var heldCanvasUrl by remember { mutableStateOf<String?>(null) }
    if (!state.canvasPending || !canvasWanted) heldCanvasUrl = state.canvasUrl
    val nowPlayingPlatform = LocalNowPlayingPlatform.current
    // The panel's own loop: the same stream, so no second decode.
    val stageMotion = nowPlayingPlatform.rememberMotionCover(
        StageArtChoice.wantsAnimated(settings.stageArt, settings.animatedCover, lowPerformance),
        MotionCoverQuery(nowPlaying.track.artists, nowPlaying.track.album, nowPlaying.track.title),
    )
    // The first picked picture the song has; with none, no stage for this song.
    val stagePicture = StageArtChoice.pick(
        arts = settings.stageArt,
        animatedCover = settings.animatedCover,
        lowPerformance = lowPerformance,
        canvas = state.canvasUrl?.let { CanvasLookup.Found(it) }
            ?: if (state.canvasPending) CanvasLookup.Pending else CanvasLookup.None,
        motionUrl = stageMotion.url.takeIf { stageMotion.settled },
    )
    // The big cover's own pick, in portrait. The canvas is the one held through a song change.
    val portraitMotion = nowPlayingPlatform.rememberMotionCover(
        portraitAllowed && StageArtChoice.wantsAnimated(settings.bigCoverArt, settings.animatedCover, lowPerformance),
        MotionCoverQuery(nowPlaying.track.artists, nowPlaying.track.album, nowPlaying.track.title),
    )
    val portraitPicture = if (!portraitAllowed) null else StageArtChoice.pick(
        arts = settings.bigCoverArt,
        animatedCover = settings.animatedCover,
        lowPerformance = lowPerformance,
        canvas = heldCanvasUrl?.let { CanvasLookup.Found(it) }
            ?: if (state.canvasPending) CanvasLookup.Pending else CanvasLookup.None,
        motionUrl = portraitMotion.url.takeIf { portraitMotion.settled },
    )
    // Held while a new song's animated cover is looked up, so it doesn't drop out between songs.
    var shownPortrait by remember { mutableStateOf<StagePicture?>(null) }
    if (portraitMotion.settled || portraitPicture is StagePicture.Canvas || !portraitAllowed) shownPortrait = portraitPicture
    // While a new song's videos are still looked up, the stage holds what it shows (the last
    // song's loop keeps playing), then gives way to the answer, as Spotify's own player does.
    val stageSettled = !(stageCanvas && state.canvasPending) && stageMotion.settled
    var stageHasPicture by remember { mutableStateOf(false) }
    var shownStagePicture by remember { mutableStateOf<StagePicture>(StagePicture.Still) }
    if (stageSettled || !stageAllowed) {
        stageHasPicture = stagePicture != null
        // Fading out, the stage keeps what it last showed.
        if (stagePicture != null) shownStagePicture = stagePicture
    }
    val stageOn = stageAllowed && stageHasPicture
    // How far it has moved to the middle: with the big cover's expansion, or there throughout
    // without the panel, where it only shows while the big cover is up.
    val stageCentred = { if (hideHeader) 1f else expansion }
    val stageShown = animateFloatAsState(
        if (stageOn) 1f else 0f,
        tween(STAGE_FADE_MS, easing = StageEaseOut),
        label = "coverStage",
    )
    val stageVisible by remember { derivedStateOf { stageShown.value > 0f } }
    // Fully up, the panel's cover underneath stops playing its loop.
    val stageCovers by remember { derivedStateOf { stageShown.value == 1f } }
    // Swapping sides, the stage slides out toward the new side, flips while it's hidden, and slides
    // back in from the old one.
    var stageRight by remember { mutableStateOf(settings.panelSide == PanelSide.Right) }
    val stageSwapAlpha = remember { Animatable(1f) }
    val stageSwapX = remember { Animatable(0f) }
    val uiAnimations = LocalUiAnimations.current
    val swapDriftPx = screenWidthDp * LocalDensity.current.density * STAGE_SWAP_DRIFT
    LaunchedEffect(settings.panelSide, stageOn, uiAnimations) {
        val right = settings.panelSide == PanelSide.Right
        if (!stageOn || !uiAnimations) {
            stageRight = right
            stageSwapAlpha.snapTo(1f)
            stageSwapX.snapTo(0f)
            return@LaunchedEffect
        }
        val settle = tween<Float>(STAGE_SWAP_IN_MS, easing = StageEaseOut)
        // Pressed again before it flipped: it settles back where it was.
        if (right != stageRight) {
            val drift = swapDriftPx * (if (right) 1f else -1f)
            val out = tween<Float>(STAGE_SWAP_OUT_MS, easing = CubicBezierEasing(0.4f, 0f, 1f, 1f))
            coroutineScope {
                launch { stageSwapAlpha.animateTo(0f, out) }
                stageSwapX.animateTo(drift, out)
            }
            stageRight = right
            stageSwapX.snapTo(-drift)
        }
        coroutineScope {
            launch { stageSwapAlpha.animateTo(1f, settle) }
            stageSwapX.animateTo(0f, settle)
        }
    }
    // Where the cover is while the controls sit on it: only a touch there brings them up.
    val coverTouchArea = remember { CoverTouchArea() }
    // Elsewhere, where a touch leaves the controls alone: the cover's gestures, credit links.
    val revealGuard = remember { ControlsRevealGuard() }
    val renderConfig = settings.renderConfig(state.durationMs)
    val showSettings = overlays.settings
    val settingsPeeking = overlays.settingsPeeking
    val showLyricsManager = overlays.lyricsManager
    val showQuickSettings = overlays.quickSettings
    val showSpotifySearch = overlays.spotifySearch
    // Leaving the app only shrinks the lyrics when nothing else is half done in front of them.
    // Settings and the other panels don't count: the settings wait on their page for its return,
    // and the others close as it shrinks, like on the lock screen.
    val pipAllowed = settings.autoPip && state.accessGranted && !showIntro &&
        !state.playerNotice && lyricsViewState.profileRequest == null
    LaunchedEffect(pipAllowed) { host.setPipAllowed(pipAllowed) }
    LaunchedEffect(inPip) { if (inPip) overlays.closePanels() }
    // Where nothing can blur (Android before 12), the glass and pop-ups fall back to their solid
    // fills instead of showing the page through.
    val backdrop = remember { HazeState().takeIf { nowPlayingPlatform.blursLayers } }

    // Any touch shows the controls, and they fade after a few seconds without one.
    var controlsVisible by remember { mutableStateOf(true) }
    // Touch times go to the idle timer only: nothing in composition reads them, so a drag
    // (a touch event every frame) doesn't recompose the whole screen.
    val touches = remember { MutableStateFlow(0L) }
    // They stay while paused, and while the portrait big cover is up (it has nothing else to
    // show). On the landscape cover they always go, like the desktop page's once the mouse
    // rests: they'd keep the cover darkened.
    val hold = (!state.isPlaying && !controlsOnCover && !settings.hideControlsWhilePaused) ||
        // Over a Canvas there's something to see, so there they go as usual.
        (headerExpanded && !landscape && shownPortrait !is StagePicture.Canvas) || !settings.autoHideControls
    val idleMs = settings.controlsHideDelay.millis
    // A key (remote, keyboard, gamepad) counts as a touch; a screen over the lyrics counts as shown.
    val keyInput = host.keys
    SideEffect { keyInput?.controlsShown = { controlsVisible || overlays.settings || overlays.quickSettings || overlays.spotifySearch } }
    // While something is open over the lyrics screen, a remote's arrows stay in it: the screen
    // underneath takes no focus, and gives up what it had as the overlay opens.
    val overSettings = showSpotifySearch || showLyricsManager
    val overlayOpen = showSettings || showQuickSettings || overSettings ||
        lyricsViewState.profileRequest != null || state.playerNotice
    // Whichever is on top: one opened from settings takes the focus from them too.
    val topOverlay = listOf(showSpotifySearch, showLyricsManager, showQuickSettings, showSettings, overlayOpen).indexOf(true)
    val focusManager = LocalFocusManager.current
    LaunchedEffect(topOverlay) { if (topOverlay >= 0) focusManager.clearFocus(force = true) }
    if (keyInput != null) LaunchedEffect(keyInput) {
        keyInput.presses.collect { at ->
            if (at == 0L) return@collect
            controlsVisible = true
            touches.value = at
        }
    }
    LaunchedEffect(hold, idleMs) {
        if (hold) {
            controlsVisible = true
            return@LaunchedEffect
        }
        touches.collectLatest {
            delay(idleMs)
            controlsVisible = false
        }
    }
    // Ambient mode: left untouched while the music plays, the page goes dark and dim with only the
    // lyrics on it. Any touch or key brings it back (and does nothing else). Over the big cover in
    // portrait too: the cover fades with the header, leaving only the docked song, and comes back as it was.
    var ambient by remember { mutableStateOf(false) }
    val ambientAllowed = settings.ambientMode && state.isPlaying && !inPip && !overlayOpen && !(headerExpanded && landscape)
    LaunchedEffect(ambientAllowed, settings.ambientDelay) {
        if (!ambientAllowed) {
            ambient = false
            return@LaunchedEffect
        }
        touches.collectLatest {
            ambient = false
            delay(settings.ambientDelay.millis)
            ambient = true
        }
    }
    val ambientFade = animateFloatAsState(if (ambient) 1f else 0f, tween(if (ambient) AMBIENT_IN_MS else AMBIENT_OUT_MS), label = "ambient")
    val ambientDrawn by remember { derivedStateOf { ambientFade.value > 0f } }
    // Awake while the music plays, so the lyrics can be followed without touching the phone.
    host.WindowEffects(
        keepAwake = settings.keepScreenOn && state.isPlaying,
        highRefreshRate = settings.highRefreshRate,
        ambient = ambient,
    )

    val backgroundType = settings.backgroundType
    // Only what's on screen is looked up: the moving Kawarp background's beats, the artist's
    // header for the header backgrounds, the year where it shows.
    val beatReactive = backgroundType == BackgroundType.Default && settings.beatReactiveBackground &&
        !settings.staticBackground && !lowPerformance && host.backgroundExtras
    host.PlaybackEffects(beatReactive)

    // The bars are hidden, so this is only the camera cutout (and the keyboard in settings).
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        val context = LyricsScreenContext(
            padding = padding,
            backdrop = backdrop,
            viewState = lyricsViewState,
            renderConfig = renderConfig,
            romanize = romanize,
            canvasUrl = heldCanvasUrl,
            noticeActions = noticeActions,
            controlsShown = controlsVisible,
            onTouch = {
                controlsVisible = true
                touches.value = uptimeMs()
            },
            ambient = ambient,
            beatReactive = beatReactive,
            artwork = nowPlaying.artwork,
            showIntro = showIntro,
        )
        // The floating window has room for the lyrics and nothing else.
        if (inPip) {
            host.Pip(context)
            return@Scaffold
        }
        // A big window (a tablet held sideways, DeX, an unfolded foldable) gets the big-screen
        // page; phones and upright tablets keep the layout below. Follows resizes as they happen.
        if (host.isBigScreen(screenWidthDp.toFloat(), screenHeightDp.toFloat())) {
            host.BigScreen(context)
        } else CompositionLocalProvider(LocalControlsRevealGuard provides revealGuard) {
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { revealGuard.screen = it }
                .pointerInput(Unit) {
                    // Sees every touch on its way down without taking it from what's underneath.
                    awaitPointerEventScope {
                        // What the touch under way holds back, decided where its first finger lands.
                        var hold = ControlsRevealGuard.Hold.None
                        var downAt = Offset.Zero
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val onCover = coverTouchArea.contains
                            if (event.type == PointerEventType.Press && event.changes.count { it.pressed } == 1) {
                                downAt = event.changes.first { it.pressed }.position
                                // The cover that carries the controls brings them up; nothing else holds.
                                hold = if (onCover != null) ControlsRevealGuard.Hold.None else revealGuard.holdAt(downAt)
                            }
                            if (hold == ControlsRevealGuard.Hold.Taps &&
                                event.changes.any { (it.position - downAt).getDistance() > viewConfiguration.touchSlop }
                            ) {
                                hold = ControlsRevealGuard.Hold.None
                            }
                            val held = hold != ControlsRevealGuard.Hold.None
                            if (event.changes.none { it.pressed }) hold = ControlsRevealGuard.Hold.None
                            if (held) continue
                            if (onCover != null && event.changes.none { onCover(it.position) }) continue
                            controlsVisible = true
                            touches.value = uptimeMs()
                        }
                    }
                },
        ) {
            // The background fills the screen; everything on it keeps clear of the cutout.
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .focusProperties { onEnter = { if (overlayOpen) cancelFocusChange() } }
                    .focusGroup(),
            ) {
                // The header rises into the cutout's band (the camera is clear of the cover), so the
                // page starts at the top of the screen and only the bottom inset comes off.
                val topInsetPx = with(LocalDensity.current) { padding.calculateTopPadding().toPx() }
                val bottomInsetPx = with(LocalDensity.current) { padding.calculateBottomPadding().toPx() }
                val pageWidth = constraints.maxWidth.toFloat()
                val pageHeight = constraints.maxHeight.toFloat() - bottomInsetPx
                val density = LocalDensity.current.density
                val headerMetrics = remember(pageWidth, pageHeight, density, topInsetPx, settings.headerSize) {
                    CompactHeaderMetrics(
                        pageWidthPx = pageWidth,
                        pageHeightPx = pageHeight,
                        density = density,
                        lyricFontSizeSp = LyricsLayoutMetrics(pageWidth, density, LyricsType.Syllable, 1f).baseFontSizeSp,
                        topInsetPx = topInsetPx,
                        size = settings.headerSize,
                    )
                }
                val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
                val belowTop = androidx.compose.foundation.layout.PaddingValues(
                    start = padding.calculateStartPadding(layoutDirection),
                    end = padding.calculateEndPadding(layoutDirection),
                    bottom = padding.calculateBottomPadding(),
                )
                val startInsetPx = with(LocalDensity.current) { belowTop.calculateStartPadding(layoutDirection).toPx() }
                val endInsetPx = with(LocalDensity.current) { belowTop.calculateEndPadding(layoutDirection).toPx() }
                // Landscape: the page between the cutout's insets, which is where the header and
                // lyrics are placed (`belowTop`).
                val timelineOutside = settings.timelineOutsideCover
                val landscapeMetrics = remember(pageWidth, pageHeight, density, topInsetPx, startInsetPx, endInsetPx, timelineOutside) {
                    LandscapeMetrics(
                        pageWidthPx = pageWidth - startInsetPx - endInsetPx,
                        pageHeightPx = pageHeight - topInsetPx,
                        density = density,
                        topPx = topInsetPx,
                        timelineOutside = timelineOutside,
                    )
                }
                // The lyrics' box beside the panel (or across the page without one). Mid-swap they
                // fade out and come back on the other side, rather than crossing the panel.
                val lyricsBoxLeft = landscapeMetrics.lyricsBox(withPanel = !hideHeader, panelOnRight = false)
                val lyricsBoxRight = landscapeMetrics.lyricsBox(withPanel = !hideHeader, panelOnRight = true)
                val lyricsBoxWidth = with(LocalDensity.current) { lyricsBoxLeft.widthPx.toDp() }
                fun Modifier.inLyricsBox(): Modifier = if (!landscape) this else this
                    .offset { IntOffset((if (panelSide.value < 0.5f) lyricsBoxLeft else lyricsBoxRight).leftPx.roundToInt(), 0) }
                    .width(lyricsBoxWidth)
                val swapFade = { if (landscape && !hideHeader) abs(1f - 2f * panelSide.value) else 1f }
                // Landscape lyrics keep the desktop page's size against the cover rather than
                // following their box's width.
                val lyricsFontScale = settings.lyricsScale(landscape) * if (landscape) {
                    landscapeMetrics.lyricFontSizeSp /
                        LyricsLayoutMetrics(lyricsBoxLeft.widthPx, density, LyricsType.Syllable, 1f).baseFontSizeSp
                } else 1f
                coverTouchArea.contains = if (!controlsOnCover) null else if (stageOn && headerExpanded) { position ->
                    // The centred stage's solid part, around the big cover's place.
                    val left = startInsetPx + landscapeMetrics.centred.artLeftPx
                    position.x in left..(left + landscapeMetrics.coverSizePx)
                } else if (stageOn) { position ->
                    // The stage's solid part, from the screen's edge.
                    val solid = (if (stageRight) endInsetPx else startInsetPx) + landscapeMetrics.stageSolidPx
                    if (stageRight) position.x >= pageWidth - solid else position.x <= solid
                } else { position ->
                    val size = landscapeMetrics.coverSizePx
                    val t = if (hideHeader) 1f else expansion
                    val left = startInsetPx + landscapeMetrics.coverLeftPx(panelSide.value) +
                        (landscapeMetrics.centred.artLeftPx - landscapeMetrics.coverLeftPx(panelSide.value)) * t
                    val top = landscapeMetrics.coverTopPx
                    position.x in left..(left + size) && position.y in top..(top + size)
                }
                var controlsHeightPx by remember { mutableIntStateOf(0) }
                // Pinned credits sit just above the screen's edge, under the controls, and fade out
                // while the bottom controls show (they'd move under a finger otherwise). The lyrics
                // fade out above them.
                var pinnedHeightPx by remember { mutableIntStateOf(0) }
                val pinnedGapPx = with(LocalDensity.current) { PINNED_FOOTER_GAP.toPx() }
                val pinnedClearPx = with(LocalDensity.current) { 8.dp.toPx() }
                // While the controls show, the scroll-to-active button keeps clear of them.
                val controlsBottomPx by animateFloatAsState(
                    if (controlsVisible && bottomBar) controlsHeightPx.toFloat() else 0f,
                    tween(SpicyMotion.CONTROLS_FADE_MS),
                    label = "controlsBottom",
                )
                // A notice centres in the space they leave above them and the blurring part of
                // their shade, so its buttons stay sharp. It sits in what the shade blurs.
                val controlsClearPx = remember(controlsHeightPx, landscape, density) {
                    controlsClearancePx(controlsHeightPx, wide = landscape, density = density)
                }
                val noticeBottomPx by animateFloatAsState(
                    if (controlsVisible && bottomBar) controlsClearPx else 0f,
                    tween(SpicyMotion.CONTROLS_FADE_MS),
                    label = "noticeBottom",
                )
                // Out of the way under settings too: their glass would keep blurring behind it.
                val controlsTarget = controlsVisible && (!showSettings || settingsPeeking)
                // Quick to appear under the finger; a slow, soft fade when they time out.
                val controlsShown by animateFloatAsState(
                    if (controlsTarget) 1f else 0f,
                    if (controlsTarget) {
                        tween(CONTROLS_SHOW_MS, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
                    } else {
                        tween(CONTROLS_HIDE_MS, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f))
                    },
                    label = "controlsShown",
                )
                // Once faded out, the controls stop blurring: the shade's progressive blur and each
                // glass button would otherwise re-blur the whole page every frame, unseen.
                val controlsGone by remember { derivedStateOf { controlsShown == 0f } }
                val playbackControls = PlaybackControlsState(
                    credit = lyricsViewState.shownFooter?.miniCredit()?.takeIf { pinnedFooter != PinnedFooterMode.Off },
                    hideSettings = settingsPeeking,
                    // Hidden on a screen that's landscape on its own: there's nothing to turn.
                    onRotate = { rotated = !rotated }.takeIf { host.canRotate && settings.rotateButton && (rotated || !landscape) },
                    rotated = rotated,
                    isPlaying = state.isPlaying,
                    canSeek = nowPlaying.canSeek,
                    durationMs = state.durationMs,
                    positionMs = transport::positionMs,
                    onPlayPause = transport::playPause,
                    onPrevious = transport::skipPrevious,
                    onNext = transport::skipNext,
                    onSeek = transport::seekTo,
                    // Shuffle and repeat stay in the playback row; only the player's other actions are floating buttons.
                    customActions = shownCustomActions(nowPlaying.customActions, settings.playerButtons),
                    onCustomAction = { action ->
                        host.count(ScreenCounter.PlayerAction)
                        transport.sendCustomAction(action)
                    },
                    onResync = host::resync,
                    // Only where a translation would help: not on songs already in the target language
                    // or one left untranslated, going by what the phone guesses before anything is sent.
                    onTranslate = host::toggleTranslation.takeIf {
                        state.lyrics is LyricsState.Ready && settings.translateButton && state.translationHelps
                    },
                    translated = state.translationEnabled && !state.translationFailed,
                    translating = state.translating,
                    animate = uiAnimations,
                )
                val onEnterPipButton = host::enterPip.takeIf { host.pictureInPicture && settings.pipButton }
                val onOpenLyricsManager = if (settings.lyricsManagerButton) ({ overlays.openLyricsManager() }) else null
                val onOpenQuickSettings = if (settings.quickSettingsButton) ({ overlays.openQuickSettings(QuickScreen.Main) }) else null
                // The queue's own button opens quick settings on its queue screen.
                val onOpenQueue = if (settings.queueButton && state.hasQueue) ({ overlays.openQuickSettings(QuickScreen.Queue) }) else null
                // So does the source's, on its list of sources, while there's a song to choose for.
                val onOpenSource = if (settings.sourceButton && state.hasSongKey) ({ overlays.openQuickSettings(QuickScreen.Source) }) else null
                val romanizeButton = romanizationAvailable && settings.romanizeButton
                // Expanded, it's the only way back to the lyrics, so it stays.
                val expandButton = settings.expandButton || headerExpanded
                // The Cover Art background's animated cover; a stream that fails is forgotten, like the header's.
                val backgroundMotionQuery = MotionCoverQuery(nowPlaying.track.artists, nowPlaying.track.album, nowPlaying.track.title)
                val backgroundMotionUrl = rememberMotionCoverUrl(nowPlayingPlatform, 
                    backgroundType == BackgroundType.CoverArt && settings.animatedBackground && !lowPerformance,
                    backgroundMotionQuery,
                )
                var failedBackgroundMotionUrl by remember { mutableStateOf<String?>(null) }
                // The animated cover the moving background follows: the header's own stream, so
                // only while that setting is on.
                val followMotionUrl = rememberMotionCoverUrl(nowPlayingPlatform, 
                    backgroundType == BackgroundType.Default && settings.backgroundFollowsMotion && settings.animatedCover &&
                        !lowPerformance && !settings.staticBackground && host.backgroundExtras,
                    backgroundMotionQuery,
                )
                // While the stage shows a canvas, or the Canvas background has one, the page's
                // backdrop is that canvas, blurred; the moving background fades out under it and
                // holds still once it's hidden.
                val stageAmbientUrl = (shownStagePicture as? StagePicture.Canvas)?.url?.takeIf { stageOn && settings.canvasBackdrop }
                    ?: (shownPortrait as? StagePicture.Canvas)?.url?.takeIf { headerExpanded && settings.canvasBackdrop }
                // While the stage or the big cover shows the record's own cover, it melts into the
                // background's colours, which are the cover's: the Canvas background steps aside.
                val coverOnStage = (stageOn && shownStagePicture !is StagePicture.Canvas) ||
                    (headerExpanded && shownPortrait != null && shownPortrait !is StagePicture.Canvas)
                val backgroundAmbientUrl = heldCanvasUrl?.takeIf { canvasBackground && !coverOnStage }
                val ambientShown by animateFloatAsState(
                    if (stageAmbientUrl != null || backgroundAmbientUrl != null) 1f else 0f,
                    tween(STAGE_FADE_MS, easing = StageEaseOut),
                    label = "canvasAmbient",
                )
                // Fading out, it keeps what it showed.
                var ambientUrl by remember { mutableStateOf<String?>(null) }
                (stageAmbientUrl ?: backgroundAmbientUrl)?.let { ambientUrl = it }
                // Portrait, with the big cover up: the song's canvas fills the screen in its place,
                // or its cover spreads edge to edge.
                val immersiveShown by animateFloatAsState(
                    if (shownPortrait != null) 1f else 0f,
                    tween(STAGE_FADE_MS, easing = StageEaseOut),
                    label = "portraitCanvas",
                )
                // Fading out, it keeps what it showed.
                var immersivePicture by remember { mutableStateOf<StagePicture?>(null) }
                shownPortrait?.let { immersivePicture = it }
                val immersive = { immersiveShown * expansion }
                var failedFollowMotionUrl by remember { mutableStateOf<String?>(null) }
                val motionScope = rememberCoroutineScope()
                val stageInfo = nowPlaying.info
                // Everything the glass controls blur.
                Box(Modifier.fillMaxSize().then(backdrop?.let { Modifier.hazeSource(it) } ?: Modifier)) {
                    host.Background(
                        animate = !lowPerformance && !settings.staticBackground && ambientShown < 1f && !ambient,
                        beatReactive = beatReactive,
                        modifier = Modifier.fillMaxSize(),
                        followMotionUrl = followMotionUrl?.takeIf { it != failedFollowMotionUrl },
                        onFollowMotionFailed = {
                            failedFollowMotionUrl = followMotionUrl
                            motionScope.launch { nowPlayingPlatform.forgetMotionCover(backgroundMotionQuery) }
                        },
                        motionCoverUrl = backgroundMotionUrl?.takeIf { it != failedBackgroundMotionUrl },
                        onMotionCoverFailed = {
                            failedBackgroundMotionUrl = backgroundMotionUrl
                            motionScope.launch { nowPlayingPlatform.forgetMotionCover(backgroundMotionQuery) }
                        },
                    )
                    ambientUrl?.takeIf { ambientShown > 0f }?.let { url ->
                        host.CanvasAmbient(
                            url = url,
                            onFailed = { host.canvasFailed(url) },
                            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = ambientShown },
                        )
                    }
                    if (landscape && stageVisible) {
                        CoverStage(
                            info = stageInfo,
                            metrics = landscapeMetrics,
                            onRight = stageRight,
                            startInsetPx = startInsetPx,
                            endInsetPx = endInsetPx,
                            picture = shownStagePicture,
                            onCanvasFailed = host::canvasFailed,
                            shown = { stageShown.value },
                            controlsShown = { controlsShown },
                            swapAlpha = { stageSwapAlpha.value },
                            swapX = { stageSwapX.value },
                            centred = stageCentred,
                        )
                    }
                    if (!landscape) immersivePicture?.takeIf { immersive() > 0f }?.let { picture ->
                        if (picture is StagePicture.Canvas) {
                            PortraitCanvas(
                                url = picture.url,
                                onFailed = host::canvasFailed,
                                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = immersive() },
                            )
                        } else {
                            host.PortraitCoverStage(
                                info = stageInfo,
                                picture = picture,
                                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = immersive() },
                            )
                        }
                    }
                    if (ambientDrawn) Box(Modifier.fillMaxSize().graphicsLayer { alpha = ambientFade.value }.background(Color.Black))
                    // Without the header (or in ambient mode, where it has faded), the lyrics start
                    // just under the camera instead; in landscape they run the page's full height.
                    val lyricsFullPage = hideHeader || ambient
                    val lyricsTopPx = when {
                        landscape -> topInsetPx
                        lyricsFullPage -> headerMetrics.barTopPx
                        else -> headerMetrics.lyricsTopPx
                    }
                    // The box moves at once; drawn where it was, it glides to its new top.
                    val shownLyricsTopPx = animateFloatAsState(
                        lyricsTopPx,
                        tween(if (ambient) AMBIENT_IN_MS else AMBIENT_OUT_MS),
                        label = "lyricsTop",
                    )
                    Column(
                        Modifier
                            .padding(belowTop)
                            .inLyricsBox()
                            .fillMaxSize()
                            // Expanding, the lyrics fade out under the cover. In ambient mode a notice in
                            // their place (no lyrics, an error) fades too, leaving the docked song alone.
                            .graphicsLayer {
                                val notice = if (state.lyrics is LyricsState.Ready) 1f else 1f - ambientFade.value
                                alpha = (1f - expansion) * notice * swapFade()
                                translationY = shownLyricsTopPx.value - lyricsTopPx
                            },
                    ) {
                        Spacer(Modifier.height(with(LocalDensity.current) { lyricsTopPx.toDp() }))
                        // Landscape lyrics scroll like the full page: centred, 30px high against lyrics
                        // at their size here, on the whole page (the low bar comes and goes over it).
                        val centredLiftPx = if (landscape) {
                            val lyricSp = landscapeMetrics.lyricFontSizeSp * settings.lyricsScale(landscape = true)
                            30f * lyricSp / 56f * density
                        } else {
                            30f * headerMetrics.lyricsScale * headerMetrics.density + controlsHeightPx / 2f
                        }
                        LyricsPanel(
                            lyrics = state.lyrics,
                            translation = state.translation.takeIf { state.translationEnabled },
                            translationMode = settings.translationMode,
                            textCredits = settings.textCredits,
                            romanizationCredit = rememberRomanizationCredit(state.lyrics, state.humanRomanizedLines, romanize, settings.textCredits),
                            romanizationMode = settings.romanizationMode,
                            currentTimeMs = host::lyricPositionMs,
                            onSeek = host::seekToLyric,
                            romanize = romanize,
                            isPlaying = state.isPlaying,
                            // Compact scrolling keeps the active line near the top; without the
                            // header, full-page scrolling centres it, 30px high (`GetScrollType`), in the
                            // space above the controls (whether or not they are showing, so it holds still).
                            activeLineTopPx = headerMetrics.activeLineTopPx.takeUnless { lyricsFullPage || landscape },
                            centredLiftPx = centredLiftPx,
                            noticeBottomPx = { noticeBottomPx },
                            viewState = lyricsViewState,
                            // Pinned credits fade out under the controls, so while those show the
                            // credits go back to the end of the lyrics.
                            pinnedFooter = if (controlsVisible && bottomBar) PinnedFooterMode.Off else pinnedFooter,
                            maskBottomPx = { if (pinnedHeightPx > 0) pinnedGapPx + pinnedHeightPx + pinnedClearPx else 0f },
                            config = renderConfig,
                            fontSizeScale = lyricsFontScale,
                            noticeActions = noticeActions,
                            waitingDetail = state.waitingDetail,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Ambient mode: the lyrics sink into the black under the song docked at the bottom,
                    // and rise out of it at the top.
                    if (ambientDrawn && !landscape) Box(
                        Modifier.fillMaxSize().graphicsLayer { alpha = ambientFade.value }.drawBehind {
                            drawRect(
                                Brush.verticalGradient(
                                    0f to Color.Black,
                                    0.04f to Color.Black,
                                    0.2f to Color.Transparent,
                                    0.6f to Color.Transparent,
                                    0.82f to Color.Black,
                                    1f to Color.Black,
                                ),
                            )
                        },
                    )
                // Hidden lyrics take no touches (no seeking through the cover view).
                if (headerExpanded) {
                    Box(Modifier.fillMaxSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } })
                }
                // The stage's dock sits over the lyrics' layer: centred, it's where they are.
                if (landscape && stageVisible) {
                    // Along the stage's outer side beside the lyrics, gliding to the middle as it centres.
                    val stageAlign = { (if (stageRight) 1f else -1f) * (1f - stageCentred()) }
                    CoverStageDock(
                        metrics = landscapeMetrics,
                        onRight = stageRight,
                        startInsetPx = startInsetPx,
                        endInsetPx = endInsetPx,
                        shown = { stageShown.value },
                        swapAlpha = { stageSwapAlpha.value },
                        swapX = { stageSwapX.value },
                        centred = stageCentred,
                    ) {
                        // No glass blur here: the stage is part of what the glass blurs.
                        CompositionLocalProvider(LocalBackdrop provides null) {
                            StageControls(
                                controls = playbackControls,
                                romanizeAvailable = romanizeButton,
                                showResync = settings.resyncButton,
                                showExpand = expandButton,
                                romanized = romanize,
                                onToggleRomanize = {
                                    settings.romanize = !settings.romanize
                                    host.count(ScreenCounter.Romanize)
                                },
                                onOpenSettings = overlays::openSettings,
                                onOpenLyricsManager = onOpenLyricsManager,
                                onOpenQueue = onOpenQueue,
                                onOpenSource = onOpenSource,
                                onOpenQuickSettings = onOpenQuickSettings,
                                onEnterPip = onEnterPipButton,
                                expanded = headerExpanded,
                                onToggleExpanded = expandButtonTapped,
                                // Only beside the lyrics: centred, it stands where the big cover does.
                                onSwapSide = if (headerExpanded) null else ({
                                    settings.panelSide = if (settings.panelSide == PanelSide.Left) PanelSide.Right else PanelSide.Left
                                }),
                                shown = { controlsShown },
                                interactive = controlsVisible && stageOn,
                                onRight = stageRight,
                                align = stageAlign,
                                metadata = {
                                    val gutter = landscapeMetrics.coverSizePx * 0.04f
                                    val alignment = remember(stageRight) { SlidingAlignment(stageAlign) }
                                    StageMetadata(stageInfo, landscapeMetrics.panel, alignment, stageAlign, landscapeMetrics.coverSizePx - gutter * 2f)
                                },
                            )
                        }
                    }
                }
                // Hidden, the header only shows while the big cover is open or fading.
                val headerShowing by remember { derivedStateOf { expansion > 0f } }
                val expandedHeader = remember(headerMetrics, controlsHeightPx, landscape, landscapeMetrics) {
                    if (landscape) landscapeMetrics.centred else headerMetrics.expanded(pageHeight - controlsHeightPx)
                }
                CompactNowPlayingHeader(
                    info = nowPlaying.info,
                    releaseYear = settings.releaseYearPosition.takeIf { it != ReleaseYearPosition.Off }?.let { position ->
                        ReleaseYear(state.releaseYear, state.releaseYearPending, before = position == ReleaseYearPosition.Left)
                    },
                    metrics = headerMetrics,
                    modifier = Modifier.padding(belowTop).graphicsLayer { alpha = 1f - ambientFade.value },
                    expansion = { expansion },
                    expanded = expandedHeader,
                    isPlaying = state.isPlaying,
                    onPlayPause = transport::playPause,
                    onSkip = transport::skip,
                    animatedCover = settings.animatedCover && !lowPerformance && (!hideHeader || headerShowing) && !stageCovers,
                    hidden = hideHeader,
                    // Under the stage its gestures would sit over the dock.
                    interactive = (!hideHeader || headerExpanded) && !stageOn,
                    panel = landscapeMetrics.panel.takeIf { landscape },
                    panelLeft = { landscapeMetrics.coverLeftPx(panelSide.value) },
                    stage = { stageShown.value },
                    immersive = { if (landscape) 0f else immersive() },
                    coverOverlay = if (!controlsOnCover || controlsGone || stageOn) null else {
                        {
                            // No glass blur here: the cover is part of what the glass blurs.
                            CompositionLocalProvider(LocalBackdrop provides null) {
                                CoverControls(
                                    controls = playbackControls,
                                    romanizeAvailable = romanizeButton,
                                    showResync = settings.resyncButton,
                                    showExpand = expandButton,
                                    romanized = romanize,
                                    onToggleRomanize = {
                                        settings.romanize = !settings.romanize
                                        host.count(ScreenCounter.Romanize)
                                    },
                                    onOpenSettings = overlays::openSettings,
                                    onOpenLyricsManager = onOpenLyricsManager,
                                    onOpenQueue = onOpenQueue,
                                    onOpenSource = onOpenSource,
                                    onOpenQuickSettings = onOpenQuickSettings,
                                    onEnterPip = onEnterPipButton,
                                    expanded = headerExpanded,
                                    onToggleExpanded = expandButtonTapped,
                                    // Only beside the lyrics: the big cover sits in the middle.
                                    onSwapSide = if (hideHeader || headerExpanded) null else ({
                                        settings.panelSide = if (settings.panelSide == PanelSide.Left) PanelSide.Right else PanelSide.Left
                                    }),
                                    coverOnRight = settings.panelSide == PanelSide.Right,
                                    shown = { controlsShown },
                                    timeline = !timelineOutside,
                                )
                            }
                        }
                    },
                    belowCover = if (!timelineOutside || !controlsOnCover) null else {
                        {
                            PanelTimeline(
                                controls = playbackControls,
                                coverSizePx = landscapeMetrics.coverSizePx,
                                pageHeightPx = landscapeMetrics.pageHeightPx,
                                timeSizeSp = landscapeMetrics.baseFontSizeSp,
                            )
                        }
                    },
                )
                // The song docked over the portrait Canvas or cover, where the big cover's text was.
                // The controls' shade is off with the big cover up, so nothing blurs it here.
                if (!landscape && immersivePicture != null) {
                    // In line with the controls' rows, so the song sits over the timeline.
                    val gutter = controlsContentStartPx(pageWidth, density)
                    PortraitCanvasDock(
                        info = stageInfo,
                        sizes = expandedHeader,
                        controls = playbackControls,
                        controlsShown = { if (bottomBar) controlsShown else 0f },
                        controlsHeightPx = controlsHeightPx,
                        widthPx = pageWidth - gutter * 2f,
                        gutterPx = gutter,
                        bottomPx = gutter,
                        // Ambient mode docks the song itself, below; this one goes with the cover.
                        alpha = { immersive() * (1f - ambientFade.value) },
                    )
                }
                // Ambient mode: the song and its timeline docked at the bottom, as over the Canvas.
                if (!landscape && ambientDrawn) {
                    val gutter = controlsContentStartPx(pageWidth, density)
                    PortraitCanvasDock(
                        info = stageInfo,
                        sizes = expandedHeader,
                        controls = playbackControls,
                        controlsShown = { 0f },
                        controlsHeightPx = controlsHeightPx,
                        widthPx = pageWidth - gutter * 2f,
                        gutterPx = gutter,
                        bottomPx = gutter,
                        alpha = { ambientFade.value },
                    )
                }
                // The header sits in what the glass and the shade blur, so the shade blurs it
                // rather than painting the blurred page over it.
                }
                // In landscape, the bottom bar gives way to the controls on the big cover.
                val barFade = { if (landscape) 1f - expansion.coerceIn(0f, 1f) else 1f }
                val barGone by remember { derivedStateOf { controlsShown * barFade() == 0f } }
                if (bottomBar) CompositionLocalProvider(LocalBackdrop provides backdrop?.takeUnless { barGone || lowPerformance }) {
                    LyricsControls(
                        controls = playbackControls,
                        romanizeAvailable = romanizeButton,
                        showResync = settings.resyncButton,
                        showExpand = expandButton,
                        romanized = romanize,
                        onToggleRomanize = {
                            settings.romanize = !settings.romanize
                            host.count(ScreenCounter.Romanize)
                        },
                        onOpenSettings = overlays::openSettings,
                        onOpenLyricsManager = onOpenLyricsManager,
                        onOpenQueue = onOpenQueue,
                        onOpenSource = onOpenSource,
                        onOpenQuickSettings = onOpenQuickSettings,
                        onEnterPip = onEnterPipButton,
                        expanded = headerExpanded,
                        onToggleExpanded = expandButtonTapped,
                        interactive = controlsVisible && !controlsOnCover,
                        shown = { controlsShown * barFade() * (1f - ambientFade.value) },
                        // No lyrics behind them when expanded, so no shade over the cover.
                        shade = { 1f - expansion.coerceIn(0f, 1f) },
                        onControlsHeight = { controlsHeightPx = it },
                        wide = landscape,
                        insets = belowTop,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
                // Over the controls' shade rather than under it, so neither blurs nor covers them.
                PinnedLyricsFooter(
                    state = lyricsViewState,
                    mode = pinnedFooter,
                    fontSizeScale = lyricsFontScale,
                    // Faded out, they let the controls under them take the touch.
                    tappable = !(bottomBar && controlsTarget),
                    refined = settings.refinedCredits,
                    onHeight = { pinnedHeightPx = it },
                    modifier = Modifier
                        .align(if (landscape) Alignment.BottomStart else Alignment.BottomCenter)
                        .padding(belowTop)
                        .inLyricsBox()
                        .graphicsLayer {
                            translationY = -pinnedGapPx
                            val underControls = if (bottomBar) controlsShown * barFade() else 0f
                            // In portrait ambient mode the docked song has the bottom.
                            val underDock = if (landscape) 0f else ambientFade.value
                            alpha = (1f - expansion) * swapFade() * (1f - underControls) * (1f - underDock)
                        },
                )
                CompositionLocalProvider(LocalBackdrop provides backdrop?.takeUnless { lowPerformance }) {
                    ScrollToActiveButton(
                        direction = lyricsViewState.activeLineDirection.takeIf {
                            settings.showScrollToActive && !headerExpanded && (!showSettings || settingsPeeking)
                        },
                        onClick = lyricsViewState::scrollToActive,
                        topPx = when {
                            landscape -> topInsetPx
                            hideHeader -> headerMetrics.barTopPx
                            else -> headerMetrics.lyricsTopPx
                        },
                        bottomPx = { pinnedGapPx + maxOf(controlsBottomPx, if (pinnedHeightPx > 0) pinnedHeightPx + pinnedClearPx else 0f) },
                        modifier = Modifier.padding(belowTop).inLyricsBox(),
                    )
                }
                // The touch that wakes ambient mode only wakes it: no seek, no button. It wakes it
                // here too, since the page's own watcher lets some touches by.
                if (ambient) {
                    Box(
                        Modifier.fillMaxSize().pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent().changes.forEach { it.consume() }
                                    touches.value = uptimeMs()
                                }
                            }
                        },
                    )
                }
            }
        }
        }

        host.Overlays(context)

        SpicyToastHost(messages, Modifier.padding(padding).padding(top = SpicySpacing.S4))
    }
}

/** Tells the touch watcher whether a touch landed on the landscape cover; null: anywhere counts. */
private class CoverTouchArea {
    var contains: ((Offset) -> Boolean)? = null
}

/** Pinned credits' distance above the controls or the screen's edge (20px + 1.25rem). */
private val PINNED_FOOTER_GAP = 20.dp
/** The cover stage's side swap: out, then in, drifting this share of the screen's width. */
private const val STAGE_SWAP_OUT_MS = 160
private const val STAGE_SWAP_IN_MS = 420
private const val STAGE_SWAP_DRIFT = 0.025f
private val StageEaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
private const val CONTROLS_SHOW_MS = 350
private const val AMBIENT_IN_MS = 1_500
private const val AMBIENT_OUT_MS = 300
private const val CONTROLS_HIDE_MS = 700
private val CssEaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

/**
 * The arrow that takes the lyrics back to the line being sung once it has been scrolled out of
 * sight: at the top when the line is below, at the bottom (above the controls and pinned
 * credits) when it's above, pointing its way. It fades in over 0.2 s and grows from 0.92 with a
 * little overshoot.
 */
@Composable
private fun ScrollToActiveButton(
    direction: ActiveLineDirection?,
    onClick: () -> Unit,
    topPx: Float,
    bottomPx: () -> Float,
    modifier: Modifier = Modifier,
) {
    // Keeps its place and arrow while it fades out.
    var shownDirection by remember { mutableStateOf(ActiveLineDirection.Below) }
    if (direction != null) shownDirection = direction
    val visible = direction != null
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(200), label = "scrollToActiveAlpha")
    val scale by animateFloatAsState(
        if (visible) 1f else 0.92f,
        tween(160, easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)),
        label = "scrollToActiveScale",
    )
    val above = shownDirection == ActiveLineDirection.Above
    val rotation by animateFloatAsState(if (above) 180f else 0f, tween(200), label = "scrollToActiveArrow")
    if (alpha == 0f && !visible) return
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        // clamp(2rem, 8cqh, 6rem) from the lyrics' top or bottom edge.
        val edge = (maxHeight * 0.08f).coerceIn(32.dp, 96.dp)
        val top = with(density) { topPx.toDp() } + edge
        GlassButton(
            onClick = { if (visible) onClick() },
            contentDescription = if (above) "Scroll up to active lyric" else "Scroll down to active lyric",
            size = 48.dp,
            modifier = Modifier
                .align(if (above) Alignment.BottomEnd else Alignment.TopEnd)
                .padding(end = 24.dp, top = if (above) 0.dp else top)
                .graphicsLayer {
                    if (above) translationY = -(bottomPx() + edge.toPx())
                    this.alpha = alpha
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                null,
                Modifier.size(28.dp).graphicsLayer { rotationZ = rotation },
                tint = SpicyColors.TextPrimary,
            )
        }
    }
}

@Composable
fun LyricsPanel(
    lyrics: LyricsState,
    currentTimeMs: () -> Long,
    onSeek: (Long) -> Unit,
    romanize: Boolean,
    isPlaying: Boolean,
    activeLineTopPx: Float?,
    centredLiftPx: Float,
    noticeBottomPx: () -> Float,
    viewState: LyricsViewState,
    pinnedFooter: PinnedFooterMode,
    maskBottomPx: () -> Float,
    config: RenderConfig,
    fontSizeScale: Float,
    /** The notice's buttons; null draws the floating window's compact notice. */
    noticeActions: NoticeActions?,
    modifier: Modifier = Modifier,
    maskScale: Float = 1f,
    translation: TranslationResult? = null,
    translationMode: TranslationMode = TranslationMode.UnderLine,
    romanizationMode: RomanizationMode = RomanizationMode.Replace,
    /** Whether the credits say who translated the lyrics; [romanizationCredit] is already null when they don't. */
    textCredits: Boolean = true,
    romanizationCredit: String? = null,
    /** Under "Waiting for a song", such as why the player can't be read. */
    waitingDetail: String? = null,
) {
    // The loading skeleton: up as soon as the lookup starts, over whatever the panel
    // shows, fading in over 0.2s and out over 0.25s (ease-out).
    Box(modifier) {
        LyricsPanelContent(
            lyrics, currentTimeMs, onSeek, romanize, isPlaying, activeLineTopPx, centredLiftPx, noticeBottomPx,
            viewState, pinnedFooter, maskBottomPx, config, fontSizeScale, noticeActions, Modifier.fillMaxSize(),
            maskScale = maskScale,
            translation = translation,
            translationMode = translationMode,
            romanizationMode = romanizationMode,
            textCredits = textCredits,
            romanizationCredit = romanizationCredit,
            waitingDetail = waitingDetail,
        )
        AnimatedVisibility(
            visible = lyrics == LyricsState.Loading,
            enter = fadeIn(tween(200, easing = CssEaseOut)),
            exit = fadeOut(tween(250, easing = CssEaseOut)),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current.density
                val metrics = LyricsLayoutMetrics(constraints.maxWidth.toFloat(), density, LyricsType.Syllable, fontSizeScale)
                val column = metrics.contentSlot(hasDuet = false, isRtl = false, oppositeAligned = false)
                LyricsSkeleton(
                    lineSizeSp = metrics.baseFontSizeSp,
                    lineStartPx = column.startPx,
                    lineMaxWidthPx = column.widthPx,
                    topPx = activeLineTopPx ?: 0f,
                )
            }
        }
    }
}

@Composable
private fun LyricsPanelContent(
    lyrics: LyricsState,
    currentTimeMs: () -> Long,
    onSeek: (Long) -> Unit,
    romanize: Boolean,
    isPlaying: Boolean,
    activeLineTopPx: Float?,
    centredLiftPx: Float,
    noticeBottomPx: () -> Float,
    viewState: LyricsViewState,
    pinnedFooter: PinnedFooterMode,
    maskBottomPx: () -> Float,
    config: RenderConfig,
    fontSizeScale: Float,
    /** The notice's buttons; null draws the floating window's compact notice. */
    noticeActions: NoticeActions?,
    modifier: Modifier = Modifier,
    maskScale: Float = 1f,
    translation: TranslationResult? = null,
    translationMode: TranslationMode = TranslationMode.UnderLine,
    romanizationMode: RomanizationMode = RomanizationMode.Replace,
    textCredits: Boolean = true,
    romanizationCredit: String? = null,
    waitingDetail: String? = null,
) {
    when (lyrics) {
        LyricsState.Idle -> LyricsNotice("Waiting for a song", modifier, body = waitingDetail, bottomPx = noticeBottomPx, compact = noticeActions == null, animate = LocalUiAnimations.current)
        LyricsState.Loading -> Unit
        is LyricsState.Error -> LyricsNotice(lyrics, noticeActions, noticeBottomPx, modifier, animate = LocalUiAnimations.current)
        is LyricsState.Ready -> {
            val originalLines = remember(lyrics.lines) {
                lyrics.lines.map { line ->
                    Line(
                        words = line.words.map { word ->
                            Word(word.text, word.startMs, word.endMs, isPartOfWord = word.attached, romanizedText = word.romanized)
                        },
                        startMs = line.startMs,
                        endMs = line.endMs,
                        agent = line.agent,
                        role = line.role,
                        groupId = line.groupId,
                        oppositeAligned = line.oppositeAligned,
                        lineTimed = line.lineTimed,
                        untimed = line.untimed,
                    )
                }
            }
            val rendererLines = remember(originalLines, config.isMinimal, config.isSimple) {
                buildDisplayTimeline(originalLines, minimalMode = config.isMinimal, holdThroughShortGaps = when (lyrics.lyricsType) {
                    LyricsType.Syllable -> config.isMinimal
                    LyricsType.Line -> config.isSimple
                    else -> false
                })
            }
            val presentation = remember(rendererLines, originalLines, translation, translationMode) {
                translation?.let { result ->
                    TranslationPresentation.forTimeline(originalLines, rendererLines, result, translationMode)
                }
            }
            LyricsView(
                lines = rendererLines,
                documentId = remember(lyrics.lines) { lyrics.lines.hashCode().toString() },
                currentTimeMs = currentTimeMs,
                onSeekWord = onSeek,
                romanize = romanize,
                romanizationMode = romanizationMode,
                translation = presentation,
                isPlaying = isPlaying,
                activeLineTopPx = activeLineTopPx,
                focusAnchorFraction = 0.5f,
                focusLiftPx = centredLiftPx,
                // The list's `padding-top: 25cqh`.
                restTopFraction = 0.25f,
                lyricsType = lyrics.lyricsType,
                config = config,
                fontSizeScale = fontSizeScale,
                viewState = viewState,
                pinnedFooter = pinnedFooter,
                maskBottomPx = maskBottomPx,
                maskScale = maskScale,
                footer = remember(lyrics, translation, textCredits, romanizationCredit) {
                    LyricsFooter(
                        songwriters = lyrics.songwriters,
                        translationCredit = translation?.credit()?.takeIf { textCredits },
                        romanizationCredit = romanizationCredit,
                        // "Lyrics: Spicy Lyrics • Apple Music" when a provider syndicates another catalogue.
                        // The source alone: Apple Music's lyrics read the same from every service that fetches them.
                        provenance = LyricsProvenance(lyrics.source ?: lyrics.provider),
                        maker = lyrics.maker?.let { LyricsCredit(it.username, it.profileUrl, it.avatarUrl) },
                        uploader = lyrics.uploader?.let { LyricsCredit(it.username, it.profileUrl, it.avatarUrl) },
                    )
                },
                modifier = modifier,
            )
        }
    }
}

/** "Romanization from" in the credits: while romanization shows and the credit is on, where it comes from. */
@Composable
fun rememberRomanizationCredit(lyrics: LyricsState, humanRomanizedLines: Int, romanize: Boolean, shown: Boolean): String? =
    remember(lyrics, humanRomanizedLines, romanize, shown) {
        romanizationCredit(lyrics, humanRomanizedLines, romanize, shown)
    }

/**
 * The record's animated cover stream, or null while there is none (setting off, still searching,
 * or the album has no animation). Resets to null the moment the record changes.
 */
@Composable
private fun rememberMotionCoverUrl(platform: NowPlayingPlatform, enabled: Boolean, query: MotionCoverQuery): String? =
    platform.rememberMotionCover(enabled, query).let { if (it.settled) it.url else null }

/** A monotonic clock in ms, for the touches that restart the controls' idle timer. */
private val screenClock = TimeSource.Monotonic.markNow()

private fun uptimeMs(): Long = screenClock.elapsedNow().inWholeMilliseconds
