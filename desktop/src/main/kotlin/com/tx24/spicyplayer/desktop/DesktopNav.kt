package com.tx24.spicyplayer.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/** What the main window shows: the lyrics, or one of the pages drawn over them. */
enum class Destination { Lyrics, Settings, LyricsManager, SyncDelay }

/**
 * The page a shortcut opens: Ctrl+, settings, Ctrl+L the Lyrics Manager, Ctrl+D the sync delay
 * (Cmd on a Mac); null for any other key.
 */
fun shortcutDestination(key: Key, command: Boolean): Destination? = if (!command) null else when (key) {
    Key.Comma -> Destination.Settings
    Key.L -> Destination.LyricsManager
    Key.D -> Destination.SyncDelay
    else -> null
}

/** Where the main window is. Pages open over the lyrics, and back (Esc, or a page's close button) returns to them. */
class DesktopNav(start: Destination = Destination.Lyrics) {
    var at by mutableStateOf(start)
        private set

    /** Opens settings over the lyrics instead of as a page, once the window has them. */
    var openSettings: (() -> Unit)? = null

    fun open(destination: Destination) {
        val settings = openSettings
        if (destination == Destination.Settings && settings != null) settings() else at = destination
    }

    fun back() {
        at = Destination.Lyrics
    }

    /** For the window's `onPreviewKeyEvent`, so focused fields don't swallow them: opens a page on its shortcut. */
    fun onShortcut(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val destination = shortcutDestination(event.key, event.isCtrlPressed || event.isMetaPressed) ?: return false
        open(destination)
        return true
    }

    /**
     * For the window's `onKeyEvent`: on a page, Esc goes back and every other key stops here, so
     * the lyrics page's keys don't act behind it. A page that handles Esc itself (settings steps
     * back through its search and sections first) consumes it before it gets here.
     */
    fun onPageKey(event: KeyEvent): Boolean {
        if (at == Destination.Lyrics) return false
        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) back()
        return true
    }
}

/**
 * A page over the lyrics: it fills the window, takes the clicks and focus from the lyrics behind
 * it, and gives the content an opaque floor in case it draws none.
 */
@Composable
fun NavPage(content: @Composable () -> Unit) {
    val focus = remember { FocusRequester() }
    // Before the content's own effects, so a page that focuses a field of its own (settings' search) wins.
    LaunchedEffect(Unit) { focus.requestFocus() }
    Box(
        Modifier.fillMaxSize().background(Color(0xFF121212))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .focusRequester(focus).focusable(),
    ) { content() }
}
