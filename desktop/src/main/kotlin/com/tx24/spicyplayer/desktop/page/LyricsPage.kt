package com.tx24.spicyplayer.desktop.page

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
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
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.tx24.spicyplayer.desktop.Track
import com.tx24.spicyplayer.desktop.Transport
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.spicy.canvas.LyricsLayoutCalculator
import com.tx24.spicyplayer.ui.components.GlassButton
import com.tx24.spicyplayer.ui.components.LyricsSkeleton
import com.tx24.spicyplayer.ui.components.MoreGlyph
import com.tx24.spicyplayer.ui.components.SettingsGlyph
import com.tx24.spicyplayer.ui.components.SpicyGlyphs
import com.tx24.spicyplayer.ui.components.SwapSideGlyph
import com.tx24.spicyplayer.ui.components.ExpandGlyph
import com.tx24.spicyplayer.ui.controls.PlayPauseGlyph
import com.tx24.spicyplayer.ui.controls.SkipGlyph
import com.tx24.spicyplayer.ui.nowplaying.CompactHeaderMetrics
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage

/** What the lyrics area holds: nothing playing, or a track and its lyrics as far as they've got. */
sealed interface PageContent {
    data object Waiting : PageContent
    data class Playing(val track: Track, val lyrics: LyricsState) : PageContent
}

/** What the page asks the player and the app to do. */
class PageActions(
    val onState: (PageState) -> Unit,
    val onPlayPause: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onRetry: () -> Unit,
    val onOpenSettings: () -> Unit,
)

/**
 * The lyrics page: the [background] across the window, the now-playing bar (cover, name and
 * artists) beside the lyrics, the round view controls, and in cinema and fullscreen the cover's
 * playback row and timeline on hover. Below [PageState.COMPACT_BELOW_DP] off the page, the song
 * moves to a bar across the top instead.
 *
 * [lyricsView] draws ready lyrics into the box it's given, romanized or not, keeping the active
 * line's top at the given px (compact mode) or centred (null).
 */
@Composable
fun LyricsPage(
    state: PageState,
    content: PageContent,
    cover: File?,
    isPlaying: Boolean,
    positionMs: () -> Long,
    capabilities: Set<Transport>,
    actions: PageActions,
    background: @Composable () -> Unit,
    lyricsView: @Composable (modifier: Modifier, romanize: Boolean, activeLineTopPx: Float?) -> Unit,
) {
    val pageHover = remember { MutableInteractionSource() }
    val pageHovered by pageHover.collectIsHoveredAsState()
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).hoverable(pageHover)) {
        val density = LocalDensity.current.density
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val widthDp = widthPx / density
        val metrics = remember(widthPx, heightPx, density, state.mode) { PageMetrics(widthPx, heightPx, density, state.mode) }
        val art = rememberCover(cover)
        background()

        val playing = content as? PageContent.Playing
        val compact = state.isCompact(widthDp) && playing != null
        if (compact && playing != null) {
            CompactPage(state, playing, art, metrics, widthDp, pageHovered, actions, lyricsView)
            return@BoxWithConstraints
        }

        val withBar = state.nowBarOpen && playing != null
        val box = metrics.lyricsBox(withBar, state.nowBarOnRight)
        val lyricsModifier = Modifier.offset { IntOffset(box.leftPx.roundToInt(), 0) }.width(box.widthPx.toDp(density)).fillMaxHeight()
        when (content) {
            PageContent.Waiting -> LyricsNotice(
                "Waiting for a song",
                "Play something in Spotify or another player that shows in the system's media controls.",
                widthPx,
                Modifier.fillMaxSize(),
            )
            is PageContent.Playing -> LyricsArea(content, state, metrics, box.leftPx, box.widthPx, heightPx, lyricsModifier, actions, lyricsView)
        }
        if (withBar && playing != null) {
            NowBar(state, playing.track, art, metrics, widthDp, isPlaying, positionMs, capabilities, actions)
        }
        if (!state.immersive) {
            val alpha by animateFloatAsState(if (pageHovered) 1f else 0f, tween(300), label = "viewControls")
            ViewControlsRow(
                state, widthDp, metrics.pageControlSizePx.toDp(density), metrics.controlGapPx.toDp(density), actions,
                Modifier.fillMaxWidth().offset { IntOffset(0, metrics.pageControlsTopPx.roundToInt()) }.alpha(alpha),
            )
        }
    }
}

