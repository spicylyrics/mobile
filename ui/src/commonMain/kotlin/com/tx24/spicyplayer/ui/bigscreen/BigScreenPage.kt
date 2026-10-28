package com.tx24.spicyplayer.ui.bigscreen

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.spicy.RenderConfig
import com.tx24.spicyplayer.lyrics.spicy.canvas.LyricsLayoutCalculator
import com.tx24.spicyplayer.ui.components.ExpandGlyph
import com.tx24.spicyplayer.ui.components.GlassButton
import com.tx24.spicyplayer.ui.components.GlyphEase
import com.tx24.spicyplayer.ui.components.LyricsSkeleton
import com.tx24.spicyplayer.ui.components.MoreGlyph
import com.tx24.spicyplayer.ui.components.PictureInPictureGlyph
import com.tx24.spicyplayer.ui.components.SettingsGlyph
import com.tx24.spicyplayer.ui.components.SpicyGlyphs
import com.tx24.spicyplayer.ui.components.SwapSideGlyph
import com.tx24.spicyplayer.ui.controls.PlayPauseGlyph
import com.tx24.spicyplayer.ui.controls.SkipGlyph
import com.tx24.spicyplayer.ui.controls.formatClock
import com.tx24.spicyplayer.ui.nowplaying.CompactHeaderMetrics
import com.tx24.spicyplayer.ui.nowplaying.LocalNowPlayingPlatform
import com.tx24.spicyplayer.ui.nowplaying.LyricsNotice
import com.tx24.spicyplayer.ui.nowplaying.MarqueeLine
import com.tx24.spicyplayer.ui.components.LocalBackdrop
import androidx.compose.foundation.focusable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.onKeyEvent
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.tx24.spicyplayer.ui.nowplaying.NoticeActions
import com.tx24.spicyplayer.ui.nowplaying.NowPlaying
import com.tx24.spicyplayer.ui.nowplaying.NowPlayingState
import com.tx24.spicyplayer.ui.nowplaying.RepeatState
import com.tx24.spicyplayer.ui.theme.SpicyColors
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest

/**
 * How a view control gets its tooltip. The desktop wraps the button in its own tooltip area;
 * platforms without hover leave the default, which shows none.
 */
interface TooltipHost {
    @Composable
    fun Tooltip(text: String, content: @Composable () -> Unit)
}

private object NoTooltips : TooltipHost {
    @Composable
    override fun Tooltip(text: String, content: @Composable () -> Unit) = content()
}

val LocalTooltipHost = staticCompositionLocalOf<TooltipHost> { NoTooltips }

/** What the page asks of the app beyond the player: a new view state, the settings panel, the floating window. */
class BigScreenActions(
    val onState: (BigScreenState) -> Unit,
    val onOpenSettings: () -> Unit,
    val onEnterPip: () -> Unit = {},
    val onOpenLyricsManager: () -> Unit = {},
)

/**
 * The big-screen lyrics page, for desktop windows, tablets and DeX: the [background] across the
 * window, the now-playing bar (cover, name and artists) beside the lyrics, the round view
 * controls, and in cinema and fullscreen the cover's playback row and timeline. Too narrow or
 * upright for that ([BigScreenState.layoutFor]), the song moves to a bar across the top.
 *
 * Controls show on hover, or toggle with a tap where nothing else took it (touch). The song and its
 * transport come from [nowPlaying]; [capabilities] say which commands the platform carries out.
 * [lyricsView] draws ready lyrics into the box it's given, romanized or not, keeping the active
 * line's top at the given px (compact) or centred (null), drawn with [renderConfig]: the one
 * place the page picks how the lyrics render. [noticeActions] are the notice's buttons.
 * [options] are the settings it follows ([bigScreenOptions]).
 */
@Composable
fun BigScreenPage(
    state: BigScreenState,
    nowPlaying: NowPlaying,
    capabilities: PlayerCapabilities,
    lyrics: LyricsState,
    actions: BigScreenActions,
    noticeActions: NoticeActions?,
    background: @Composable () -> Unit,
    lyricsView: @Composable (modifier: Modifier, romanize: Boolean, activeLineTopPx: Float?, renderConfig: RenderConfig) -> Unit,
    renderConfig: RenderConfig = RenderConfig.FULL,
    /** Whether the controls show, where the platform decides (a touch screen's own idle timer); null follows hover and taps. */
    controlsShown: Boolean? = null,
    /** The glass buttons blur what's behind them; off (Low Performance), they keep their translucent fill. */
    /** The settings the page follows; [bigScreenOptions] reads them. */
    options: BigScreenOptions = BigScreenOptions(),
    blurGlass: Boolean = true,
    /** The waiting notice's text while nothing plays, where the platform knows why (its media source failed). */
    waitingBody: String? = null,
) {
    // What the glass blurs: the background, the lyrics and the covers, each marked as a source.
    val backdrop = remember(blurGlass) { if (blurGlass) HazeState() else null }
    CompositionLocalProvider(LocalBackdrop provides backdrop) {
        BigScreenContent(state, nowPlaying, capabilities, lyrics, actions, noticeActions, background, lyricsView, renderConfig, controlsShown, waitingBody, options)
    }
}

