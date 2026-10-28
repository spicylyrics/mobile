package com.tx24.spicyplayer.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.tx24.spicyplayer.ui.lyricsmanager.LibraryTrack
import com.tx24.spicyplayer.ui.lyricsmanager.LocalLyricsFiles
import com.tx24.spicyplayer.ui.lyricsmanager.LyricsLibraryPanel
import com.tx24.spicyplayer.ui.settings.DesktopPlatformActions
import com.tx24.spicyplayer.ui.settings.LocalPlatformActions

/** Whether the Lyrics Manager is open; Ctrl+L (Cmd+L on a Mac) opens it. */
class LyricsManagerWindowState {
    var open by mutableStateOf(false)

    /** For a window's `onPreviewKeyEvent`: opens the Lyrics Manager on the shortcut. */
    fun onKey(event: KeyEvent): Boolean {
        val shortcut = event.type == KeyEventType.KeyDown && event.key == Key.L && (event.isCtrlPressed || event.isMetaPressed)
        if (shortcut) open = true
        return shortcut
    }
}

/** The playing song as the library keys it. */
fun Track.asLibraryTrack() = LibraryTrack(title, artist, album, durationMs)

/**
 * The Lyrics Manager in a window of its own, while [state] is open: [library]'s songs, uploads
 * for the song [lyrics] is showing, which looks its lyrics up again when they change.
 */
@Composable
fun ApplicationScope.LyricsManagerWindow(state: LyricsManagerWindowState, library: DesktopLyricsLibrary, lyrics: DesktopLyrics) {
    if (!state.open) return
    Window(
        onCloseRequest = { state.open = false },
        state = rememberWindowState(size = DpSize(720.dp, 640.dp)),
        title = "Lyrics Manager",
        onPreviewKeyEvent = state::onKey,
    ) {
        val settings = desktopSettings
        val shown by lyrics.shown.collectAsState()
        val track = shown?.track
        val platform = remember { DesktopPlatformActions(window) }
        val files = remember { DesktopLyricsFiles(platform, window) }
        CompositionLocalProvider(LocalPlatformActions provides platform, LocalLyricsFiles provides files) {
            LyricsLibraryPanel(
                library = library,
                current = track?.asLibraryTrack(),
                theme = settings.popupTheme,
                saveUploads = settings.ttmlUploadSaves,
                onSaveUploadsChange = { settings.ttmlUploadSaves = it },
                onLyricsChanged = { lyrics.shown.value?.track?.let(lyrics::load) },
                onClose = { state.open = false },
            )
        }
    }
}
