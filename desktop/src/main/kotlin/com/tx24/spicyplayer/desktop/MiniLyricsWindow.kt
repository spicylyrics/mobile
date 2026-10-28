package com.tx24.spicyplayer.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
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
import com.tx24.spicyplayer.ui.controls.PlayPauseGlyph
import com.tx24.spicyplayer.ui.nowplaying.LyricsNotice
import com.tx24.spicyplayer.ui.nowplaying.NowPlaying
import com.tx24.spicyplayer.ui.nowplaying.PipDock
import com.tx24.spicyplayer.ui.nowplaying.PipSongCard
import com.tx24.spicyplayer.ui.settings.FileSettingsStore
import com.tx24.spicyplayer.ui.settings.PipWithoutLyrics
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
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
 * there are none to show. Dragged anywhere to move, from the bottom-right corner to resize;
 * hovering shows play/pause and a close button. Its place is kept in the settings store.
 *
 * [lyricsView] draws ready lyrics at the font scale it's given.
 */
@Composable
fun ApplicationScope.MiniLyricsWindow(
    nowPlaying: NowPlaying,
    lyrics: LyricsState,
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
        WindowDraggableArea {
            MiniLyricsContent(nowPlaying, lyrics, lyricsView, state)
        }
    }
}

@Composable
private fun MiniLyricsContent(
    nowPlaying: NowPlaying,
    lyrics: LyricsState,
    lyricsView: @Composable (Modifier, Float) -> Unit,
    window: WindowState,
) {
    val playing by nowPlaying.state.collectAsState()
    val settings = desktopSettings
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).hoverable(hover)) {
        // The song's cover, dimmed so the lyrics read over it.
        playing.artwork?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = COVER_DIM)))

        val track = playing.track
        val songCard = !settings.pipShowLyrics ||
            (lyrics is LyricsState.Error && settings.pipWithoutLyrics == PipWithoutLyrics.SongCard)
        when {
            songCard -> PipSongCard(track.title, track.artists, playing.artwork, null, playing.hasSession)
            lyrics is LyricsState.Ready -> {
                val staticBoost = if (lyrics.lyricsType == LyricsType.Static) {
                    LyricsLayoutMetrics.baseFontSizeSp(LyricsType.Syllable, maxWidth.value) /
                        LyricsLayoutMetrics.baseFontSizeSp(LyricsType.Static, maxWidth.value)
                } else 1f
                val scale = miniLyricsFontScale(maxWidth.value, maxHeight.value, settings.pipLyricsSize, staticBoost)
                // Square or taller, the song docks along the bottom, under the lyrics.
                val dock = settings.pipDock && maxHeight >= maxWidth
                Column(Modifier.fillMaxSize()) {
                    lyricsView(Modifier.fillMaxWidth().weight(1f), scale)
                    if (dock) PipDock(
                        track.title, track.artists, nowPlaying::positionMs, track.durationMs, playing.isPlaying,
                        cover = settings.pipDockCover, artwork = playing.artwork, hasSession = playing.hasSession,
                    )
                }
            }
            lyrics is LyricsState.Error -> LyricsNotice(lyrics, actions = null, bottomPx = { 0f })
            lyrics is LyricsState.Idle -> LyricsNotice("Waiting for a song", compact = true)
            else -> Unit
        }

        if (hovered) {
            HoverControls(playing.isPlaying, onPlayPause = nowPlaying::playPause, onClose = { MiniLyrics.open = false })
        }
        ResizeCorner(window, Modifier.align(Alignment.BottomEnd))
    }
}

/** Play/pause in the middle, close in the corner, while the pointer is over the window. */
@Composable
private fun HoverControls(isPlaying: Boolean, onPlayPause: () -> Unit, onClose: () -> Unit) {
    val latestPlaying by rememberUpdatedState(isPlaying)
    val glyph = remember { PlayPauseGlyph { if (latestPlaying) 1f else 0f } }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.Center)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onPlayPause),
            contentAlignment = Alignment.Center,
        ) {
            Image(glyph, if (isPlaying) "Pause" else "Play", Modifier.size(18.dp))
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            BasicText("×", style = TextStyle(color = Color.White, fontSize = 15.sp))
        }
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

private const val SAVE_AFTER_MS = 400L
private const val COVER_DIM = 0.55f
