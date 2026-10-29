package com.tx24.spicyplayer.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.tx24.spicyplayer.BuildConfig
import com.tx24.spicyplayer.PictureInPicture
import com.tx24.spicyplayer.analytics.UsageCounter
import com.tx24.spicyplayer.analytics.UsageStats
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.network.data.LyricsSourceSettings
import com.tx24.spicyplayer.playback.ExternalPlaybackViewModel
import com.tx24.spicyplayer.playback.PlayerUiState
import com.tx24.spicyplayer.translation.TranslationLanguages
import com.tx24.spicyplayer.ui.components.LocalSettingsQuery
import com.tx24.spicyplayer.ui.components.Searchable
import com.tx24.spicyplayer.ui.components.SettingRow
import com.tx24.spicyplayer.ui.components.SpicyButton
import com.tx24.spicyplayer.ui.components.SpicySelect
import com.tx24.spicyplayer.ui.components.outlinedCard
import com.tx24.spicyplayer.ui.lyricsmanager.LyricsManagerContent
import com.tx24.spicyplayer.ui.theme.SpicyColors
import com.tx24.spicyplayer.ui.theme.SpicySpacing
import com.tx24.spicyplayer.ui.theme.SpicyType
import com.tx24.spicyplayer.update.UpdateStatus
import com.tx24.spicyplayer.update.UpdateViewModel
import kotlinx.coroutines.launch

/** What the settings show of the player, from the view model's state. */
internal fun settingsState(state: PlayerUiState): SettingsState = SettingsState(
    songTitle = state.title,
    songSummary = songSummary(state),
    lyricDelayMs = state.lyricDelayMs,
    outputLabel = state.outputLabel,
    songDelayMs = state.songDelayMs,
    hasSongKey = state.localLyricsKey != null,
    translationSummary = "${TranslationLanguages.name(state.translationPreferences.targetLanguage)} · " +
        if (state.translationPreferences.automatic) "Automatic" else "On demand",
    sourcesSummary = state.sourceRanking.let { ranking ->
        "${ranking.origins.count { LyricsSourceSettings.isOn(ranking, it.id) }} of ${ranking.origins.size} on"
    },
    queueSize = state.queue.size,
    sourceSummary = if (state.localLyricsKey != null && state.lyrics !is LyricsState.Idle) {
        val chosen = state.sourceOverride?.let { id -> state.sourceDescriptors.firstOrNull { it.id == id }?.displayName ?: id }
        val showing = (state.lyrics as? LyricsState.Ready)?.provider
        when {
            chosen != null -> "$chosen, chosen for this song"
            showing != null -> "Automatic, showing $showing"
            else -> "Automatic"
        }
    } else null,
    lyrics = state.lyrics,
)