/** Marks what the page's glass blurs, where there is a [LocalBackdrop]. */
@Composable
private fun Modifier.backdropSource(zIndex: Float = 0f): Modifier =
    LocalBackdrop.current?.let { this.hazeSource(it, zIndex) } ?: this

@Composable
private fun BigScreenContent(
    state: BigScreenState,
    nowPlaying: NowPlaying,
    capabilities: PlayerCapabilities,
    lyrics: LyricsState,
    actions: BigScreenActions,
    noticeActions: NoticeActions?,
    background: @Composable () -> Unit,
    lyricsView: @Composable (modifier: Modifier, romanize: Boolean, activeLineTopPx: Float?, renderConfig: RenderConfig) -> Unit,
    renderConfig: RenderConfig,
    controlsShown: Boolean?,
    waitingBody: String?,
    options: BigScreenOptions,
) {
    val lyricsSlot: @Composable (Modifier, Boolean, Float?) -> Unit = { modifier, romanize, top -> lyricsView(modifier, romanize, top, renderConfig) }
    val playing by nowPlaying.state.collectAsState()
    // The page takes the keyboard while nothing inside it (a text field, a focused button) does.
    val keys = remember { FocusRequester() }
    LaunchedEffect(keys) { runCatching { keys.requestFocus() } }
    val pageHover = remember { MutableInteractionSource() }
    val pageHovered by pageHover.collectIsHoveredAsState()
    val rowHover = remember { MutableInteractionSource() }
    val rowHovered by rowHover.collectIsHoveredAsState()
    val coverHover = remember { MutableInteractionSource() }
    val coverHovered by coverHover.collectIsHoveredAsState()
    // A tap where nothing else took it brings hidden controls up and puts shown ones away.
    var revealed by remember { mutableStateOf(false) }
    // The More menu lives here, not in the row: the row leaves when the controls fade, and an
    // open menu (like a timeline being dragged) holds them up instead.
    var menuOpen by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    val held = menuOpen || dragging

    // In cinema and fullscreen the controls fade a while after the pointer last moved. Each move
    // goes into a flow rather than state, so the page doesn't recompose at the pointer's rate.
    val moves = remember { MutableStateFlow(0L) }
    var idle by remember { mutableStateOf(false) }
    // Where the platform decides ([controlsShown], a touch screen's own idle timer), its answer
    // stands in for hover, taps and idling, and shown is shown in full.
    val platformShown = controlsShown
    val idleAfter = idleAfterMs(options.idle, playing.isPlaying).takeIf { state.immersive && platformShown == null }
    LaunchedEffect(idleAfter) {
        idle = false
        if (idleAfter == null) return@LaunchedEffect
        moves.collectLatest {
            idle = false
            delay(idleAfter)
            idle = true
            revealed = false
        }
    }
    var lastLevel by remember { mutableStateOf(ControlsLevel.Hidden) }
    val onPage = pageHovered && platformShown == null
    val tapped = platformShown ?: revealed
    val level = controlsLevel(onPage, platformShown ?: coverHovered, tapped, idle, held, previous = lastLevel)
    SideEffect { lastLevel = level }
    val currentLevel by rememberUpdatedState(level)
    // Off cinema and fullscreen the row has three steps; in the compact layout it idles too.
    val rowAlpha = viewControlsAlpha(onPage && !idle, rowHovered && platformShown == null, tapped && !idle, held)
    val input = PageInput(level, rowHover, coverHover, menuOpen, { menuOpen = it }, { dragging = it })
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .hoverable(pageHover)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Move || event.type == PointerEventType.Press) moves.value++
                    }
                }
            }
            .then(if (controlsShown != null) Modifier else Modifier.pointerInput(Unit) { detectTapGestures { revealed = revealedAfterTap(currentLevel) } })
            .focusRequester(keys)
            .focusable()
            .onKeyEvent { event -> onPlaybackKey(event, nowPlaying, capabilities) },
    ) {
        val density = LocalDensity.current.density
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val widthDp = widthPx / density
        val heightDp = heightPx / density
        // The timeline leaves the cover only in cinema and fullscreen: the page has none.
        val timelineOutside = options.timelineOutsideCover && state.immersive
        val metrics = remember(widthPx, heightPx, density, state.mode, timelineOutside) {
            BigScreenMetrics(widthPx, heightPx, density, state.mode, timelineOutside)
        }
        val cover = LocalNowPlayingPlatform.current
            .rememberCover(playing.artwork, playing.artworkUri, metrics.coverSizePx.roundToInt().coerceAtLeast(1), playing.hasSession)
            ?.image
        val env = PageEnv(state, capabilities, actions, widthDp, heightDp, metrics, options, input)
        Box(Modifier.fillMaxSize().backdropSource()) { background() }

        if (!playing.hasSession) {
            LyricsNotice(
                title = "Waiting for a song",
                modifier = Modifier.fillMaxSize(),
                body = waitingBody ?: "Play something in Spotify or another player that shows in the system's media controls.",
            )
            PageControls(env, rowAlpha, Modifier.fillMaxWidth())
            return@BoxWithConstraints
        }
        if (state.layoutFor(widthDp, heightDp) == BigScreenLayout.Compact) {
            CompactPage(env, playing, cover, lyrics, noticeActions, rowAlpha, lyricsSlot)
            return@BoxWithConstraints
        }

        val withBar = showsNowBar(state, options)
        val lyricsHidden = hidesLyrics(lyrics, state.immersive, options)
        // Opening, closing and moving the bar slide the cover and the lyrics over 400 ms; a closed
        // bar only waits off the page, so moving it then doesn't sweep it across.
        val layoutChange: Any = if (withBar) listOf(state.nowBarOnRight, lyricsHidden, state.mode) else false
        val box = metrics.lyricsBox(withBar, state.nowBarOnRight)
        val boxLeft = slidingPx(box.leftPx, layoutChange, LAYOUT_SLIDE_MS)
        val boxWidth = slidingPx(box.widthPx, layoutChange, LAYOUT_SLIDE_MS)
        if (!lyricsHidden) LyricsArea(
            lyrics, state.romanized, metrics, boxLeft, boxWidth, 0f,
            Modifier.offset { IntOffset(boxLeft.roundToInt(), 0) }.width(boxWidth.toDp(density)).fillMaxHeight(),
            noticeActions, lyricsSlot,
        )
        val hiddenLeft = metrics.hiddenCoverLeftPx(state.nowBarOnRight)
        val coverLeft = slidingPx(
            if (withBar) metrics.coverLeftPx(state.nowBarOnRight, lyricsHidden) else hiddenLeft, layoutChange, LAYOUT_SLIDE_MS,
        )
        if (withBar || coverLeft != hiddenLeft) NowBar(env, playing, cover, nowPlaying, coverLeft)
        if (!state.immersive) PageControls(env, rowAlpha, Modifier.fillMaxWidth())
    }
}

