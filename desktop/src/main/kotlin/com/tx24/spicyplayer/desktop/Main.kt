package com.tx24.spicyplayer.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tx24.spicyplayer.ui.bigscreen.BigScreenActions
import com.tx24.spicyplayer.ui.bigscreen.BigScreenPage
import com.tx24.spicyplayer.ui.bigscreen.BigScreenState
import com.tx24.spicyplayer.ui.bigscreen.LocalLyricsActions
import com.tx24.spicyplayer.ui.bigscreen.LocalTooltipHost
import com.tx24.spicyplayer.ui.bigscreen.ViewMode
import com.tx24.spicyplayer.ui.bigscreen.pageKeyOf
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.spicy.RenderConfig
import com.tx24.spicyplayer.lyrics.spicy.canvas.LocalDisplayRefreshRate
import com.tx24.spicyplayer.lyrics.spicy.canvas.LocalLyricsImageLoader
import com.tx24.spicyplayer.lyrics.spicy.canvas.CjkFonts
import com.tx24.spicyplayer.lyrics.spicy.canvas.LyricsView
import com.tx24.spicyplayer.lyrics.spicy.models.Line
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsCredit
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsFooter
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsProvenance
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsType
import com.tx24.spicyplayer.lyrics.spicy.models.Word
import com.tx24.spicyplayer.lyrics.spicy.models.buildDisplayTimeline
import com.tx24.spicyplayer.lyrics.spicy.romanization.RomanizationMode
import com.tx24.spicyplayer.lyrics.spicy.canvas.kawarp.BackgroundSpeed
import com.tx24.spicyplayer.network.data.spotify.AudioAnalysis
import com.tx24.spicyplayer.network.data.spotify.LocalTrackMetadata
import com.tx24.spicyplayer.network.data.spotify.SharedSpotify
import com.tx24.spicyplayer.platform.CoreLog
import com.tx24.spicyplayer.desktop.update.UpdateBanner
import com.tx24.spicyplayer.desktop.update.UpdateCheck

import com.tx24.spicyplayer.translation.TranslationMode
import com.tx24.spicyplayer.translation.TranslationPresentation
import com.tx24.spicyplayer.translation.TranslationResult
import java.awt.GraphicsEnvironment
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.skia.Image
import kotlinx.coroutines.launch

/** Where Spotify's audio analyses are kept between runs. */
private val cacheDir = File(System.getenv("LOCALAPPDATA") ?: System.getProperty("java.io.tmpdir"), "Spicy Lyrics/cache")

/** What the window shows: nothing playing, a track being looked up, or its lyrics or notice. */
private sealed interface Screen {
    data object Waiting : Screen
    data class Track(val track: com.tx24.spicyplayer.desktop.Track, val lyrics: LyricsState) : Screen
}

/**
 * Usage: `run [--app <id substring>] [--offset <ms>] [--helper <gsmtc.exe on Windows>] [--snap file.png --snap-at S]`, or `run --demo ...` for the
 * renderer on a made-up song (see [runDemo]).
 *
 * Shows the lyrics of Windows' current media session (or the first whose app id contains
 * `--app`), word-synced on the session's position, `--offset` ms earlier.
 */
