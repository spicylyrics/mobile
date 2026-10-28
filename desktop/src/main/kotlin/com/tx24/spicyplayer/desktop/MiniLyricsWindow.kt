package com.tx24.spicyplayer.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.spicy.canvas.LyricsLayoutMetrics
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsType
import com.tx24.spicyplayer.ui.bigscreen.PlayerCapabilities
import com.tx24.spicyplayer.ui.bigscreen.nextRepeat
import com.tx24.spicyplayer.ui.bigscreen.playbackToggles
import com.tx24.spicyplayer.ui.bigscreen.progressOf
import com.tx24.spicyplayer.ui.components.SpicyGlyphs
import com.tx24.spicyplayer.ui.controls.PlayPauseGlyph
import com.tx24.spicyplayer.ui.controls.SkipGlyph
import com.tx24.spicyplayer.ui.nowplaying.LyricsNotice
import com.tx24.spicyplayer.ui.nowplaying.NowPlaying
import com.tx24.spicyplayer.ui.nowplaying.NowPlayingState
import com.tx24.spicyplayer.ui.nowplaying.PipSongCard
import com.tx24.spicyplayer.ui.nowplaying.RepeatState
import com.tx24.spicyplayer.ui.settings.FileSettingsStore
import com.tx24.spicyplayer.ui.settings.PipWithoutLyrics
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.event.WindowAdapter
import java.util.Locale
import java.awt.event.WindowEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop

/** Whether the mini lyrics window is open; Ctrl+M (Cmd+M on a Mac) opens and closes it. */
object MiniLyrics {
    var open by mutableStateOf(false)

    /** For a window's key handler: toggles the mini window on the shortcut. */
    fun onKey(event: KeyEvent): Boolean {
        val shortcut = event.type == KeyEventType.KeyDown && event.key == Key.M && (event.isCtrlPressed || event.isMetaPressed)
        if (shortcut) open = !open
        return shortcut
    }
}

/**
 * The phone's floating-window lyrics on the desktop: a small, undecorated window that stays on
 * top, over the song's cover, dimmed. It shows the lyrics at the floating window's size (the
 * floating-window settings apply: Lyrics Size, the song card, the dock), or the song's card when
 * there are none to show. Dragged anywhere to move, from the bottom-right corner to resize.
 *
 * The dock under the lyrics names the song, with its times when the window is tall enough
 * ([miniChrome]). Moving the pointer raises the transport over the lyrics' foot: previous,
 * play/pause and next, with shuffle and repeat at its ends when the window is wide enough and the
 * player takes them ([capabilities]). Hovering shows the romanize toggle and close in the corner.
 * Its place is kept in the settings store; [onFocus] runs whenever it gains focus.
 *
 * [lyricsView] draws ready lyrics at the font scale it's given.
 */
@Composable
fun ApplicationScope.MiniLyricsWindow(
    nowPlaying: NowPlaying,
    capabilities: PlayerCapabilities,
    lyrics: LyricsState,
    onFocus: () -> Unit = {},
    lyricsView: @Composable (modifier: Modifier, fontSizeScale: Float) -> Unit,
) {
    if (!MiniLyrics.open) return
    val store = FileSettingsStore.default()
    val initial = remember { MiniLyricsPlacement.load(store, usableScreens()) }
    val state = rememberWindowState(
        position = WindowPosition(initial.x.dp, initial.y.dp),
        size = DpSize(initial.width.dp, initial.height.dp),
    )
    // Saved once it has settled after a move or a resize.
    LaunchedEffect(state) {
        snapshotFlow { state.position to state.size }.drop(1).collectLatest { (position, size) ->
            delay(SAVE_AFTER_MS)
            if (position is WindowPosition.Absolute) {
                MiniLyricsPlacement.save(
                    store,
                    WindowBounds(position.x.value.toInt(), position.y.value.toInt(), size.width.value.toInt(), size.height.value.toInt()),
                )
            }
        }
    }
    Window(
        onCloseRequest = { MiniLyrics.open = false },
        state = state,
        title = "Mini lyrics",
        undecorated = true,
        alwaysOnTop = true,
        resizable = true,
        onKeyEvent = MiniLyrics::onKey,
    ) {
        OnWindowFocus(window, onFocus)
        WindowDraggableArea {
            MiniLyricsContent(nowPlaying, capabilities, lyrics, lyricsView, state)
        }
    }
}