/** What most of the page's pieces need, resolved once per frame of layout. */
private class PageEnv(
    val state: BigScreenState,
    val capabilities: PlayerCapabilities,
    val actions: BigScreenActions,
    val widthDp: Float,
    val heightDp: Float,
    val metrics: BigScreenMetrics,
    val options: BigScreenOptions,
    val input: PageInput,
)

/**
 * Where the pointer is and what holds the controls up: the cover's [level] in cinema and
 * fullscreen, the hover of the view controls row and of the cover, the More menu, and a timeline
 * drag ([onDragging]).
 */
private class PageInput(
    val level: ControlsLevel,
    val rowHover: MutableInteractionSource,
    val coverHover: MutableInteractionSource,
    val menuOpen: Boolean,
    val onMenu: (Boolean) -> Unit,
    val onDragging: (Boolean) -> Unit,
)

/** The overdamped spring the cover's controls and its dimming move on. */
private val CoverControlsSpring = spring<Float>(dampingRatio = 2f, stiffness = 158f)

/** `transition: left 0.4s`: the bar opening, closing and changing sides. */
private const val LAYOUT_SLIDE_MS = 400

/** `transition: height 0.35s`: the compact layout's song bar. */
private const val COMPACT_SLIDE_MS = 350

/** CSS `ease`. */
private val CssEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

/**
 * [targetPx], moved there over [durationMs] when [trigger] changes (a view control, a setting),
 * but straight away when only the window's size moved it, so nothing trails a resize.
 */
@Composable
private fun slidingPx(targetPx: Float, trigger: Any, durationMs: Int): Float {
    val anim = remember { Animatable(targetPx) }
    var seen by remember { mutableStateOf(trigger) }
    var sliding by remember { mutableStateOf(false) }
    val changed = trigger != seen
    LaunchedEffect(trigger, targetPx) {
        if (trigger != seen) {
            seen = trigger
            sliding = true
            try {
                anim.animateTo(targetPx, tween(durationMs, easing = CssEase))
            } finally {
                sliding = false
            }
        } else {
            anim.snapTo(targetPx)
        }
    }
    // The frame the trigger changes on still shows where the slide starts from.
    return if (sliding || changed) anim.value else targetPx
}

/**
 * `.ViewControls` on the page: a row 24px from the top, or from the bottom with the setting, at
 * [target] opacity ([viewControlsAlpha]).
 */
@Composable
private fun PageControls(env: PageEnv, target: Float, modifier: Modifier) {
    val density = LocalDensity.current.density
    val alpha by animateFloatAsState(target, tween(300), label = "viewControls")
    if (alpha == 0f) return
    val metrics = env.metrics
    val size = metrics.pageControlSizePx
    val top = if (env.options.viewControlsAtBottom) metrics.heightPx - metrics.pageControlsTopPx - size else metrics.pageControlsTopPx
    ViewControlsRow(
        env, size.toDp(density),
        modifier.offset { IntOffset(0, top.roundToInt()) }.alpha(alpha),
    )
}