fun main(args: Array<String>) {
    if ("--demo" in args) return runDemo(args)
    val opts = args.toList().zipWithNext().filter { it.first.startsWith("--") }.toMap()
    val source = mediaSource(opts["--helper"]?.let(::File))
    source.unavailableReason?.let { error(it) }
    CoreLog.sink = { level, tag, message -> if (level >= CoreLog.Level.WARN) System.err.println("[$tag] $message") }

    val delays = SyncDelays(SyncDelays.defaultStore(), opts["--offset"]?.toLongOrNull())
    val session = MediaSession(opts["--app"], delays::offsetFor)
    val lyricsLibrary = DesktopLyricsLibrary(coverOf = { session.cover })
    val lookup = LyricsLookup(local = lyricsLibrary)
    val screen = MutableStateFlow<Screen>(Screen.Waiting)
    val playing = MutableStateFlow(false)
    val cover = MutableStateFlow<File?>(null)
    // The playing song's audio analysis, which the background's speed follows.
    val analysis = MutableStateFlow<AudioAnalysis?>(null)
    SyncDelay.attach(session, delays, analysis) { (screen.value as? Screen.Track)?.lyrics }
    val spotify = SharedSpotify.extras(cacheDir)
    val backgroundImages = DesktopImageCache(File(cacheDir, "backgrounds"))
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    var beats: Job? = null
    val nowPlaying = DesktopNowPlaying(source, session)
    val songLyrics = DesktopLyrics(lookup, GoogleSongTranslator(File(cacheDir, "translations")), desktopSettings, DesktopLyrics.defaultStore(), scope)
        .also { it.followSettings() }
    scope.launch {
        songLyrics.shown.collect { shown -> if (shown != null) screen.value = Screen.Track(shown.track, shown.lyrics) }
    }
    val capabilities = capabilitiesOf(source.capabilities)

    fun lookUp(track: Track) = songLyrics.load(track)

    val updates = UpdateCheck.forThisApp()
    scope.launch { updates.run() }

    Runtime.getRuntime().addShutdownHook(Thread { source.close() })
    scope.launch {
        source.updates().collect { received ->
            val update = received.update as? MediaUpdate.Snapshot ?: return@collect
            val event = session.feed(update.snapshot, received.receivedMono, received.receivedWall)
            playing.value = session.clock.isPlaying
            cover.value = session.cover
            nowPlaying.refresh()
            if (event is SessionEvent.NewTrack) {
                val track = event.track
                lookUp(track)
                analysis.value = null
                beats?.cancel()
                // The background follows the song's beats where the settings ask for it.
                if (desktopSettings.desktopBackground().beatReactive) beats = scope.launch {
                    val local = LocalTrackMetadata(track.title, track.artist, track.album, track.durationMs)
                    val found = spotify.trackIds(local, null).firstOrNull()?.let { spotify.audioAnalysis(it) }
                    if (session.track?.key == track.key) analysis.value = found
                }
            }
        }
        songLyrics.clear()
        screen.value = Screen.Waiting
        nowPlaying.clear()
    }

    val refreshRate = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.displayMode.refreshRate
        .takeIf { it > 0 }?.toFloat() ?: 60f
    application {
        val windowState = rememberWindowState(size = DpSize(1100.dp, 760.dp))
        val settingsWindow = remember { SettingsWindowState() }
        var page by remember { mutableStateOf(BigScreenState(romanized = desktopSettings.romanize)) }
        // The page's romanize button and the Romanize setting are one switch.
        LaunchedEffect(page.romanized) { if (desktopSettings.romanize != page.romanized) desktopSettings.romanize = page.romanized }
        LaunchedEffect(desktopSettings.romanize) { if (page.romanized != desktopSettings.romanize) page = page.copy(romanized = desktopSettings.romanize) }
        LaunchedEffect(page.mode) {
            windowState.placement = if (page.mode == ViewMode.Fullscreen) WindowPlacement.Fullscreen else WindowPlacement.Floating
        }
        val lyricsManager = remember { LyricsManagerWindowState() }
        Window(
            onCloseRequest = { source.close(); exitApplication() },
            onPreviewKeyEvent = { settingsWindow.onKey(it) || lyricsManager.onKey(it) },
            state = windowState,
            title = "Spicy Lyrics",
            icon = remember { BitmapPainter(Image.makeFromEncoded(object {}.javaClass.getResource("/icon.png")!!.readBytes()).toComposeImageBitmap()) },
            onKeyEvent = { event -> MiniLyrics.onKey(event) || SyncDelay.onKey(event) || pageKeyOf(event)?.let { page = page.onKey(it, capabilities); true } ?: false },
        ) {
            CompositionLocalProvider(
                LocalDisplayRefreshRate provides refreshRate,
                LocalLyricsImageLoader provides PlaceholderAvatars,
                LocalTooltipHost provides DesktopTooltips,
                LocalLyricsActions provides songLyrics,
            ) {
                val shown by screen.collectAsState()
                val isPlaying by playing.collectAsState()
                val coverFile by cover.collectAsState()
                val translated by songLyrics.shown.collectAsState()
                val actions = remember { BigScreenActions(onState = { page = it }, onOpenSettings = { settingsWindow.open = true }) }
                // Settings are Compose state: these follow the settings window as it changes them.
                val track = shown as? Screen.Track
                val lyricsState = track?.lyrics ?: LyricsState.Idle
                val display = desktopSettings.desktopLyrics(
                    lyricsState,
                    track?.track?.durationMs ?: 0L,
                    windowState.size.width.value,
                    windowState.size.height.value,
                )
                val backdrop = desktopSettings.desktopBackground()
                val noticeActions = remember {
                    desktopNoticeActions(
                        track = { session.track },
                        onRetry = { session.track?.let { lookUp(it) } },
                        report = { session.track?.let { "Track: ${it.title} by ${it.artist}\nPlayer: ${it.app}" }.orEmpty() },
                    )
                }
                Box(Modifier.fillMaxSize()) {
                    BigScreenPage(
                        state = page,
                        nowPlaying = nowPlaying,
                        capabilities = capabilities,
                        lyrics = lyricsState,
                        actions = actions,
                        noticeActions = noticeActions,
                        background = {
                            val type = desktopSettings.backgroundType
                            DesktopBackgrounds(
                                type = type,
                                blurDp = desktopSettings.backgroundBlurFor(type),
                                animate = backdrop.animate,
                                cover = coverFile,
                                trackKey = track?.track?.key,
                                findArtistHeader = { session.track?.let { artistHeaderUrl(spotify, it) } },
                                images = backgroundImages,
                            ) {
                                DesktopKawarp(
                                    coverFile,
                                    isPlaying,
                                    speed = {
                                        analysis.value?.let { BackgroundSpeed.at(session.clock.positionAt(monoMs()) / 1000f, it) }
                                    }.takeIf { backdrop.beatReactive },
                                    animate = backdrop.animate,
                                    blurPasses = backdrop.blurPasses,
                                )
                            }
                        },
                        lyricsView = { modifier, romanize, activeLineTopPx, renderConfig ->
                            val s = shown as? Screen.Track
                            val lyrics = s?.lyrics as? LyricsState.Ready
                            if (s != null && lyrics != null) Lyrics(
                                lyrics = lyrics,
                                trackKey = s.track.key,
                                currentTimeMs = { session.clock.positionAt(monoMs()) },
                                onSeek = { session.seek(source, it) },
                                isPlaying = isPlaying,
                                modifier = modifier,
                                romanize = romanize,
                                activeLineTopPx = activeLineTopPx,
                                config = renderConfig,
                                translation = translated?.takeIf { it.lyrics === lyrics }?.translation,
                                translationMode = desktopSettings.translationMode,
                                fontSizeScale = display.fontSizeScale,
                                romanizationMode = display.romanizationMode,
                            )
                        },
                        renderConfig = display.config,
                        blurGlass = !desktopSettings.lowPerformance,
                    )
                    UpdateBanner(updates.update, Modifier.align(Alignment.BottomCenter))
                }
            }
            opts["--snap"]?.let { SnapAfter(window, File(it), ((opts["--snap-at"]?.toDouble() ?: 8.0) * 1000).toLong()) }
        }
        SettingsWindow(settingsWindow)
        LyricsManagerWindow(lyricsManager, lyricsLibrary, songLyrics)

        // Ctrl+M: the lyrics in a small window that stays on top.
        val miniShown by screen.collectAsState()
        val miniTranslated by songLyrics.shown.collectAsState()
        val miniPlaying by playing.collectAsState()
        val miniTrack = miniShown as? Screen.Track
        MiniLyricsWindow(nowPlaying, miniTrack?.lyrics ?: LyricsState.Idle) { modifier, fontSizeScale ->
            val ready = miniTrack?.lyrics as? LyricsState.Ready
            if (miniTrack != null && ready != null) {
                val display = desktopSettings.desktopLyrics(ready, miniTrack.track.durationMs, 1f, 1f)
                // A window of its own: it doesn't see the main window's locals.
                CompositionLocalProvider(LocalDisplayRefreshRate provides refreshRate, LocalLyricsImageLoader provides PlaceholderAvatars) {
                Lyrics(
                    lyrics = ready,
                    trackKey = miniTrack.track.key,
                    currentTimeMs = { session.clock.positionAt(monoMs()) },
                    onSeek = {},
                    isPlaying = miniPlaying,
                    modifier = modifier,
                    romanize = display.romanize,
                    activeLineTopPx = null,
                    config = display.config,
                    translation = miniTranslated?.takeIf { it.lyrics === ready }?.translation,
                    translationMode = desktopSettings.translationMode,
                    fontSizeScale = fontSizeScale,
                    romanizationMode = display.romanizationMode,
                )
                }
            }
        }
    }
}

