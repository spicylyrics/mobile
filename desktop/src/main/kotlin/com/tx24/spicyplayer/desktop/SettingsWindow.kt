package com.tx24.spicyplayer.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import java.io.File
import javax.swing.JOptionPane
import com.tx24.spicyplayer.ui.components.LocalPopupPalette
import com.tx24.spicyplayer.ui.settings.CommonAppSettings
import com.tx24.spicyplayer.ui.settings.DesktopPlatformActions
import com.tx24.spicyplayer.ui.settings.FileSettingsStore
import com.tx24.spicyplayer.ui.settings.LocalPlatformActions
import com.tx24.spicyplayer.ui.settings.LyricsFont
import com.tx24.spicyplayer.ui.settings.PlatformActions
import com.tx24.spicyplayer.ui.settings.panel.PanelActions
import com.tx24.spicyplayer.ui.settings.panel.SettingsPanel
import com.tx24.spicyplayer.ui.settings.panel.settingsPanelSections

/** The app's settings, in the user's app data folder; read once, saved as they change. */
val desktopSettings: CommonAppSettings by lazy {
    CommonAppSettings(FileSettingsStore.default(), pipButtonByDefault = false, prereleasesByDefault = true)
}

/**
 * The settings rows that do nothing on desktop: the phone's header and its expanded cover, the
 * background's older look (the desktop only has the refined one), in-app profiles, and the
 * phone's interface animations.
 */
val DESKTOP_HIDDEN_ROWS = setOf("headerSize", "refinedBackground", "profilesInBrowser", "expandWithoutLyrics", "uiAnimations")

/** Asks for a font file and makes a copy of it the Custom lyrics font, or says it isn't one. */
private fun pickLyricsFont(settings: CommonAppSettings, platform: PlatformActions) {
    platform.pickFile(DesktopFontFile.MIME_TYPES) { path ->
        val source = path?.let(::File) ?: return@pickFile
        val imported = DesktopFontFile.import(source)
        if (imported == null) {
            JOptionPane.showMessageDialog(null, "That file isn't a font the app can read. Pick a .ttf or .otf file.", "Lyrics Font", JOptionPane.WARNING_MESSAGE)
            return@pickFile
        }
        settings.customFontFile = imported.fileName
        settings.customFontName = imported.displayName
        settings.lyricsFont = LyricsFont.Custom
    }
}

/** Whether the settings window is open; Ctrl+, (Cmd+, on a Mac) opens it. */
class SettingsWindowState {
    var open by mutableStateOf(false)

    /** For a window's `onPreviewKeyEvent`: opens settings on the shortcut. */
    fun onKey(event: KeyEvent): Boolean {
        val shortcut = event.type == KeyEventType.KeyDown && event.key == Key.Comma && (event.isCtrlPressed || event.isMetaPressed)
        if (shortcut) open = true
        return shortcut
    }
}

/** The settings panel in a window of its own, while [state] is open. */
@Composable
fun ApplicationScope.SettingsWindow(state: SettingsWindowState) {
    if (!state.open) return
    Window(
        onCloseRequest = { state.open = false },
        state = rememberWindowState(size = DpSize(940.dp, 700.dp)),
        title = "Settings",
        onPreviewKeyEvent = state::onKey,
    ) {
        val settings = desktopSettings
        val platform = remember { DesktopPlatformActions(window) }
        val actions = remember {
            PanelActions(
                // Set by the installer's launcher; a run from Gradle has none.
                version = System.getProperty("jpackage.app-version"),
                links = listOf("GitHub" to "https://github.com/spicylyrics/mobile"),
                lyricsManagerButton = true,
                pickLyricsFont = { pickLyricsFont(settings, platform) },
            )
        }
        val sections = remember(settings, actions) { settingsPanelSections(settings, actions, DESKTOP_HIDDEN_ROWS) }
        CompositionLocalProvider(LocalPlatformActions provides platform, LocalPopupPalette provides settings.popupTheme.palette) {
            SettingsPanel(sections, settings.settingsTheme, onClose = { state.open = false }, actions = actions)
        }
    }
}