/** Lyrics, the skeleton while they load, or the notice in their place, inside the lyrics box. */
@Composable
private fun LyricsArea(
    lyrics: LyricsState,
    romanize: Boolean,
    metrics: BigScreenMetrics,
    boxLeftPx: Float,
    boxWidthPx: Float,
    topPx: Float,
    modifier: Modifier,
    noticeActions: NoticeActions?,
    lyricsView: @Composable (Modifier, Boolean, Float?) -> Unit,
    activeLineTopPx: Float? = null,
) {
    val inset = boxWidthPx * CompactHeaderMetrics.LYRICS_SIDE_INSET
    Box(Modifier.fillMaxSize().backdropSource()) {
    when (lyrics) {
        is LyricsState.Ready -> lyricsView(modifier, romanize, activeLineTopPx)
        is LyricsState.Error -> LyricsNotice(lyrics, noticeActions, bottomPx = { 0f }, modifier = modifier)
        // `.SkeletonLines { padding: 25cqh ... }`: the first bar where the first line will rest.
        else -> LyricsSkeleton(
            lineSizeSp = metrics.lyricFontSizeSp,
            lineStartPx = boxLeftPx + inset,
            lineMaxWidthPx = boxWidthPx - 2f * inset,
            topPx = topPx + (metrics.heightPx - topPx) * 0.25f,
        )
    }
    }
}

/**
 * The now-playing bar: the cover and, centred under it, the name (`font-weight: 900`, .95) and
 * the artists (.7). Off the page the pointer on the page dims the cover a little and brings up the
 * view controls along its top and the playback row and timeline along its bottom, faintly; on the
 * cover it dims it more and brings them up fully ([ControlsLevel]), both on a slow spring. With the
 * timeline outside the cover it sits, always shown, between the cover and the song. It all sits at
 * [leftPx], which slides.
 */
@Composable
private fun NowBar(
    env: PageEnv,
    playing: NowPlayingState,
    cover: ImageBitmap?,
    nowPlaying: NowPlaying,
    leftPx: Float,
) {
    val density = LocalDensity.current.density
    val metrics = env.metrics
    val left = leftPx
    val coverSize = metrics.coverSizePx.toDp(density)
    val level = if (env.state.immersive) env.input.level else ControlsLevel.Hidden
    val controls by animateFloatAsState(level.controlsAlpha, CoverControlsSpring, label = "coverControls")
    val brightness by animateFloatAsState(level.artworkBrightness, CoverControlsSpring, label = "coverBrightness")
    Box(
        Modifier
            .offset { IntOffset(left.roundToInt(), metrics.coverTopPx.roundToInt()) }
            .size(coverSize)
            .hoverable(env.input.coverHover),
    ) {
        val shape = RoundedCornerShape(metrics.coverCornerPx.toDp(density))
        Cover(cover, Modifier.fillMaxSize().shadow(20.dp, shape, clip = false).clip(shape).alpha(0.95f).backdropSource(zIndex = 1f))
        // The spring only nears its rest, so the overlay goes once it's out of sight.
        if (brightness < 0.995f) Box(Modifier.fillMaxSize().clip(shape).background(Color.Black.copy(alpha = 1f - brightness)))
        if (controls > 0.005f) {
            Box(Modifier.fillMaxSize().alpha(controls)) {
                ViewControlsRow(
                    env, metrics.coverControlSizePx.toDp(density),
                    Modifier.fillMaxWidth().offset { IntOffset(0, metrics.coverControlsTopPx.roundToInt()) },
                )
                CoverPlayback(env, playing, nowPlaying, withTimeline = !metrics.timelineOutside, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
    if (metrics.timelineOutside) PlaybackTimeline(
        env, playing, nowPlaying, metrics.outsideTimeline,
        Modifier.offset { IntOffset(left.roundToInt(), metrics.outsideTimelineTopPx.roundToInt()) }.width(coverSize),
    )
    SongText(
        playing,
        metrics.titleSizeSp, metrics.titleLineHeightSp, metrics.artistsSizeSp, metrics.artistsLineHeightSp,
        Alignment.CenterHorizontally,
        metrics.coverSizePx,
        Modifier.offset { IntOffset(left.roundToInt(), metrics.textTopPx.roundToInt()) }.width(coverSize),
    )
}

/**
 * The song's name over its artists, one line each. A name too long for its line scrolls back and
 * forth like the header's (`HeaderMarquee`) with its edges faded; one that fits sits still.
 * [widthPx] is the text block's width: centred under the cover the name gets 85% of it and the
 * artists 80% (`.SongName { max-width: 85cqw }`, `.Artists { max-width: 80cqw }`); in the compact
 * bar both get all of it.
 */
@Composable
private fun SongText(
    playing: NowPlayingState,
    titleSp: Float,
    titleLineSp: Float,
    artistsSp: Float,
    artistsLineSp: Float,
    align: Alignment.Horizontal,
    widthPx: Float,
    modifier: Modifier,
) {
    val family = LyricsLayoutCalculator.spicyFontFamily
    val centred = align != Alignment.Start
    Column(modifier, horizontalAlignment = align) {
        MarqueeLine(
            playing.track.title,
            TextStyle(fontSize = titleSp.sp, fontWeight = FontWeight.Black, fontFamily = family, lineHeight = titleLineSp.sp),
            Color.White.copy(alpha = 0.95f),
            containerWidthPx = widthPx * if (centred) 0.85f else 1f,
        )
        MarqueeLine(
            playing.track.artists,
            TextStyle(fontSize = artistsSp.sp, fontWeight = FontWeight.Normal, fontFamily = family, lineHeight = artistsLineSp.sp),
            Color.White.copy(alpha = 0.7f),
            containerWidthPx = widthPx * if (centred) 0.8f else 1f,
        )
    }
}

/** The cover art, crossfading to each new one; a dim square until the first arrives. */
@Composable
private fun Cover(art: ImageBitmap?, modifier: Modifier) {
    Crossfade(art, modifier, animationSpec = tween(400), label = "cover") { image ->
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.09f)))
        }
    }
}

