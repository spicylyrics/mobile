package com.tx24.spicyplayer.ui.bigscreen

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.spicy.RenderConfig
import com.tx24.spicyplayer.lyrics.spicy.canvas.LyricsLayoutCalculator
import com.tx24.spicyplayer.ui.components.ExpandGlyph
import com.tx24.spicyplayer.ui.components.GlassButton
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
import com.tx24.spicyplayer.ui.nowplaying.NoticeActions
import com.tx24.spicyplayer.ui.nowplaying.NowPlaying
import com.tx24.spicyplayer.ui.nowplaying.NowPlayingState
import com.tx24.spicyplayer.ui.theme.SpicyColors
import kotlin.math.roundToInt

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
) {
    val lyricsSlot: @Composable (Modifier, Boolean, Float?) -> Unit = { modifier, romanize, top -> lyricsView(modifier, romanize, top, renderConfig) }
    val playing by nowPlaying.state.collectAsState()
    val pageHover = remember { MutableInteractionSource() }
    val pageHovered by pageHover.collectIsHoveredAsState()
    // A tap where nothing else took it shows the controls; the next one hides them.
    var revealed by remember { mutableStateOf(false) }
    val showControls = controlsShown ?: (pageHovered || revealed)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .hoverable(pageHover)
            .then(if (controlsShown != null) Modifier else Modifier.pointerInput(Unit) { detectTapGestures { revealed = !revealed } }),
    ) {
        val density = LocalDensity.current.density
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val widthDp = widthPx / density
        val heightDp = heightPx / density
        val metrics = remember(widthPx, heightPx, density, state.mode) { BigScreenMetrics(widthPx, heightPx, density, state.mode) }
        val cover = LocalNowPlayingPlatform.current
            .rememberCover(playing.artwork, playing.artworkUri, metrics.coverSizePx.roundToInt().coerceAtLeast(1), playing.hasSession)
            ?.image
        val env = PageEnv(state, capabilities, actions, widthDp, heightDp, metrics)
        background()

        if (!playing.hasSession) {
            LyricsNotice(
                title = "Waiting for a song",
                modifier = Modifier.fillMaxSize(),
                body = "Play something in Spotify or another player that shows in the system's media controls.",
            )
            PageControls(env, showControls, Modifier.fillMaxWidth())
            return@BoxWithConstraints
        }
        if (state.layoutFor(widthDp, heightDp) == BigScreenLayout.Compact) {
            CompactPage(env, playing, cover, lyrics, noticeActions, showControls, lyricsSlot)
            return@BoxWithConstraints
        }

        val withBar = state.nowBarOpen
        val box = metrics.lyricsBox(withBar, state.nowBarOnRight)
        LyricsArea(
            lyrics, state.romanized, metrics, box.leftPx, box.widthPx, 0f,
            Modifier.offset { IntOffset(box.leftPx.roundToInt(), 0) }.width(box.widthPx.toDp(density)).fillMaxHeight(),
            noticeActions, lyricsSlot,
        )
        if (withBar) NowBar(env, playing, cover, nowPlaying, showControls)
        if (!state.immersive) PageControls(env, showControls, Modifier.fillMaxWidth())
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
)