/** Lyrics, the skeleton while they load, or the notice in their place, inside the lyrics box. */
@Composable
private fun LyricsArea(
    content: PageContent.Playing,
    state: PageState,
    metrics: PageMetrics,
    boxLeftPx: Float,
    boxWidthPx: Float,
    pageHeightPx: Float,
    modifier: Modifier,
    actions: PageActions,
    lyricsView: @Composable (Modifier, Boolean, Float?) -> Unit,
    activeLineTopPx: Float? = null,
    topPx: Float = 0f,
) {
    val inset = boxWidthPx * CompactHeaderMetrics.LYRICS_SIDE_INSET
    when (val lyrics = content.lyrics) {
        is LyricsState.Ready -> lyricsView(modifier, state.romanized, activeLineTopPx)
        is LyricsState.Error -> LyricsNotice(
            lyrics.message,
            listOfNotNull(lyrics.body, lyrics.detail).joinToString("\n\n"),
            metrics.widthPx,
            modifier,
            onRetry = actions.onRetry.takeIf { lyrics.kind.retry },
            report = if (lyrics.kind.report) noticeReport(content.track, lyrics) else null,
        )
        // `.SkeletonLines { padding: 25cqh ... }`: the first bar where the first line will rest.
        else -> LyricsSkeleton(
            lineSizeSp = metrics.lyricFontSizeSp,
            lineStartPx = boxLeftPx + inset,
            lineMaxWidthPx = boxWidthPx - 2f * inset,
            topPx = topPx + (pageHeightPx - topPx) * 0.25f,
        )
    }
}

/** The details "Copy details" puts on the clipboard. */
private fun noticeReport(track: Track, error: LyricsState.Error): String = listOfNotNull(
    "Notice: ${error.kind.slug}${error.code?.let { " ($it)" }.orEmpty()}",
    "Track: ${track.title} by ${track.artist}",
    "Player: ${track.app}",
    error.detail,
).joinToString("\n")

/**
 * The now-playing bar: the cover and, centred under it, the name (`font-weight: 900`, .95) and
 * the artists (.7). Off the page, hovering the cover darkens it and brings up the view controls
 * along its top and the playback row and timeline along its bottom.
 */