/** Runs [onFocus] each time [window] gains focus. */
@Composable
fun OnWindowFocus(window: java.awt.Window, onFocus: () -> Unit) {
    val latest by rememberUpdatedState(onFocus)
    DisposableEffect(window) {
        val listener = object : WindowAdapter() {
            override fun windowGainedFocus(e: WindowEvent?) = latest()
        }
        window.addWindowFocusListener(listener)
        onDispose { window.removeWindowFocusListener(listener) }
    }
}

@Composable
private fun MiniLyricsContent(
    nowPlaying: NowPlaying,
    capabilities: PlayerCapabilities,
    lyrics: LyricsState,
    lyricsView: @Composable (Modifier, Float) -> Unit,
    window: WindowState,
) {
    val playing by nowPlaying.state.collectAsState()
    val settings = desktopSettings
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    // Each pointer move raises the transport; it sinks again a while after the last one.
    var moves by remember { mutableIntStateOf(0) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .hoverable(hover)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Move) moves++
                    }
                }
            },
    ) {
        val chrome = miniChrome(maxWidth.value, maxHeight.value)
        // The song's cover, dimmed so the lyrics read over it.
        playing.artwork?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = COVER_DIM)))

        val track = playing.track
        val songCard = !settings.pipShowLyrics ||
            (lyrics is LyricsState.Error && settings.pipWithoutLyrics == PipWithoutLyrics.SongCard)
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when {
                    songCard -> PipSongCard(track.title, track.artists, playing.artwork, null, playing.hasSession)
                    lyrics is LyricsState.Ready -> {
                        val staticBoost = if (lyrics.lyricsType == LyricsType.Static) {
                            LyricsLayoutMetrics.baseFontSizeSp(LyricsType.Syllable, maxWidth.value) /
                                LyricsLayoutMetrics.baseFontSizeSp(LyricsType.Static, maxWidth.value)
                        } else 1f
                        val scale = miniLyricsFontScale(maxWidth.value, maxHeight.value, settings.pipLyricsSize, staticBoost)
                        lyricsView(Modifier.fillMaxSize(), scale)
                    }
                    lyrics is LyricsState.Error -> LyricsNotice(lyrics, actions = null, bottomPx = { 0f })
                    lyrics is LyricsState.Idle -> LyricsNotice("Waiting for a song", compact = true)
                    else -> Unit
                }
                if (playing.hasSession) {
                    RisingTransport(moves, playing, capabilities, chrome, nowPlaying, Modifier.align(Alignment.BottomCenter))
                }
            }
            if (settings.pipDock && !songCard && playing.hasSession) {
                MiniDock(playing, nowPlaying::positionMs, showTimes = chrome.times, cover = settings.pipDockCover)
            }
        }

        if (hovered) {
            ViewControls(
                romanized = settings.romanize,
                onToggleRomanize = { settings.romanize = !settings.romanize },
                onClose = { MiniLyrics.open = false },
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
        ResizeCorner(window, Modifier.align(Alignment.BottomEnd))
    }
}

/** The song at rest under the lyrics: its title and artist, and its times when [showTimes]. */
@Composable
private fun MiniDock(playing: NowPlayingState, positionMs: () -> Long, showTimes: Boolean, cover: Boolean) {
    Row(
        Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val art = playing.artwork
        if (cover && art != null) {
            Image(art, null, Modifier.size(34.dp).clip(RoundedCornerShape(5.dp)), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            BasicText(
                playing.track.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            )
            BasicText(
                playing.track.artists, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp),
            )
            if (showTimes && playing.track.durationMs > 0) MiniTimes(positionMs, playing.track.durationMs, playing.isPlaying)
        }
    }
}

