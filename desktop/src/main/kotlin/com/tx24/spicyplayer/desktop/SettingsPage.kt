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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.tx24.spicyplayer.ui.lyricsmanager.AddResult
import com.tx24.spicyplayer.ui.lyricsmanager.LibraryEntry
import com.tx24.spicyplayer.ui.lyricsmanager.LyricsManagerActions
import com.tx24.spicyplayer.ui.lyricsmanager.LyricsManagerContent
import com.tx24.spicyplayer.ui.lyricsmanager.LyricsManagerState
import com.tx24.spicyplayer.ui.lyricsmanager.acceptsFile
import com.tx24.spicyplayer.ui.lyricsmanager.sanitizeFilename
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image
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
 * Advanced) and its device rows are left out. The Lyrics Manager keeps its songs in [library];
 * [reloadLyrics] looks the playing song's lyrics up again once they change, and [say] shows a notice.
 */
class DesktopSettingsHost(
    window: Frame?,
    private val session: MediaSession,
    private val delays: SyncDelays,
    private val sources: DesktopSourceSettings?,
    private val library: DesktopLyricsLibrary,
    private val reloadLyrics: () -> Unit = {},
    private val say: (String) -> Unit = {},
) : SettingsHost() {
    private val platform = DesktopPlatformActions(window)
    private val files = DesktopLyricsFiles(platform, window)

    /**
     * The density settings are drawn at: the window's own, times the Interface Scale setting,
     * without the lyrics screen's fit to a small window, so the panel keeps its size in any
     * window. Null until the window sets it.
     */
    var density by mutableStateOf<Density?>(null)

    /** Bumped whenever [library] changes, which reads its list again. */
    private var libraryVersion by mutableIntStateOf(0)

    /** What the settings show of the player; [rememberState] keeps it current while they're open. */
    private val state = mutableStateOf(SettingsState())

    override fun setLyricDelay(ms: Int) {
        val app = session.track?.app ?: return
        // The desktop saves how much earlier the lyrics show; the settings show how much later.
        delays.set(app, -ms.toLong())
        session.offsetChanged()
        state.value = read()
    }

    override fun setSongDelay(ms: Int) = Unit
    override val songDelay: Boolean get() = false

    override fun hasPage(page: SettingsPage): Boolean = when (page) {
        SettingsPage.ThisSong, SettingsPage.Translation, SettingsPage.FloatingLyrics, SettingsPage.Advanced -> false
        SettingsPage.Sources -> sources != null
        else -> true
    }

    override val floatingPanel: Boolean get() = true
    override val appName: String get() = "Spicy Lyrics Desktop"
    override val phoneDevice: Boolean get() = false
    override val lockScreen: Boolean get() = false
    override val queueAndSource: Boolean get() = false
    override val deviceName: String get() = "computer"
    override val aboutSearchTerms: List<String> get() = ABOUT_TERMS
    override val systemName: String get() = "the app"
    override val upright: String get() = "In a tall window"
    override val sideways: String get() = "In a wide window"
    override val lastTouch: String get() = "the mouse or keys were last used"
    override val aTouch: String get() = "Moving the mouse or pressing a key"
    override val untouched: String get() = "alone"

    /** What the settings show of the player, read again every half second while they're open. */
    @Composable
    fun rememberState(): SettingsState {
        LaunchedEffect(Unit) {
            while (true) {
                state.value = read()
                delay(STATE_POLL_MS)
            }
        }
        return state.value
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
                        state.value = read()
                    }
                },
                searchDescription = "Lyrics source provider",
            )
        }
    }

    /**
     * The Lyrics Manager over [library]: its list is read again whenever it opens and after every
     * change, and uploads are for the song the player is on.
     */
    @Composable
    override fun LyricsManagerScreen(uploading: Boolean, onUploadingChange: (Boolean) -> Unit, onDismiss: () -> Unit) {
        val scope = rememberCoroutineScope()
        val entries by produceState(emptyList<LibraryEntry>(), libraryVersion) {
            value = runCatching { library.entries() }.getOrDefault(emptyList())
        }
        // Read for its title, so a new song redraws the screen.
        val title = state.value.songTitle
        val track = session.track
        val currentId = track?.let { library.idOf(it.asLibraryTrack()) }
        val actions = remember(scope) {
            object : LyricsManagerActions {
                override suspend fun cover(id: String): ImageBitmap? =
                    library.cover(id)?.let { runCatching { Image.makeFromEncoded(it).toComposeImageBitmap() }.getOrNull() }

                override fun export(entry: LibraryEntry) {
                    scope.launch {
                        val ttml = library.raw(entry.id) ?: return@launch say("Could not retrieve TTML for this track.")
                        files.save("${sanitizeFilename(entry.title)}.ttml", ttml) { written ->
                            if (written == false) say("Could not retrieve TTML for this track.")
                        }
                    }
                }

                override fun remove(id: String) {
                    scope.launch {
                        library.remove(id)
                        libraryVersion++
                        say("Removed from Local DB.")
                        if (id == session.track?.let { library.idOf(it.asLibraryTrack()) }) reloadLyrics()
                    }
                }

                override fun resetOnce() {
                    library.clearOnce()
                    reloadLyrics()
                }

                override fun pickUpload(save: Boolean, onPicked: (apply: suspend () -> Boolean) -> Unit) {
                    files.open { name, text -> onPicked { upload(name, text, save) } }
                }
            }
        }
        LyricsManagerContent(
            LyricsManagerState(entries, currentId, title.ifEmpty { track?.title.orEmpty() }),
            actions,
            desktopSettings,
            uploading,
            onUploadingChange,
            onDismiss,
        )
    }

    /** Applies [text], the file [name], to the playing song: true once it's shown. */
    private suspend fun upload(name: String, text: String, save: Boolean): Boolean {
        val track = session.track ?: return false.also { say("Nothing is playing.") }
        if (!acceptsFile(name)) return false.also { say("Only .ttml and .lrc files are supported.") }
        return when (val result = library.add(track.asLibraryTrack(), text.removePrefix("\uFEFF"), name, save)) {
            is AddResult.Refused -> false.also { say(result.message) }
            is AddResult.Added -> {
                libraryVersion++
                say(if (result.saved) "Saved to Local DB." else "Applied for this song.")
                reloadLyrics()
                true
            }
        }
    }

    /** The name, the version the installer set (none from Gradle), and the links out. */
    @Composable
    override fun AboutCard() {
        Searchable(*ABOUT_TERMS.toTypedArray()) {
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
        val ABOUT_TERMS = listOf("About", "Version", "GitHub", "Star", "Spicy Lyrics Desktop", "Discord", "Feedback", "Bug", "Report")
        const val PROJECT_URL = "https://github.com/spicylyrics/mobile"
        // The server invite, not a thread link: a thread link only opens for people already in the server.
        const val DISCORD_URL = "https://discord.com/invite/uqgXU5wh8j"
        val DISCORD_BLURPLE = Color(88, 101, 242)
        val BUG_PINK = Color(255, 94, 138)
    }
}
