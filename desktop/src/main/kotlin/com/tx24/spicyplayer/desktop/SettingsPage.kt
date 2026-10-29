package com.tx24.spicyplayer.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tx24.spicyplayer.network.data.LyricsSourceSettings
import com.tx24.spicyplayer.network.data.SourceRanking
import com.tx24.spicyplayer.ui.components.LocalSettingsQuery
import com.tx24.spicyplayer.ui.components.Searchable
import com.tx24.spicyplayer.ui.components.SpicyButton
import com.tx24.spicyplayer.ui.components.ToggleRow
import com.tx24.spicyplayer.ui.components.outlinedCard
import com.tx24.spicyplayer.ui.settings.CommonAppSettings
import com.tx24.spicyplayer.ui.settings.DesktopPlatformActions
import com.tx24.spicyplayer.ui.settings.FileSettingsStore
import com.tx24.spicyplayer.ui.settings.LyricsFont
import com.tx24.spicyplayer.ui.settings.SettingsHost
import com.tx24.spicyplayer.ui.settings.SettingsPage
import com.tx24.spicyplayer.ui.settings.SettingsState
import com.tx24.spicyplayer.ui.theme.SpicyColors
import com.tx24.spicyplayer.ui.theme.SpicySpacing
import com.tx24.spicyplayer.ui.theme.SpicyType
import kotlinx.coroutines.delay
import java.awt.Frame
import java.io.File
import javax.swing.JOptionPane

/** The app's settings, in the user's app data folder; read once, saved as they change. */
val desktopSettings: CommonAppSettings by lazy {
    CommonAppSettings(FileSettingsStore.default(), pipButtonByDefault = false, prereleasesByDefault = true)
}

/**
 * The settings pages' desktop side: the followed player's delay, the source switches, the font
 * picker and the links. The pages only a phone has (This song, translation, the floating window,
 * Advanced) and its device rows are left out, and the Lyrics Manager opens as the window's page.
 */