/** The settings' platform on the phone: the view model, the usage report, and the pages only the phone has. */
internal class AndroidSettingsHost(
    private val state: PlayerUiState,
    private val viewModel: ExternalPlaybackViewModel,
    private val updater: UpdateViewModel,
    private val settings: AppSettings,
    context: Context,
    /** A wide window: settings float as one panel ([SettingsHost.floatingPanel]). */
    override val floatingPanel: Boolean = false,
) : SettingsHost() {
    override fun setLyricDelay(ms: Int) = viewModel.setLyricDelay(ms)
    override fun setSongDelay(ms: Int) = viewModel.setSongDelay(ms)
    override fun chooseCanvas(onChosen: () -> Unit) = viewModel.chooseCanvas(onChosen)
    override fun countPage(page: SettingsPage) = UsageStats.count(UsageCounter.page(page.name))

    override fun quickScreenOpened(screen: QuickScreen) {
        when (screen) {
            QuickScreen.Queue -> UsageStats.count(UsageCounter.QUEUE)
            QuickScreen.LyricsManager -> {
                UsageStats.count(UsageCounter.LYRICS_MANAGER)
                viewModel.refreshLocalLyrics()
            }
            else -> Unit
        }
    }

    override fun quickSettingsShown() = viewModel.detectLyricsLanguage()

    override val pictureInPicture: Boolean = PictureInPicture.supported(context)
    override val lockScreen: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1
    override val appIconLabel: String get() = settings.appIcon.label

    @Composable
    override fun rememberLyricsFontPicker(onFailed: (Boolean) -> Unit): () -> Unit {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val imported = LyricsFontFile.import(context, uri)
                onFailed(imported == null)
                if (imported != null) {
                    settings.customFontFile = imported.fileName
                    settings.customFontName = imported.displayName
                    settings.lyricsFont = LyricsFont.Custom
                }
            }
        }
        return { picker.launch(FONT_MIME_TYPES) }
    }

    @Composable
    override fun ThisSongPage(onOpenLyricsManager: () -> Unit, onOpenSpotifySearch: () -> Unit) =
        ThisSongContent(state, viewModel, this, onOpenLyricsManager, onOpenSpotifySearch)

    @Composable
    override fun TranslationPage() = RomanizationTranslationContent(state, viewModel, settings)

    @Composable
    override fun SourcesPage() = SourcesContent(state, viewModel)

    @Composable
    override fun AdvancedPage() = AdvancedContent(state, viewModel, updater, settings)

    @Composable
    override fun AboutCard() = AboutCard(state, updater, settings)

    @Composable
    override fun AppIconRow() {
        SettingRow(
            label = "App Icon",
            description = "Choose the icon on your home screen. It changes once you leave the app.",
            icon = Icons.Rounded.Apps,
        ) {
            SpicySelect(
                value = settings.appIcon.name,
                options = AppIcon.entries.map { it.name },
                labels = AppIcon.entries.map { it.label },
                onChange = { name ->
                    settings.appIcon = AppIcon.valueOf(name)
                },
            )
        }
    }

    @Composable
    override fun HapticsSection() = HapticsSection(settings)

    @Composable
    override fun DelayCalibrationRow() = DelayCalibrationRow(state, viewModel)

    @Composable
    override fun LyricsLanguageRow() = LyricsLanguageRow(state, viewModel)

    @Composable
    override fun QueueScreen(onJumped: () -> Unit, modifier: Modifier) = QueueList(state, viewModel, onJumped, modifier)

    @Composable
    override fun SourceScreen(onChosen: () -> Unit, modifier: Modifier) = SourceList(state, viewModel, onChosen, modifier)

    @Composable
    override fun LyricsManagerScreen(uploading: Boolean, onUploadingChange: (Boolean) -> Unit, onDismiss: () -> Unit) =
        LyricsManagerContent(state, viewModel, settings, uploading, onUploadingChange, onDismiss)
}

/** The phone's settings platform for [state], made again as it changes. */
@Composable
internal fun rememberAndroidSettingsHost(
    state: PlayerUiState,
    viewModel: ExternalPlaybackViewModel,
    updater: UpdateViewModel,
    settings: AppSettings,
    floatingPanel: Boolean = false,
): AndroidSettingsHost {
    val context = LocalContext.current
    return remember(state, viewModel, updater, settings, context, floatingPanel) {
        AndroidSettingsHost(state, viewModel, updater, settings, context, floatingPanel)
    }
}

/** Font files come labelled all sorts of ways, so the picker offers these (and checks the file). */
private val FONT_MIME_TYPES = arrayOf(
    "font/*", "application/font-sfnt", "application/x-font-ttf", "application/x-font-otf",
    "application/vnd.ms-opentype", "application/octet-stream",
)

/**
 * The footer card, kept short: the name, version, channel and update status with a manual check,
 * then the links out two by two. The status checks quietly on its own and claims nothing until it knows.
 */