/**
 * The cover's bottom: the playback row (previous, play/pause, next, each dimmed where the
 * platform can't) above the timeline ([withTimeline]; else the row sits `6.5cqh` from the bottom and
 * the timeline is under the cover).
 */
@Composable
private fun CoverPlayback(env: PageEnv, playing: NowPlayingState, nowPlaying: NowPlaying, withTimeline: Boolean, modifier: Modifier) {
    val density = LocalDensity.current.density
    val metrics = env.metrics
    val pause by animateFloatAsState(if (playing.isPlaying) 1f else 0f, tween(200), label = "playPause")
    val skip = remember { SkipGlyph { 0f } }
    val playPause = remember { PlayPauseGlyph { pause } }
    val toggles = playbackToggles(env.capabilities, playing)
    // With either toggle, both ends keep their room, so the three buttons stay centred.
    val withToggles = toggles.shuffle != null || toggles.repeat != null
    Column(
        modifier.fillMaxWidth().padding(bottom = if (withTimeline) 0.dp else metrics.playbackRowBottomPx.toDp(density)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = metrics.playbackRowInsetPx.toDp(density))
                .height(metrics.playbackRowHeightPx.toDp(density)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (withToggles) PlaybackToggle(
                SpicyGlyphs.Shuffle, active = toggles.shuffle == true, shown = toggles.shuffle != null,
                label = "Shuffle", widthPx = metrics.toggleWidthPx,
            ) { toggles.shuffle?.let { nowPlaying.setShuffle(!it) } }
            for (slot in transportSlots(env.capabilities)) {
                val width = if (slot.button == TransportButton.PlayPause) metrics.playPauseWidthPx else metrics.skipWidthPx
                val onClick: () -> Unit = when (slot.button) {
                    TransportButton.PlayPause -> nowPlaying::playPause
                    TransportButton.Previous -> nowPlaying::skipPrevious
                    TransportButton.Next -> nowPlaying::skipNext
                }
                Box(
                    Modifier
                        .width(width.toDp(density))
                        .fillMaxHeight()
                        .alpha(if (slot.enabled) 1f else 0.3f)
                        .clickable(remember { MutableInteractionSource() }, indication = null, enabled = slot.enabled, onClick = onClick),
                    contentAlignment = Alignment.Center,
                ) {
                    val glyph = if (slot.button == TransportButton.PlayPause) playPause else skip
                    val glyphModifier = Modifier.fillMaxSize(0.6f).let { if (slot.button == TransportButton.Previous) it.rotate(180f) else it }
                    Image(glyph, contentDescription = slot.button.name, modifier = glyphModifier)
                }
            }
            if (withToggles) PlaybackToggle(
                if (toggles.repeat == RepeatState.One) SpicyGlyphs.RepeatOne else SpicyGlyphs.Repeat,
                active = toggles.repeat != null && toggles.repeat != RepeatState.Off, shown = toggles.repeat != null,
                label = "Repeat", widthPx = metrics.toggleWidthPx,
            ) { toggles.repeat?.let { nowPlaying.setRepeat(nextRepeat(it, playing.repeatOneSupported)) } }
        }
        if (withTimeline) PlaybackTimeline(env, playing, nowPlaying, metrics.coverTimeline, Modifier)
    }
}

/**
 * Shuffle or repeat at an end of the playback row, in the accent with a soft glow while on (the
 * accent can be white, so the glow is what tells on from off; with Low Performance, only the tint).
 * Not [shown], it only keeps its room.
 */
@Composable
private fun PlaybackToggle(glyph: ImageVector, active: Boolean, shown: Boolean, label: String, widthPx: Float, onClick: () -> Unit) {
    val density = LocalDensity.current.density
    val width = widthPx.toDp(density)
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.width(width).fillMaxHeight().clickable(interaction, indication = null, enabled = shown, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (!shown) return@Box
        val tint = if (active) ColorFilter.tint(SpicyColors.Accent) else null
        val glyphModifier = Modifier.fillMaxSize(0.9f)
        // The glow is a blur, so Low Performance (no backdrop) leaves it out with the glass.
        if (active && LocalBackdrop.current != null) Image(
            glyph, contentDescription = null, colorFilter = tint, alpha = 0.5f,
            modifier = glyphModifier.blur(width * 0.12f, BlurredEdgeTreatment.Unbounded),
        )
        Image(glyph, contentDescription = label, colorFilter = tint, modifier = glyphModifier)
    }
}

