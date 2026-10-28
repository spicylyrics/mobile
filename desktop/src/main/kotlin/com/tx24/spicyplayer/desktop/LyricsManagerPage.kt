package com.tx24.spicyplayer.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.tx24.spicyplayer.ui.lyricsmanager.LibraryTrack
import java.awt.Frame
import com.tx24.spicyplayer.ui.lyricsmanager.LocalLyricsFiles
import com.tx24.spicyplayer.ui.lyricsmanager.LyricsLibraryPanel
import com.tx24.spicyplayer.ui.settings.DesktopPlatformActions
import com.tx24.spicyplayer.ui.settings.LocalPlatformActions

/** The playing song as the library keys it. */
fun Track.asLibraryTrack() = LibraryTrack(title, artist, album, durationMs)

/**
 * The Lyrics Manager as a page of the main [window]: [library]'s songs, uploads for the song
 * [lyrics] is showing, which looks its lyrics up again when they change. [onClose] goes back.
 */
@Composable
fun LyricsManagerPage(window: Frame, library: DesktopLyricsLibrary, lyrics: DesktopLyrics, onClose: () -> Unit) {
    val settings = desktopSettings
    val shown by lyrics.shown.collectAsState()
    val track = shown?.track
    val platform = remember(window) { DesktopPlatformActions(window) }
    val files = remember(window) { DesktopLyricsFiles(platform, window) }
    CompositionLocalProvider(LocalPlatformActions provides platform, LocalLyricsFiles provides files) {
        LyricsLibraryPanel(
            library = library,
            current = track?.asLibraryTrack(),
            theme = settings.popupTheme,
            saveUploads = settings.ttmlUploadSaves,
            onSaveUploadsChange = { settings.ttmlUploadSaves = it },
            onLyricsChanged = { lyrics.shown.value?.track?.let(lyrics::load) },
            onClose = onClose,
        )
    }
}