@Composable
private fun Lyrics(
    lyrics: LyricsState.Ready,
    trackKey: String,
    currentTimeMs: () -> Long,
    onSeek: (Long) -> Unit,
    isPlaying: Boolean,
    modifier: Modifier,
    romanize: Boolean,
    activeLineTopPx: Float?,
    config: RenderConfig,
    translation: TranslationResult?,
    translationMode: TranslationMode,
    fontSizeScale: Float,
    romanizationMode: RomanizationMode,
) {
    val original = remember(lyrics.lines) {
        lyrics.lines.map { line ->
            Line(
                words = line.words.map { Word(it.text, it.startMs, it.endMs, isPartOfWord = it.attached, romanizedText = it.romanized) },
                startMs = line.startMs,
                endMs = line.endMs,
                agent = line.agent,
                role = line.role,
                groupId = line.groupId,
                oppositeAligned = line.oppositeAligned,
                lineTimed = line.lineTimed,
                untimed = line.untimed,
            )
        }
    }
    val lines = remember(original, config) {
        buildDisplayTimeline(original, minimalMode = config.isMinimal, holdThroughShortGaps = when (lyrics.lyricsType) {
            LyricsType.Syllable -> config.isMinimal
            LyricsType.Line -> config.isSimple
            else -> false
        }).also(CjkFonts::useFor)
    }
    val presentation = remember(original, lines, translation, translationMode) {
        translation?.let { TranslationPresentation.forTimeline(original, lines, it, translationMode) }
    }
    LyricsView(
        lines = lines,
        documentId = remember(trackKey, lyrics.lines) { "$trackKey:${lyrics.lines.hashCode()}" },
        currentTimeMs = currentTimeMs,
        onSeekWord = onSeek,
        modifier = modifier,
        romanize = romanize,
        romanizationMode = romanizationMode,
        translation = presentation,
        activeLineTopPx = activeLineTopPx,
        isPlaying = isPlaying,
        focusAnchorFraction = 0.5f,
        restTopFraction = 0.25f,
        lyricsType = lyrics.lyricsType,
        config = config,
        fontSizeScale = fontSizeScale,
        footer = remember(lyrics) {
            LyricsFooter(
                songwriters = lyrics.songwriters,
                provenance = LyricsProvenance(lyrics.source ?: lyrics.provider),
                maker = lyrics.maker?.let { LyricsCredit(it.username, it.profileUrl, it.avatarUrl) },
                uploader = lyrics.uploader?.let { LyricsCredit(it.username, it.profileUrl, it.avatarUrl) },
            )
        },
    )
}