@Composable
private fun NowBar(
    state: PageState,
    track: Track,
    art: ImageBitmap?,
    metrics: PageMetrics,
    widthDp: Float,
    isPlaying: Boolean,
    positionMs: () -> Long,
    capabilities: Set<Transport>,
    actions: PageActions,
) {
    val density = LocalDensity.current.density
    val left = metrics.coverLeftPx(state.nowBarOnRight)
    val coverSize = metrics.coverSizePx.toDp(density)
    val coverHover = remember { MutableInteractionSource() }
    val coverHovered by coverHover.collectIsHoveredAsState()
    val overlay by animateFloatAsState(if (state.immersive && coverHovered) 1f else 0f, tween(300), label = "coverControls")
    Box(
        Modifier
            .offset { IntOffset(left.roundToInt(), metrics.coverTopPx.roundToInt()) }
            .size(coverSize)
            .hoverable(coverHover),
    ) {
        val shape = RoundedCornerShape(metrics.coverCornerPx.toDp(density))
        Cover(art, Modifier.fillMaxSize().shadow(20.dp, shape, clip = false).clip(shape).alpha(0.95f))
        if (overlay > 0f) {
            Box(Modifier.fillMaxSize().clip(shape).background(Color.Black.copy(alpha = 0.45f * overlay)))
            Box(Modifier.fillMaxSize().alpha(overlay)) {
                ViewControlsRow(
                    state, widthDp, metrics.coverControlSizePx.toDp(density), metrics.controlGapPx.toDp(density), actions,
                    Modifier.fillMaxWidth().offset { IntOffset(0, metrics.coverControlsTopPx.roundToInt()) },
                )
                CoverPlayback(track, metrics, isPlaying, positionMs, capabilities, actions, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
    SongText(
        track,
        metrics.titleSizeSp, metrics.titleLineHeightSp, metrics.artistsSizeSp, metrics.artistsLineHeightSp,
        Alignment.CenterHorizontally,
        Modifier.offset { IntOffset(left.roundToInt(), metrics.textTopPx.roundToInt()) }.width(coverSize),
    )
}

/** The song's name over its artists, one line each, cut off with an ellipsis. */
@Composable
private fun SongText(
    track: Track,
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
            track.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = Color.White.copy(alpha = 0.95f), fontSize = titleSp.sp, fontWeight = FontWeight.Black, fontFamily = family,
                lineHeight = titleLineSp.sp, textAlign = textAlign),
        )
        BasicText(
            track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = artistsSp.sp, fontWeight = FontWeight.Normal, fontFamily = family,
                lineHeight = artistsLineSp.sp, textAlign = textAlign),
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
private fun CoverPlayback(
    track: Track,
    metrics: PageMetrics,
    isPlaying: Boolean,
    positionMs: () -> Long,
    capabilities: Set<Transport>,
    actions: PageActions,
    modifier: Modifier,
) {
    val density = LocalDensity.current.density
    var now by remember { mutableLongStateOf(positionMs()) }
    LaunchedEffect(Unit) { while (true) withFrameMillis { now = positionMs() } }
    val pause by animateFloatAsState(if (isPlaying) 1f else 0f, tween(200), label = "playPause")
    val skip = remember { SkipGlyph { 0f } }
    val playPause = remember { PlayPauseGlyph { pause } }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = metrics.playbackRowInsetPx.toDp(density)).height(metrics.playbackRowHeightPx.toDp(density)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (slot in transportSlots(capabilities)) {
                val width = if (slot.button == TransportButton.PlayPause) metrics.playPauseWidthPx else metrics.skipWidthPx
                val onClick = when (slot.button) {
                    TransportButton.PlayPause -> actions.onPlayPause
                    // TODO: previous and next once a platform's source takes them.
                    TransportButton.Previous, TransportButton.Next -> ({})
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
        Timeline(now, track.durationMs, canSeek(capabilities), metrics, actions.onSeek)
    }
}

/** `.Timeline`: the position, the bar (white for what has played), and the length. */
@Composable
private fun Timeline(positionMs: Long, durationMs: Long, seekable: Boolean, metrics: PageMetrics, onSeek: (Long) -> Unit) {
    val density = LocalDensity.current.density
    val timeStyle = TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = metrics.baseFontSizeSp.sp, fontWeight = FontWeight.Medium,
        fontFamily = LyricsLayoutCalculator.spicyFontFamily)
    var barWidth by remember { mutableStateOf(1) }
    // While dragging, the bar follows the pointer rather than the player.
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val progress = dragFraction ?: progressOf(positionMs, durationMs)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = metrics.timelineInsetXPx.toDp(density), vertical = metrics.timelineInsetYPx.toDp(density)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(formatPlaybackTime(positionMs), Modifier.widthIn(min = (metrics.baseFontSizeSp * 2.5f).dp), style = timeStyle.copy(textAlign = TextAlign.End))
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = 6.dp)
                .height((metrics.timelineBarHeightPx.toDp(density)).coerceAtLeast(3.dp) + 10.dp)
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
            val barHeight = metrics.timelineBarHeightPx.toDp(density).coerceAtLeast(3.dp)
            val pill = RoundedCornerShape(50)
            Box(Modifier.fillMaxWidth().height(barHeight).clip(pill).background(Color.White.copy(alpha = 0.18f)))
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(barHeight).clip(pill).background(Color.White.copy(alpha = 0.95f)))
        }
        BasicText(formatPlaybackTime(durationMs), Modifier.widthIn(min = (metrics.baseFontSizeSp * 2.5f).dp), style = timeStyle)
    }
}