class DesktopSettingsHost(
    window: Frame?,
    private val session: MediaSession,
    private val delays: SyncDelays,
    private val sources: DesktopSourceSettings?,
    private val openLyricsManager: () -> Unit,
) : SettingsHost() {
    private val platform = DesktopPlatformActions(window)

    /** Bumped by a change made here, so [rememberState] reads it back at once. */
    private val changes = mutableIntStateOf(0)

    override fun setLyricDelay(ms: Int) {
        val app = session.track?.app ?: return
        // The desktop saves how much earlier the lyrics show; the settings show how much later.
        delays.set(app, -ms.toLong())
        session.offsetChanged()
        changes.intValue++
    }

    override fun setSongDelay(ms: Int) = Unit
    override val songDelay: Boolean get() = false

    override fun hasPage(page: SettingsPage): Boolean = when (page) {
        SettingsPage.ThisSong, SettingsPage.Translation, SettingsPage.FloatingLyrics, SettingsPage.Advanced -> false
        SettingsPage.Sources -> sources != null
        else -> true
    }

    override val floatingPanel: Boolean get() = true
    override val phoneDevice: Boolean get() = false
    override val lockScreen: Boolean get() = false
    override val queueAndSource: Boolean get() = false
    override val deviceName: String get() = "computer"
    override val systemName: String get() = "the app"

    override fun openLyricsManagerPage(): Boolean {
        openLyricsManager()
        return true
    }

    /** What the settings show of the player, read again every half second while they're open. */
    @Composable
    fun rememberState(): SettingsState {
        val changed by changes
        val state by produceState(read(), changed) {
            while (true) {
                value = read()
                delay(STATE_POLL_MS)
            }
        }
        return state
    }

    private fun read(): SettingsState {
        val track = session.track
        return SettingsState(
            songTitle = track?.title.orEmpty(),
            lyricDelayMs = (-session.offsetMs).toInt(),
            outputLabel = track?.app ?: "this player",
            sourcesSummary = sources?.let { sources ->
                val origins = sources.origins()
                "${origins.count { sources.isOn(it.id) }} of ${origins.size} on"
            }.orEmpty(),
            lyrics = track?.key,
        )
    }

    @Composable
    override fun rememberLyricsFontPicker(onFailed: (Boolean) -> Unit): () -> Unit {
        val failed by rememberUpdatedState(onFailed)
        val settings = desktopSettings
        return remember {
            {
                platform.pickFile(DesktopFontFile.MIME_TYPES) { path ->
                    val source = path?.let(::File) ?: return@pickFile
                    val imported = DesktopFontFile.import(source)
                    failed(imported == null)
                    if (imported != null) {
                        settings.customFontFile = imported.fileName
                        settings.customFontName = imported.displayName
                        settings.lyricsFont = LyricsFont.Custom
                    }
                }
            }
        }
    }

    /**
     * A switch for each source, in their order. Switching one on that sends the song somewhere new
     * asks first, with the phone's wording, and remembers the answer.
     */
    @Composable
    override fun SourcesPage() {
        val sources = sources ?: return
        // The switches read the store, so a change redraws them through this count.
        val version = remember { mutableIntStateOf(0) }
        val shown = version.intValue
        sources.origins().forEach { origin ->
            ToggleRow(
                label = origin.displayName,
                checked = remember(shown, origin.id) { sources.isOn(origin.id) },
                onCheckedChange = { on ->
                    val disclosure = if (on) sources.disclosureFor(origin.id) else null
                    val agreed = disclosure == null || JOptionPane.showConfirmDialog(
                        null, disclosure.description, "Turn on ${disclosure.name}?", JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE,
                    ) == JOptionPane.OK_OPTION
                    if (agreed) {
                        disclosure?.let(sources::agree)
                        sources.setSourceEnabled(origin.id, on)
                        version.intValue++
                        changes.intValue++
                    }
                },
                searchDescription = "Lyrics source provider",
            )
        }
    }

    /** The name, the version the installer set (none from Gradle), and the links out. */
    @Composable
    override fun AboutCard() {
        Searchable("About", "Version", "GitHub", "Spicy Lyrics Desktop", "Discord", "Feedback", "Bug", "Report") {
            CompositionLocalProvider(LocalSettingsQuery provides "") {
                Column(
                    Modifier.fillMaxWidth().outlinedCard().padding(SpicySpacing.S4),
                    verticalArrangement = Arrangement.spacedBy(SpicySpacing.S3),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Spicy Lyrics Desktop", style = SpicyType.Headline)
                        Text(
                            System.getProperty("jpackage.app-version")?.let { "v$it" } ?: "Development build",
                            style = SpicyType.Footnote.copy(color = SpicyColors.TextSecondary),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
                        SpicyButton("Star on GitHub", { platform.openUrl(PROJECT_URL) }, Modifier.weight(1f), horizontalPadding = 8.dp, external = true)
                        SpicyButton("Discord", { platform.openUrl(DISCORD_URL) }, Modifier.weight(1f), horizontalPadding = 8.dp, brand = DISCORD_BLURPLE, external = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
                        SpicyButton("Report a bug", { platform.openUrl("$PROJECT_URL/issues/new?template=bug_report.yml") }, Modifier.weight(1f), horizontalPadding = 8.dp, brand = BUG_PINK, external = true)
                    }
                }
            }
        }
    }

    private companion object {
        const val STATE_POLL_MS = 500L
        const val PROJECT_URL = "https://github.com/spicylyrics/mobile"
        // The server invite, not a thread link: a thread link only opens for people already in the server.
        const val DISCORD_URL = "https://discord.com/invite/uqgXU5wh8j"
        val DISCORD_BLURPLE = Color(88, 101, 242)
        val BUG_PINK = Color(255, 94, 138)
    }
}