/** A thin progress bar with the elapsed and total times under it, ticking while playing. */
@Composable
private fun MiniTimes(positionMs: () -> Long, durationMs: Long, isPlaying: Boolean) {
    var now by remember { mutableLongStateOf(positionMs()) }
    LaunchedEffect(isPlaying) {
        now = positionMs()
        while (isPlaying) {
            delay(250)
            now = positionMs()
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f))) {
            Box(Modifier.fillMaxWidth(progressOf(now, durationMs)).fillMaxHeight().background(Color.White.copy(alpha = 0.85f)))
        }
        Row(Modifier.fillMaxWidth().padding(top = 3.dp)) {
            val style = TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
            BasicText(miniTime(now), style = style)
            Spacer(Modifier.weight(1f))
            BasicText(miniTime(durationMs), style = style)
        }
    }
}

/**
 * Previous, play/pause and next, risen over the lyrics' foot after each pointer move ([moves]) and
 * sunk again [TRANSPORT_HOLD_MS] after the last, or kept up while the pointer is on it. Shuffle and
 * repeat sit at its ends where [chrome] has room and the player takes them.
 */
@Composable
private fun RisingTransport(
    moves: Int,
    playing: NowPlayingState,
    capabilities: PlayerCapabilities,
    chrome: MiniChrome,
    nowPlaying: NowPlaying,
    modifier: Modifier,
) {
    val hover = remember { MutableInteractionSource() }
    val onIt by hover.collectIsHoveredAsState()
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(moves, onIt) {
        if (moves == 0) return@LaunchedEffect
        shown = true
        if (onIt) return@LaunchedEffect
        delay(TRANSPORT_HOLD_MS)
        shown = false
    }
    AnimatedVisibility(
        shown,
        modifier,
        enter = slideInVertically { it / 2 } + fadeIn(),
        exit = slideOutVertically { it / 2 } + fadeOut(),
    ) {
        val toggles = playbackToggles(capabilities, playing)
        val latestPlaying by rememberUpdatedState(playing.isPlaying)
        val playGlyph = remember { PlayPauseGlyph { if (latestPlaying) 1f else 0f } }
        val skipGlyph = remember { SkipGlyph { 0f } }
        Row(
            Modifier
                .padding(bottom = 10.dp)
                .hoverable(hover)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val shuffle = toggles.shuffle
            if (chrome.modes && shuffle != null) {
                ControlButton(if (shuffle) "Shuffle off" else "Shuffle on", onClick = { nowPlaying.setShuffle(!shuffle) }) {
                    Image(SpicyGlyphs.Shuffle, null, Modifier.size(18.dp).alpha(if (shuffle) 1f else OFF_ALPHA))
                }
            }
            ControlButton("Previous", enabled = capabilities.skipPrevious, onClick = nowPlaying::skipPrevious) {
                Image(skipGlyph, null, Modifier.size(width = 21.dp, height = 12.dp).graphicsLayer { scaleX = -1f })
            }
            ControlButton(if (playing.isPlaying) "Pause" else "Play", enabled = capabilities.playPause, size = 40.dp, onClick = nowPlaying::playPause) {
                Image(playGlyph, null, Modifier.size(18.dp))
            }
            ControlButton("Next", enabled = capabilities.skipNext, onClick = nowPlaying::skipNext) {
                Image(skipGlyph, null, Modifier.size(width = 21.dp, height = 12.dp))
            }
            val repeat = toggles.repeat
            if (chrome.modes && repeat != null) {
                ControlButton("Repeat", onClick = { nowPlaying.setRepeat(nextRepeat(repeat, playing.repeatOneSupported)) }) {
                    Image(
                        if (repeat == RepeatState.One) SpicyGlyphs.RepeatOne else SpicyGlyphs.Repeat,
                        null,
                        Modifier.size(18.dp).alpha(if (repeat == RepeatState.Off) OFF_ALPHA else 1f),
                    )
                }
            }
        }
    }
}