/**
 * Compact mode, off the page in a narrow window: the song as a bar across the top (cover, then
 * name and artists), the view controls at its end on hover, and the lyrics below, the active
 * line held near their top.
 */
@Composable
private fun CompactPage(
    state: PageState,
    playing: PageContent.Playing,
    art: ImageBitmap?,
    metrics: PageMetrics,
    widthDp: Float,
    pageHovered: Boolean,
    actions: PageActions,
    lyricsView: @Composable (Modifier, Boolean, Float?) -> Unit,
) {
    val density = LocalDensity.current.density
    val bar = remember(metrics) { CompactHeaderMetrics(metrics.widthPx, metrics.heightPx, density, metrics.lyricFontSizeSp) }
    val lyricsTop = bar.lyricsTopPx
    LyricsArea(
        playing, state, metrics, 0f, metrics.widthPx, metrics.heightPx,
        Modifier.fillMaxSize().padding(top = lyricsTop.toDp(density)), actions, lyricsView,
        activeLineTopPx = bar.activeLineTopPx, topPx = lyricsTop,
    )
    val shape = RoundedCornerShape((bar.artSizePx * CompactHeaderMetrics.ART_CORNER_FRACTION).toDp(density))
    Cover(
        art,
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
        SongText(playing.track, bar.titleSizeSp, bar.titleLineHeightSp, bar.artistsSizeSp, bar.artistsLineHeightSp, Alignment.Start, Modifier)
    }
    val alpha by animateFloatAsState(if (pageHovered) 1f else 0f, tween(300), label = "compactControls")
    Box(Modifier.fillMaxWidth().padding(top = metrics.pageControlsTopPx.toDp(density), end = bar.contentStartPx.toDp(density)).alpha(alpha), contentAlignment = Alignment.TopEnd) {
        ViewControlsRow(state, widthDp, metrics.pageControlSizePx.toDp(density), metrics.controlGapPx.toDp(density), actions, Modifier)
    }
}

/** The round glass view controls [viewControls] picks for [state], with their tooltips; More opens its menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ViewControlsRow(state: PageState, widthDp: Float, size: Dp, gap: Dp, actions: PageActions, modifier: Modifier) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        for (control in viewControls(state)) {
            TooltipArea(
                tooltip = { Tooltip(tooltip(control, state, widthDp)) },
                delayMillis = 400,
                tooltipPlacement = TooltipPlacement.ComponentRect(Alignment.BottomCenter, Alignment.BottomCenter, DpOffset(0.dp, 6.dp)),
            ) {
                Box {
                    GlassButton(
                        onClick = {
                            when (control) {
                                ViewControl.More -> menuOpen = true
                                ViewControl.Settings -> actions.onOpenSettings()
                                else -> actions.onState(state.click(control, widthDp))
                            }
                        },
                        contentDescription = tooltip(control, state, widthDp),
                        size = size,
                    ) { ControlGlyph(control, state, widthDp, Modifier.size(size * 0.5f)) }
                    if (control == ViewControl.More && menuOpen) MoreMenu(size) { menuOpen = false }
                }
            }
        }
    }
}

@Composable
private fun ControlGlyph(control: ViewControl, state: PageState, widthDp: Float, modifier: Modifier) {
    when (control) {
        ViewControl.CinemaView -> ExpandGlyph(expanded = false, modifier)
        ViewControl.CompactMode -> CompactGlyph(state.isCompact(widthDp), modifier)
        ViewControl.Romanization -> Image(
            if (state.romanized) SpicyGlyphs.DisableRomanization else SpicyGlyphs.EnableRomanization,
            contentDescription = null,
            modifier = modifier,
        )
        ViewControl.NowBar -> NowBarGlyph(state.nowBarOnRight, modifier)
        ViewControl.NowBarSide -> SwapSideGlyph(state.nowBarOnRight, modifier)
        ViewControl.Fullscreen -> ExpandGlyph(expanded = state.mode == ViewMode.Fullscreen, modifier)
        ViewControl.More -> MoreGlyph(modifier)
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
                .background(Color(28, 28, 32).copy(alpha = 0.94f))
                .padding(vertical = 6.dp),
        ) {
            // TODO: the lyrics source picker, translation and romanization style from the mobile app.
            for (entry in listOf("Lyrics source", "Translate lyrics", "Romanization style")) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                    BasicText(entry, style = TextStyle(color = Color.White.copy(alpha = 0.42f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
                    BasicText("Coming soon", style = TextStyle(color = Color.White.copy(alpha = 0.35f), fontSize = 12.sp))
                }
            }
        }
    }
}

@Composable
private fun Tooltip(text: String) {
    BasicText(
        text,
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(28, 28, 32).copy(alpha = 0.88f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        style = TextStyle(color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp, fontWeight = FontWeight.Medium),
    )
}

/**
 * `.LyricsNotice`: the title (1.85em bold, tight), the explanation (secondary, 1.55 line), and
 * "Try again" and "Copy details" where they help, centred in the lyrics area at
 * `clamp(14px, 1.45cqw, 19px)`.
 */
