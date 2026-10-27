package com.tx24.spicyplayer.desktop

import androidx.compose.runtime.Composable
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
import com.tx24.spicyplayer.ui.settings.CommonAppSettings
import com.tx24.spicyplayer.ui.settings.DesktopPlatformActions
import com.tx24.spicyplayer.ui.settings.FileSettingsStore
import com.tx24.spicyplayer.ui.settings.panel.PanelActions
import com.tx24.spicyplayer.ui.settings.panel.SettingsPanel
import com.tx24.spicyplayer.ui.settings.panel.settingsPanelSections

/** The app's settings, in the user's app data folder; read once, saved as they change. */
val desktopSettings: CommonAppSettings by lazy {
    CommonAppSettings(FileSettingsStore.default(), pipButtonByDefault = false, prereleasesByDefault = false)
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
        val actions = remember {
            val platform = DesktopPlatformActions(window)
            PanelActions(
                openUrl = { platform.openUrl(it) },
                // Set by the installer's launcher; a run from Gradle has none.
                version = System.getProperty("jpackage.app-version"),
                links = listOf("GitHub" to "https://github.com/spicylyrics/mobile"),
            )
        }
        val sections = remember(settings, actions) { settingsPanelSections(settings, actions) }
        SettingsPanel(sections, settings.settingsTheme, onClose = { state.open = false }, actions = actions)
    }
}