/** Romanize and close, in the corner while the pointer is over the window. */
@Composable
private fun ViewControls(romanized: Boolean, onToggleRomanize: () -> Unit, onClose: () -> Unit, modifier: Modifier) {
    Row(modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ControlButton(if (romanized) "Show original script" else "Romanize", size = 26.dp, background = true, onClick = onToggleRomanize) {
            Image(if (romanized) SpicyGlyphs.DisableRomanization else SpicyGlyphs.EnableRomanization, null, Modifier.size(15.dp))
        }
        ControlButton("Close", size = 26.dp, background = true, onClick = onClose) {
            BasicText("×", style = TextStyle(color = Color.White, fontSize = 15.sp))
        }
    }
}

/** A round button; a disabled one is dimmed and takes no clicks. */
@Composable
private fun ControlButton(
    description: String,
    enabled: Boolean = true,
    size: Dp = 32.dp,
    background: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .then(if (background) Modifier.background(Color.Black.copy(alpha = 0.55f)) else Modifier)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .semantics { contentDescription = description }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** A corner to drag the window's size by: an undecorated window has no frame to take hold of. */
@Composable
private fun ResizeCorner(window: WindowState, modifier: Modifier) {
    val density = LocalDensity.current
    Box(
        modifier
            .size(18.dp)
            .pointerInput(window) {
                detectDragGestures { change, amount ->
                    change.consume()
                    val size = window.size
                    window.size = DpSize(
                        (size.width + with(density) { amount.x.toDp() }).coerceAtLeast(MiniLyricsPlacement.MIN_WIDTH.dp),
                        (size.height + with(density) { amount.y.toDp() }).coerceAtLeast(MiniLyricsPlacement.MIN_HEIGHT.dp),
                    )
                }
            },
    )
}

/** Each screen's area apart from its taskbar, the primary first, in logical pixels. */
private fun usableScreens(): List<WindowBounds> {
    val environment = GraphicsEnvironment.getLocalGraphicsEnvironment()
    if (GraphicsEnvironment.isHeadless()) return emptyList()
    val primary = environment.defaultScreenDevice
    return (listOf(primary) + environment.screenDevices.filter { it != primary }).map { device ->
        val config = device.defaultConfiguration
        val bounds = config.bounds
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(config)
        WindowBounds(
            bounds.x + insets.left,
            bounds.y + insets.top,
            bounds.width - insets.left - insets.right,
            bounds.height - insets.top - insets.bottom,
        )
    }
}

/**
 * Which of the mini window's extras fit a [widthDp] by [heightDp] window: shuffle and repeat need
 * [MiniChrome.MODES_MIN_WIDTH], the dock's times [MiniChrome.TIMES_MIN_HEIGHT].
 */
data class MiniChrome(val modes: Boolean, val times: Boolean) {
    companion object {
        const val MODES_MIN_WIDTH = 280f
        const val TIMES_MIN_HEIGHT = 230f
    }
}

fun miniChrome(widthDp: Float, heightDp: Float) = MiniChrome(
    modes = widthDp >= MiniChrome.MODES_MIN_WIDTH,
    times = heightDp >= MiniChrome.TIMES_MIN_HEIGHT,
)

/** [ms] as m:ss, or h:mm:ss from an hour. */
fun miniTime(ms: Long): String {
    val total = ms.coerceAtLeast(0L) / 1000
    val h = total / 3600
    val m = total / 60 % 60
    val sec = total % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) else String.format(Locale.ROOT, "%d:%02d", m, sec)
}

private const val SAVE_AFTER_MS = 400L
private const val COVER_DIM = 0.55f
private const val TRANSPORT_HOLD_MS = 2500L
private const val OFF_ALPHA = 0.5f
private const val DISABLED_ALPHA = 0.35f