@Composable
private fun LyricsNotice(
    title: String,
    body: String,
    pageWidthPx: Float,
    modifier: Modifier,
    onRetry: (() -> Unit)? = null,
    report: String? = null,
) {
    val density = LocalDensity.current.density
    val base = (pageWidthPx / density * 0.0145f).coerceIn(14f, 19f)
    Box(modifier.padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = (base * 34f).dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(
                title,
                style = TextStyle(color = Color.White.copy(alpha = 0.92f), fontSize = (base * 1.85f).sp, fontWeight = FontWeight.Bold,
                    lineHeight = 1.15.em, letterSpacing = (-0.025).em, textAlign = TextAlign.Center),
            )
            if (body.isNotBlank()) BasicText(
                body,
                Modifier.padding(top = (base * 1.85f * 0.85f).dp),
                style = TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = base.sp, fontWeight = FontWeight.Normal, lineHeight = 1.55.em, textAlign = TextAlign.Center),
            )
            if (onRetry != null || report != null) Row(
                Modifier.padding(top = (base * 1.6f).dp),
                horizontalArrangement = Arrangement.spacedBy((base * 0.6f).dp),
            ) {
                if (onRetry != null) NoticeAction("Try again", base, primary = true, onRetry)
                if (report != null) {
                    var copied by remember(report) { mutableStateOf(false) }
                    NoticeAction(if (copied) "Copied" else "Copy details", base, primary = onRetry == null) {
                        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(report), null)
                        copied = true
                    }
                }
            }
        }
    }
}

/** `.notice-action`: a pill, `height: 2.6em; padding: 0 1.15em`, semibold at .9em. */
@Composable
private fun NoticeAction(label: String, base: Float, primary: Boolean, onClick: () -> Unit) {
    val fontSize = base * 0.9f
    Box(
        Modifier
            .height((fontSize * 2.6f).dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = if (primary) 0.1f else 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = (fontSize * 1.15f).dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = TextStyle(color = Color.White.copy(alpha = 0.92f), fontSize = fontSize.sp, fontWeight = FontWeight.SemiBold))
    }
}

/** The cover file decoded off the main thread; the last one stays up while the next decodes. */
@Composable
private fun rememberCover(file: File?): ImageBitmap? {
    val image by produceState<ImageBitmap?>(null, file) {
        if (file == null) {
            value = null
            return@produceState
        }
        val decoded = withContext(Dispatchers.IO) {
            runCatching { SkiaImage.makeFromEncoded(file.readBytes()).toComposeImageBitmap() }.getOrNull()
        }
        if (decoded != null) value = decoded
    }
    return image
}

/** F11 and Escape as the page's keys; null for anything else. */
fun pageKeyOf(event: KeyEvent): PageKey? {
    if (event.type != KeyEventType.KeyDown) return null
    return when (event.key) {
        Key.F11 -> PageKey.F11
        Key.Escape -> PageKey.Escape
        else -> null
    }
}

private fun Float.toDp(density: Float): Dp = (this / density).dp