/** The song's timeline laid out by [geometry], following the player's position every frame. */
@Composable
private fun PlaybackTimeline(env: PageEnv, playing: NowPlayingState, nowPlaying: NowPlaying, geometry: TimelineGeometry, modifier: Modifier) {
    var now by remember { mutableLongStateOf(nowPlaying.positionMs()) }
    LaunchedEffect(nowPlaying) { while (true) withFrameMillis { now = nowPlaying.positionMs() } }
    Timeline(now, playing.track.durationMs, env.capabilities.seek && playing.canSeek, env.metrics, geometry, nowPlaying::seekTo, env.input.onDragging, modifier)
}

/**
 * `.Timeline`: the position, the bar (white for what has played), and the length. Where it seeks,
 * the pointer over the bar (or dragging it) shows the time it would go to in a small label above
 * the bar, kept inside the timeline, and while dragging the position shows the drag's time too.
 */
@Composable
private fun Timeline(
    positionMs: Long,
    durationMs: Long,
    seekable: Boolean,
    metrics: BigScreenMetrics,
    geometry: TimelineGeometry,
    onSeek: (Long) -> Unit,
    onDragging: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val density = LocalDensity.current.density
    val timeStyle = TextStyle(color = SpicyColors.TextSecondary, fontSize = metrics.baseFontSizeSp.sp, fontWeight = FontWeight.Medium,
        fontFamily = LyricsLayoutCalculator.spicyFontFamily, fontFeatureSettings = "tnum")
    var barWidth by remember { mutableStateOf(1) }
    // While dragging, the bar follows the pointer rather than the player.
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var hoverFraction by remember { mutableStateOf<Float?>(null) }
    val progress = dragFraction ?: progressOf(positionMs, durationMs)
    val barHeight = geometry.barHeightPx.toDp(density).coerceAtLeast(3.dp)
    val timeWidth = (metrics.baseFontSizeSp * 2.5f).dp
    // Where the bar sits in the timeline, for the label above it.
    var timeline by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var bar by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Box(modifier.fillMaxWidth().onGloballyPositioned { timeline = it }) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = geometry.insetXPx.toDp(density), end = geometry.insetXPx.toDp(density),
                top = geometry.insetTopPx.toDp(density), bottom = geometry.insetBottomPx.toDp(density),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shownMs = dragFraction?.let { seekTarget(it, durationMs) } ?: positionMs
        BasicText(formatClock(shownMs), Modifier.widthIn(min = timeWidth), style = timeStyle.copy(textAlign = TextAlign.End))
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = geometry.barMarginPx.toDp(density))
                .height(barHeight + BigScreenMetrics.BAR_HIT_SLOP_DP.dp)
                .onSizeChanged { barWidth = it.width.coerceAtLeast(1) }
                .onGloballyPositioned { bar = it }
                .then(
                    if (!seekable) Modifier else Modifier
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    // A finger has no hover; its drag shows the label instead.
                                    if (event.changes.first().type == PointerType.Touch) continue
                                    hoverFraction = when (event.type) {
                                        PointerEventType.Exit -> null
                                        else -> (event.changes.first().position.x / barWidth).coerceIn(0f, 1f)
                                    }
                                }
                            }
                        }
                        .pointerInput(durationMs) {
                            detectTapGestures { onSeek(seekTarget(it.x / barWidth, durationMs)) }
                        }
                        .pointerInput(durationMs) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    dragFraction = it.x / barWidth
                                    onDragging(true)
                                },
                                onDragEnd = {
                                    dragFraction?.let { onSeek(seekTarget(it, durationMs)) }
                                    dragFraction = null
                                    onDragging(false)
                                },
                                onDragCancel = {
                                    dragFraction = null
                                    onDragging(false)
                                },
                            ) { change, _ -> dragFraction = (change.position.x / barWidth).coerceIn(0f, 1f) }
                        },
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            val pill = RoundedCornerShape(50)
            Box(Modifier.fillMaxWidth().height(barHeight).clip(pill).background(SpicyColors.TextQuaternary))
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(barHeight).clip(pill).background(SpicyColors.Accent))
        }
        BasicText(formatClock(durationMs), Modifier.widthIn(min = timeWidth), style = timeStyle)
    }
    val preview = (dragFraction ?: hoverFraction).takeIf { seekable && durationMs > 0 }
    val barHeightPx = barHeight.value * density
    ScrubPreview(
        text = formatClock(seekTarget(preview ?: 0f, durationMs)),
        shown = preview != null,
        fontSizeSp = metrics.baseFontSizeSp,
        anchor = {
            val outer = timeline
            val inner = bar
            if (outer == null || inner == null || !outer.isAttached || !inner.isAttached) {
                null
            } else {
                val start = outer.localPositionOf(inner, Offset.Zero)
                // The bar is drawn centred in its taller hit box.
                Offset(start.x + (preview ?: 0f) * inner.size.width, start.y + (inner.size.height - barHeightPx) / 2f)
            }
        },
        maxWidthPx = { timeline?.size?.width?.toFloat() ?: 0f },
    )
    }
}

/**
 * `.ScrubPreview`: the time a click would seek to, in a small dark label centred over [anchor] (the
 * bar's top edge under the pointer), kept within [maxWidthPx], fading and growing in quickly.
 */