/** `.ViewControls` on the page: a row 24px from the top, faded in while the page is hovered. */
@Composable
private fun PageControls(env: PageEnv, shown: Boolean, modifier: Modifier) {
    val density = LocalDensity.current.density
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(300), label = "viewControls")
    if (alpha == 0f) return
    ViewControlsRow(
        env, env.metrics.pageControlSizePx.toDp(density),
        modifier.offset { IntOffset(0, env.metrics.pageControlsTopPx.roundToInt()) }.alpha(alpha),
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

/**
 * The now-playing bar: the cover and, centred under it, the name (`font-weight: 900`, .95) and
 * the artists (.7). Off the page, hovering (or tapping) the cover darkens it and brings up the
 * view controls along its top and the playback row and timeline along its bottom.
 */
@Composable
private fun NowBar(env: PageEnv, playing: NowPlayingState, cover: ImageBitmap?, nowPlaying: NowPlaying, pageRevealed: Boolean) {
    val density = LocalDensity.current.density
    val metrics = env.metrics
    val left = metrics.coverLeftPx(env.state.nowBarOnRight)
    val coverSize = metrics.coverSizePx.toDp(density)
    val coverHover = remember { MutableInteractionSource() }
    val coverHovered by coverHover.collectIsHoveredAsState()
    val overlay by animateFloatAsState(
        if (env.state.immersive && (coverHovered || pageRevealed)) 1f else 0f, tween(300), label = "coverControls",
    )
    Box(
        Modifier
            .offset { IntOffset(left.roundToInt(), metrics.coverTopPx.roundToInt()) }
            .size(coverSize)
            .hoverable(coverHover),
    ) {
        val shape = RoundedCornerShape(metrics.coverCornerPx.toDp(density))
        Cover(cover, Modifier.fillMaxSize().shadow(20.dp, shape, clip = false).clip(shape).alpha(0.95f))
        if (overlay > 0f) {
            Box(Modifier.fillMaxSize().clip(shape).background(Color.Black.copy(alpha = 0.45f * overlay)))
            Box(Modifier.fillMaxSize().alpha(overlay)) {
                ViewControlsRow(
                    env, metrics.coverControlSizePx.toDp(density),
                    Modifier.fillMaxWidth().offset { IntOffset(0, metrics.coverControlsTopPx.roundToInt()) },
                )
                CoverPlayback(env, playing, nowPlaying, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
    SongText(
        playing,
        metrics.titleSizeSp, metrics.titleLineHeightSp, metrics.artistsSizeSp, metrics.artistsLineHeightSp,
        Alignment.CenterHorizontally,
        Modifier.offset { IntOffset(left.roundToInt(), metrics.textTopPx.roundToInt()) }.width(coverSize),
    )
}

/** The song's name over its artists, one line each, cut off with an ellipsis. */
@Composable
private fun SongText(
    playing: NowPlayingState,
    titleSp: Float,
    titleLineSp: Float,
    artistsSp: Float,
    artistsLineSp: Float,
    align: Alignment.Horizontal,
    modifier: Modifier,
) {
    val textAlign = if (align == Alignment.Start) TextAlign.Start else TextAlign.Center
    val family = LyricsLayoutCalculator.spicyFontFamily
    Column(modifier, horizontalAlignment = align) {
        // TODO: the marquee (`HeaderMarquee`) for names too long to fit.
        BasicText(
            playing.track.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = Color.White.copy(alpha = 0.95f), fontSize = titleSp.sp, fontWeight = FontWeight.Black,
                fontFamily = family, lineHeight = titleLineSp.sp, textAlign = textAlign),
        )
        BasicText(
            playing.track.artists, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = artistsSp.sp, fontWeight = FontWeight.Normal,
                fontFamily = family, lineHeight = artistsLineSp.sp, textAlign = textAlign),
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
 * platform can't) above the timeline, whose bar seeks on click and drag where it can.
 */
@Composable
private fun CoverPlayback(env: PageEnv, playing: NowPlayingState, nowPlaying: NowPlaying, modifier: Modifier) {
    val density = LocalDensity.current.density
    val metrics = env.metrics
    var now by remember { mutableLongStateOf(nowPlaying.positionMs()) }
    LaunchedEffect(nowPlaying) { while (true) withFrameMillis { now = nowPlaying.positionMs() } }
    val pause by animateFloatAsState(if (playing.isPlaying) 1f else 0f, tween(200), label = "playPause")
    val skip = remember { SkipGlyph { 0f } }
    val playPause = remember { PlayPauseGlyph { pause } }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = metrics.playbackRowInsetPx.toDp(density))
                .height(metrics.playbackRowHeightPx.toDp(density)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
        }
        Timeline(now, playing.track.durationMs, env.capabilities.seek && playing.canSeek, metrics, nowPlaying::seekTo)
    }
}

/** `.Timeline`: the position, the bar (white for what has played), and the length. */
@Composable
private fun Timeline(positionMs: Long, durationMs: Long, seekable: Boolean, metrics: BigScreenMetrics, onSeek: (Long) -> Unit) {
    val density = LocalDensity.current.density
    val timeStyle = TextStyle(color = SpicyColors.TextSecondary, fontSize = metrics.baseFontSizeSp.sp, fontWeight = FontWeight.Medium,
        fontFamily = LyricsLayoutCalculator.spicyFontFamily)
    var barWidth by remember { mutableStateOf(1) }
    // While dragging, the bar follows the pointer rather than the player.
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val progress = dragFraction ?: progressOf(positionMs, durationMs)
    val barHeight = metrics.timelineBarHeightPx.toDp(density).coerceAtLeast(3.dp)
    val timeWidth = (metrics.baseFontSizeSp * 2.5f).dp
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = metrics.timelineInsetXPx.toDp(density), vertical = metrics.timelineInsetYPx.toDp(density)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(formatClock(positionMs), Modifier.widthIn(min = timeWidth), style = timeStyle.copy(textAlign = TextAlign.End))
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = 6.dp)
                .height(barHeight + 10.dp)
                .onSizeChanged { barWidth = it.width.coerceAtLeast(1) }
                .then(
                    if (!seekable) Modifier else Modifier
                        .pointerInput(durationMs) {
                            detectTapGestures { onSeek(seekTarget(it.x / barWidth, durationMs)) }
                        }
                        .pointerInput(durationMs) {
                            detectHorizontalDragGestures(
                                onDragStart = { dragFraction = it.x / barWidth },
                                onDragEnd = {
                                    dragFraction?.let { onSeek(seekTarget(it, durationMs)) }
                                    dragFraction = null
                                },
                                onDragCancel = { dragFraction = null },
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
}

/**
 * The compact layout: the song as a bar across the top (cover, then name and artists), the view
 * controls at its end, and the lyrics below, the active line held near their top.
 */
@Composable
private fun CompactPage(
    env: PageEnv,
    playing: NowPlayingState,
    cover: ImageBitmap?,
    lyrics: LyricsState,
    noticeActions: NoticeActions?,
    showControls: Boolean,
    lyricsView: @Composable (Modifier, Boolean, Float?) -> Unit,
) {
    val density = LocalDensity.current.density
    val metrics = env.metrics
    val bar = remember(metrics) { CompactHeaderMetrics(metrics.widthPx, metrics.heightPx, density, metrics.lyricFontSizeSp) }
    val lyricsTop = bar.lyricsTopPx
    LyricsArea(
        lyrics, env.state.romanized, metrics, 0f, metrics.widthPx, lyricsTop,
        Modifier.fillMaxSize().padding(top = lyricsTop.toDp(density)), noticeActions, lyricsView,
        activeLineTopPx = bar.activeLineTopPx,
    )
    val shape = RoundedCornerShape((bar.artSizePx * CompactHeaderMetrics.ART_CORNER_FRACTION).toDp(density))
    Cover(
        cover,
        Modifier
            .offset { IntOffset(bar.contentStartPx.roundToInt(), bar.barTopPx.roundToInt()) }
            .size(bar.artSizePx.toDp(density))
            .shadow(bar.artShadowBlurDp.dp, shape, clip = false)
            .clip(shape),
    )
    Box(
        Modifier
            .offset { IntOffset(bar.textStartPx.roundToInt(), bar.barTopPx.roundToInt()) }
            .width(bar.textWidthPx.toDp(density))
            .height(bar.barHeightPx.toDp(density)),
        contentAlignment = Alignment.CenterStart,
    ) {
        SongText(playing, bar.titleSizeSp, bar.titleLineHeightSp, bar.artistsSizeSp, bar.artistsLineHeightSp, Alignment.Start, Modifier)
    }
    val alpha by animateFloatAsState(if (showControls) 1f else 0f, tween(300), label = "compactControls")
    if (alpha > 0f) Box(
        Modifier
            .fillMaxWidth()
            .padding(top = metrics.pageControlsTopPx.toDp(density), end = bar.contentStartPx.toDp(density))
            .alpha(alpha),
        contentAlignment = Alignment.TopEnd,
    ) {
        ViewControlsRow(env, metrics.pageControlSizePx.toDp(density), Modifier)
    }
}

/** The round glass view controls [viewControls] picks, with their tooltips; More opens its menu. */
@Composable
private fun ViewControlsRow(env: PageEnv, size: Dp, modifier: Modifier) {
    val tooltips = LocalTooltipHost.current
    val state = env.state
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(env.metrics.controlGapPx.toDp(LocalDensity.current.density), Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (control in viewControls(state, env.capabilities)) {
            val label = tooltip(control, state, env.widthDp, env.heightDp)
            tooltips.Tooltip(label) {
                Box {
                    GlassButton(
                        onClick = {
                            when (control) {
                                ViewControl.More -> menuOpen = true
                                ViewControl.Settings -> env.actions.onOpenSettings()
                                ViewControl.PictureInPicture -> env.actions.onEnterPip()
                                else -> env.actions.onState(state.click(control, env.widthDp, env.heightDp, env.capabilities))
                            }
                        },
                        contentDescription = label,
                        size = size,
                    ) { ControlGlyph(control, env, Modifier.size(size * 0.5f)) }
                    if (control == ViewControl.More && menuOpen) MoreMenu(size) { menuOpen = false }
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
        ViewControl.Close -> CloseGlyph(modifier)
    }
}

/** The mobile app's extras, listed for now. */
@Composable
private fun MoreMenu(anchor: Dp, onDismiss: () -> Unit) {
    val density = LocalDensity.current
    Popup(
        alignment = Alignment.TopCenter,
        offset = with(density) { IntOffset(0, (anchor + 8.dp).roundToPx()) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            Modifier
                .width(220.dp)
                .shadow(24.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(SpicyColors.BgElevated)
                .padding(vertical = 6.dp),
        ) {
            // TODO: the lyrics source picker, translation and romanization style from the mobile app.
            for (entry in listOf("Lyrics source", "Translate lyrics", "Romanization style")) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                    BasicText(entry, style = TextStyle(color = SpicyColors.TextTertiary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
                    BasicText("Coming soon", style = TextStyle(color = SpicyColors.TextTertiary, fontSize = 12.sp))
                }
            }
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

private fun Float.toDp(density: Float): Dp = (this / density).dp
