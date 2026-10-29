package com.tx24.spicyplayer.ui.settings

import com.tx24.spicyplayer.ui.components.SpicyGlyphs
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tx24.spicyplayer.ui.components.LocalUiAnimations
import com.tx24.spicyplayer.ui.components.SettingRow
import com.tx24.spicyplayer.ui.components.SettingsSkeleton
import com.tx24.spicyplayer.ui.components.SpicyModal
import com.tx24.spicyplayer.ui.components.outlinedCard
import com.tx24.spicyplayer.ui.components.screenSlide
import com.tx24.spicyplayer.ui.theme.SpicySpacing
import dev.chrisbanes.haze.HazeState

/**
 * The quick settings pop-up, from its floating button: one card leading to the player's queue
 * (when it shares one), the song's lyrics source and the Lyrics Manager, then the language and delays, tuned while the lyrics keep playing behind it rather
 * than from inside Settings. The queue and the Lyrics Manager open as screens of this pop-up,
 * sliding in over it, with a way back.
 */
@Composable
fun QuickSettingsModal(
    visible: Boolean,
    state: SettingsState,
    host: SettingsHost,
    backdrop: HazeState?,
    /** The screen it opens on: the queue's and the source's own floating buttons open theirs. */
    startOn: QuickScreen = QuickScreen.Main,
    onDismiss: () -> Unit,
) {
    var screen by remember { mutableStateOf(QuickScreen.Main) }
    var uploading by remember { mutableStateOf(false) }
    LaunchedEffect(visible, state.lyrics) { if (visible) host.quickSettingsShown() }
    // Opens on its first screen each time.
    LaunchedEffect(visible) {
        if (visible) {
            screen = startOn
            if (startOn == QuickScreen.Queue) host.quickScreenOpened(QuickScreen.Queue)
            uploading = false
        }
    }
    fun open(target: QuickScreen) {
        if (target == QuickScreen.Queue || target == QuickScreen.LyricsManager) host.quickScreenOpened(target)
        screen = target
    }
    SpicyModal(
        visible = visible,
        onDismissRequest = onDismiss,
        backdrop = backdrop,
        title = when (screen) {
            QuickScreen.Main -> "Quick settings"
            QuickScreen.Queue -> "Queue"
            QuickScreen.LyricsManager -> if (uploading) "Upload TTML" else "Local Lyrics DB"
            QuickScreen.Source -> "Lyrics source"
        },
        skeleton = { SettingsSkeleton(rows = 4) },
        // The header's back arrow (and Back) step out of a screen before Back closes the pop-up.
        onBack = when {
            screen == QuickScreen.Main -> null
            uploading -> ({ uploading = false })
            else -> ({ screen = QuickScreen.Main })
        },
    ) {
        val animate = LocalUiAnimations.current
        AnimatedContent(
            targetState = screen,
            transitionSpec = { screenSlide(forward = targetState != QuickScreen.Main, animate = animate) },
            label = "quickSettingsScreen",
        ) { shown ->
            Column(verticalArrangement = Arrangement.spacedBy(SpicySpacing.S3)) {
                when (shown) {
                    QuickScreen.Main -> MainScreen(state, host, ::open)
                    // Its own height: the list scrolls inside the pop-up's body.
                    QuickScreen.Queue -> host.QueueScreen(onJumped = onDismiss, modifier = Modifier.height(QUEUE_HEIGHT))
                    QuickScreen.LyricsManager -> host.LyricsManagerScreen(uploading, { uploading = it }, onDismiss)
                    QuickScreen.Source -> host.SourceScreen(onChosen = { screen = QuickScreen.Main }, modifier = Modifier.height(QUEUE_HEIGHT))
                }
            }
        }
    }
}

@Composable
private fun MainScreen(state: SettingsState, host: SettingsHost, open: (QuickScreen) -> Unit) {
    // The screens this opens share one compact card, so the settings tuned right here keep the room.
    val rows = buildList<@Composable () -> Unit> {
        if (state.queueSize > 0) add {
            GroupRow(SpicyGlyphs.Queue, "Queue", "${state.queueSize} ${if (state.queueSize == 1) "song" else "songs"}") {
                open(QuickScreen.Queue)
            }
        }
        state.sourceSummary?.let { summary ->
            add { GroupRow(SpicyGlyphs.Source, "Lyrics source", summary) { open(QuickScreen.Source) } }
        }
        if (host.lyricsManager) add {
            GroupRow(SpicyGlyphs.LyricsManager, "Lyrics Manager", "Save or upload lyrics, link a folder") { open(QuickScreen.LyricsManager) }
        }
    }
    if (rows.isNotEmpty()) Column(Modifier.fillMaxWidth().outlinedCard()) {
        rows.forEachIndexed { i, row ->
            if (i > 0) GroupDivider()
            row()
        }
    }
    host.LyricsLanguageRow()
    if (host.songDelay) SongDelayRow(state, host)
    SettingRow(
        label = "Output delay",
        description = "For every song on ${state.outputLabel}. Move it right if the lyrics run ahead of the song.",
        icon = Icons.Rounded.Timer,
        stacked = true,
    ) {
        DelayControl(state.lyricDelayMs, host::setLyricDelay)
    }
}

/** The queue's height in the pop-up: room for about eight songs. */
private val QUEUE_HEIGHT = 440.dp