@Composable
internal fun AboutCard(state: PlayerUiState, updater: UpdateViewModel, settings: AppSettings) {
    val context = LocalContext.current
    val update by updater.state.collectAsState()
    LaunchedEffect(Unit) { updater.checkQuietly(settings.includePrereleases) }
    // Found or hidden as one card; inside it, everything shows.
    Searchable("About", "Version", "GitHub", "Star", "Spicy Lyrics Mobile", "Spicy Player", "Updates", "Check for updates", "Feedback", "Bug", "Report", "Feature", "Suggest", "Discord") {
        CompositionLocalProvider(LocalSettingsQuery provides "") {
            Column(
                Modifier.fillMaxWidth().outlinedCard().padding(SpicySpacing.S4),
                verticalArrangement = Arrangement.spacedBy(SpicySpacing.S3),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Spicy Lyrics Mobile", style = SpicyType.Headline)
                        Text(versionLine(update.status, updater.enabled), style = SpicyType.Footnote.copy(color = SpicyColors.TextSecondary))
                    }
                    if (updater.enabled) UpdateButton(update.status, onUpdate = updater::show) { updater.check(settings.includePrereleases) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
                    FooterLink("Star on GitHub", Modifier.weight(1f)) { openUrl(context, PROJECT_URL) }
                    FooterLink("Discord", Modifier.weight(1f), DISCORD_BLURPLE) { openUrl(context, DISCORD_URL) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
                    // Fills in the form's version and debug info; the reporter sees both before sending.
                    FooterLink("Report a bug", Modifier.weight(1f), BUG_PINK) {
                        val debug = Uri.encode(debugReport(context, state).take(MAX_PREFILLED_DEBUG))
                        openUrl(context, "$PROJECT_URL/issues/new?template=bug_report.yml&version=${Uri.encode(BuildConfig.VERSION_NAME)}&debug=$debug")
                    }
                    FooterLink("Suggest a feature", Modifier.weight(1f)) { openUrl(context, "$PROJECT_URL/issues/new?template=feature_request.yml") }
                }
            }
        }
    }
}

@Composable
private fun FooterLink(label: String, modifier: Modifier, brand: Color? = null, onClick: () -> Unit) {
    SpicyButton(label, onClick, modifier, horizontalPadding = 8.dp, brand = brand, external = true)
}

/** "v0.6.0 · pre-release · Latest": the status only once a check has said something. */
@Composable
private fun versionLine(status: UpdateStatus, enabled: Boolean): AnnotatedString = buildAnnotatedString {
    append("v${BuildConfig.VERSION_NAME} · ${buildChannel()}")
    if (!enabled) return@buildAnnotatedString
    val (text, color) = when (status) {
        UpdateStatus.UpToDate -> "Latest" to SpicyColors.StatusSuccess
        is UpdateStatus.Available -> "Update available" to SpicyColors.StatusWarning
        is UpdateStatus.Failed -> "Couldn't check" to SpicyColors.TextTertiary
        else -> return@buildAnnotatedString
    }
    append(" · ")
    withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) { append(text) }
}

/** Checks on demand; once an update is found, opens it instead. */
@Composable
private fun UpdateButton(status: UpdateStatus, onUpdate: () -> Unit, onCheck: () -> Unit) {
    when (status) {
        is UpdateStatus.Available -> SpicyButton("Update", onUpdate, brand = SpicyColors.StatusWarning)
        UpdateStatus.Checking -> SpicyButton("Checking…", {}, enabled = false)
        is UpdateStatus.Downloading -> SpicyButton("${(status.progress * 100).toInt()}%", {}, enabled = false)
        is UpdateStatus.Installing -> SpicyButton("Installing", {}, enabled = false)
        UpdateStatus.Idle, UpdateStatus.UpToDate, is UpdateStatus.Failed -> SpicyButton("Check for updates", onCheck)
    }
}

/**
 * Which channel this build is from: every 0.x release, and any version with a suffix ("1.0.0-beta"),
 * is published as a pre-release, the same rule the update check uses to offer pre-releases.
 */
private fun buildChannel(): String = when {
    BuildConfig.DEBUG -> "debug"
    BuildConfig.VERSION_NAME.startsWith("0.") || '-' in BuildConfig.VERSION_NAME -> "pre-release"
    else -> "stable"
}

private val DISCORD_BLURPLE = Color(88, 101, 242)
private val BUG_PINK = Color(255, 94, 138)

private const val PROJECT_URL = "https://github.com/spicylyrics/mobile"
// Keeps the prefilled form link well under the length GitHub accepts.
private const val MAX_PREFILLED_DEBUG = 3000

// The server invite, not the thread link: a thread link only opens for people already in the server.
internal const val DISCORD_URL = "https://discord.com/invite/uqgXU5wh8j"

internal fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