@Composable
private fun ScrubPreview(text: String, shown: Boolean, fontSizeSp: Float, anchor: () -> Offset?, maxWidthPx: () -> Float) {
    val density = LocalDensity.current.density
    val visible by animateFloatAsState(if (shown) 1f else 0f, tween(if (shown) 120 else 90, easing = GlyphEase), label = "scrubPreview")
    if (visible == 0f) return
    var size by remember { mutableStateOf(IntSize.Zero) }
    val em = fontSizeSp * density
    BasicText(
        text,
        Modifier
            .offset {
                val at = anchor() ?: return@offset IntOffset.Zero
                val x = (at.x - size.width / 2f).coerceIn(0f, (maxWidthPx() - size.width).coerceAtLeast(0f))
                IntOffset(x.roundToInt(), (at.y - SCRUB_GAP_EM * em - size.height).roundToInt())
            }
            .onSizeChanged { size = it }
            .graphicsLayer {
                alpha = visible
                val scale = 0.96f + 0.04f * visible
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .clip(RoundedCornerShape((0.45f * em).toDp(density)))
            .background(Color(18, 18, 22).copy(alpha = 0.58f))
            .padding(horizontal = (0.5f * em).toDp(density), vertical = (0.3f * em).toDp(density)),
        style = TextStyle(
            color = Color.White, fontSize = fontSizeSp.sp, fontWeight = FontWeight.Medium, lineHeight = (fontSizeSp * 1.1f).sp,
            fontFamily = LyricsLayoutCalculator.spicyFontFamily, fontFeatureSettings = "tnum",
        ),
        maxLines = 1,
    )
}

/** `--ScrubPreviewGap: 0.55em` between the label and the bar. */
private const val SCRUB_GAP_EM = 0.55f

/**
 * The compact layout: the song as a bar across the top (cover, then name and artists), the view
 * controls at its end, and the lyrics below, the active line held near their top. Without the song
 * header (the setting, off cinema and fullscreen) the lyrics take the whole page, the active line centred.
 */
@Composable
private fun CompactPage(
    env: PageEnv,
    playing: NowPlayingState,
    cover: ImageBitmap?,
    lyrics: LyricsState,
    noticeActions: NoticeActions?,
    rowAlpha: Float,
    lyricsView: @Composable (Modifier, Boolean, Float?) -> Unit,
) {
    val density = LocalDensity.current.density
    val metrics = env.metrics
    val bar = remember(metrics) { CompactHeaderMetrics(metrics.widthPx, metrics.heightPx, density, metrics.lyricFontSizeSp) }
    val withHeader = !(env.options.hideHeader && !env.state.immersive)
    // Hiding or showing the song bar slides the lyrics up or down over 350 ms.
    val lyricsTop = slidingPx(if (withHeader) bar.lyricsTopPx else 0f, withHeader, COMPACT_SLIDE_MS)
    LyricsArea(
        lyrics, env.state.romanized, metrics, 0f, metrics.widthPx, lyricsTop,
        Modifier.fillMaxSize().padding(top = lyricsTop.toDp(density)), noticeActions, lyricsView,
        activeLineTopPx = bar.activeLineTopPx.takeIf { withHeader },
    )
    if (withHeader) CompactSongBar(playing, cover, bar)
    val alpha by animateFloatAsState(rowAlpha, tween(300), label = "compactControls")
    val atBottom = env.options.viewControlsAtBottom
    if (alpha > 0f) Box(
        Modifier
            .fillMaxSize()
            .padding(
                top = if (atBottom) 0.dp else metrics.pageControlsTopPx.toDp(density),
                bottom = if (atBottom) metrics.pageControlsTopPx.toDp(density) else 0.dp,
                end = bar.contentStartPx.toDp(density),
            )
            .alpha(alpha),
        contentAlignment = if (atBottom) Alignment.BottomEnd else Alignment.TopEnd,
    ) {
        ViewControlsRow(env, metrics.pageControlSizePx.toDp(density), Modifier)
    }
}

/** The compact layout's song bar: the cover, then the name over the artists. */
@Composable
private fun CompactSongBar(playing: NowPlayingState, cover: ImageBitmap?, bar: CompactHeaderMetrics) {
    val density = LocalDensity.current.density
    val shape = RoundedCornerShape((bar.artSizePx * CompactHeaderMetrics.ART_CORNER_FRACTION).toDp(density))
    Cover(
        cover,
        Modifier
            .offset { IntOffset(bar.contentStartPx.roundToInt(), bar.barTopPx.roundToInt()) }
            .size(bar.artSizePx.toDp(density))
            .shadow(bar.artShadowBlurDp.dp, shape, clip = false)
            .clip(shape)
            .backdropSource(zIndex = 1f),
    )
    Box(
        Modifier
            .offset { IntOffset(bar.textStartPx.roundToInt(), bar.barTopPx.roundToInt()) }
            .width(bar.textWidthPx.toDp(density))
            .height(bar.barHeightPx.toDp(density)),
        contentAlignment = Alignment.CenterStart,
    ) {
        SongText(playing, bar.titleSizeSp, bar.titleLineHeightSp, bar.artistsSizeSp, bar.artistsLineHeightSp, Alignment.Start, bar.textWidthPx, Modifier)
    }
}

/** The round glass view controls [pageViewControls] picks, with their tooltips; More opens its menu. */
@Composable
private fun ViewControlsRow(env: PageEnv, size: Dp, modifier: Modifier) {
    val tooltips = LocalTooltipHost.current
    val state = env.state
    val input = env.input
    Row(
        modifier.hoverable(input.rowHover),
        horizontalArrangement = Arrangement.spacedBy(env.metrics.controlGapPx.toDp(LocalDensity.current.density), Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (control in pageViewControls(state, env.capabilities, env.options)) {
            val label = tooltip(control, state, env.widthDp, env.heightDp)
            tooltips.Tooltip(label) {
                Box {
                    GlassButton(
                        onClick = {
                            when (control) {
                                ViewControl.More -> input.onMenu(true)
                                ViewControl.Settings -> env.actions.onOpenSettings()
                                ViewControl.PictureInPicture -> env.actions.onEnterPip()
                                ViewControl.LyricsManager -> env.actions.onOpenLyricsManager()
                                else -> env.actions.onState(state.click(control, env.widthDp, env.heightDp, env.capabilities))
                            }
                        },
                        contentDescription = label,
                        size = size,
                    ) { ControlGlyph(control, env, Modifier.size(size * 0.5f)) }
                    if (control == ViewControl.More && input.menuOpen) {
                        MoreMenu(size, (env.heightDp * 0.6f).dp, above = env.options.viewControlsAtBottom && !state.immersive) { input.onMenu(false) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlGlyph(control: ViewControl, env: PageEnv, modifier: Modifier) {
    val state = env.state
    when (control) {
        ViewControl.CinemaView -> ExpandGlyph(expanded = false, modifier)
        ViewControl.CompactMode -> CompactGlyph(state.isCompact(env.widthDp, env.heightDp), modifier)
        ViewControl.Romanization -> Image(
            if (state.romanized) SpicyGlyphs.DisableRomanization else SpicyGlyphs.EnableRomanization,
            contentDescription = null,
            modifier = modifier,
        )
        ViewControl.NowBar -> NowBarGlyph(state.nowBarOnRight, modifier)
        ViewControl.NowBarSide -> SwapSideGlyph(state.nowBarOnRight, modifier)
        ViewControl.Fullscreen -> ExpandGlyph(expanded = state.mode == ViewMode.Fullscreen, modifier)
        ViewControl.More -> MoreGlyph(modifier)
        ViewControl.PictureInPicture -> PictureInPictureGlyph(modifier)
        ViewControl.Settings -> SettingsGlyph(modifier)
        ViewControl.LyricsManager -> LyricsManagerGlyph(modifier)
        ViewControl.Close -> CloseGlyph(modifier)
    }
}

/**
 * The lyrics choices beyond the view: source, translation, romanization style ([LocalLyricsActions]),
 * under the button, or [above] it for controls along the bottom. Scrolls past [maxHeight] in short
 * windows.
 */
@Composable
private fun MoreMenu(anchor: Dp, maxHeight: Dp, above: Boolean, onDismiss: () -> Unit) {
    val density = LocalDensity.current
    val gap = with(density) { (anchor + 8.dp).roundToPx() }
    Popup(
        alignment = if (above) Alignment.BottomCenter else Alignment.TopCenter,
        offset = IntOffset(0, if (above) -gap else gap),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            Modifier
                .width(280.dp)
                .shadow(24.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(SpicyColors.BgElevated)
                .heightIn(max = maxHeight.coerceAtLeast(96.dp))
                .verticalScroll(rememberScrollState())
                .padding(vertical = 6.dp),
        ) {
            MoreMenuEntries(LocalLyricsActions.current)
        }
    }
}

/** F11, and Escape or back, as the page's keys; null for anything else. */
fun pageKeyOf(event: KeyEvent): PageKey? {
    if (event.type != KeyEventType.KeyDown) return null
    return when (event.key) {
        Key.F11 -> PageKey.F11
        Key.Escape, Key.Back -> PageKey.Escape
        else -> null
    }
}

/**
 * Carries out a playback key ([playbackKeyOf], [playbackCommand]) on [nowPlaying]; true when it
 * did, so the key goes no further. Only key presses count.
 */
private fun onPlaybackKey(event: KeyEvent, nowPlaying: NowPlaying, capabilities: PlayerCapabilities): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val key = playbackKeyOf(event.key, event.isShiftPressed, event.isCtrlPressed || event.isAltPressed || event.isMetaPressed)
        ?: return false
    val state = nowPlaying.state.value
    if (!state.hasSession) return false
    val command = playbackCommand(key, nowPlaying.positionMs(), state.track.durationMs, capabilities, state.canSeek) ?: return false
    when (command) {
        PlaybackCommand.PlayPause -> nowPlaying.playPause()
        PlaybackCommand.Previous -> nowPlaying.skipPrevious()
        PlaybackCommand.Next -> nowPlaying.skipNext()
        is PlaybackCommand.Seek -> nowPlaying.seekTo(command.positionMs)
    }
    return true
}

private fun Float.toDp(density: Float): Dp = (this / density).dp
