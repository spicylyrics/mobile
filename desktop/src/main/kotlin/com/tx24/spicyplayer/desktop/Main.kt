package com.tx24.spicyplayer.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tx24.spicyplayer.desktop.page.LyricsPage
import com.tx24.spicyplayer.desktop.page.PageActions
import com.tx24.spicyplayer.desktop.page.PageContent
import com.tx24.spicyplayer.desktop.page.PageState
import com.tx24.spicyplayer.desktop.page.ViewMode
import com.tx24.spicyplayer.desktop.page.onKey
import com.tx24.spicyplayer.desktop.page.pageKeyOf
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
import com.tx24.spicyplayer.lyrics.spicy.canvas.kawarp.BackgroundSpeed
import com.tx24.spicyplayer.network.data.spotify.AudioAnalysis
import com.tx24.spicyplayer.network.data.spotify.LocalTrackMetadata
import com.tx24.spicyplayer.network.data.spotify.SharedSpotify
import com.tx24.spicyplayer.platform.CoreLog
import java.awt.GraphicsEnvironment
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.skia.Image
import kotlinx.coroutines.launch

/** The background speeds up and slows down with the song's tempo, loudness and beats. */
private const val BEAT_REACTIVE_BACKGROUND = true

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

    val session = MediaSession(opts["--app"], opts["--offset"]?.toLongOrNull() ?: 0L)
    val lookup = LyricsLookup()
    val screen = MutableStateFlow<Screen>(Screen.Waiting)
    val playing = MutableStateFlow(false)
    val cover = MutableStateFlow<File?>(null)
    // The playing song's audio analysis, which the background's speed follows.
    val analysis = MutableStateFlow<AudioAnalysis?>(null)
    val spotify = SharedSpotify.extras(cacheDir)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    var fetch: Job? = null
    var beats: Job? = null

    fun lookUp(track: Track) {
        screen.value = Screen.Track(track, LyricsState.Loading)
        fetch?.cancel()
        fetch = scope.launch {
            val found = lookup.find(track).lyrics
            if (session.track?.key == track.key) screen.value = Screen.Track(track, found)
        }
    }

    Runtime.getRuntime().addShutdownHook(Thread { source.close() })
    scope.launch {
        source.updates().collect { received ->
            val update = received.update as? MediaUpdate.Snapshot ?: return@collect
            val event = session.feed(update.snapshot, received.receivedMono, received.receivedWall)
            playing.value = session.clock.isPlaying
            cover.value = session.cover
            if (event is SessionEvent.NewTrack) {
                val track = event.track
                lookUp(track)
                analysis.value = null
                beats?.cancel()
                if (BEAT_REACTIVE_BACKGROUND) beats = scope.launch {
                    val local = LocalTrackMetadata(track.title, track.artist, track.album, track.durationMs)
                    val found = spotify.trackIds(local, null).firstOrNull()?.let { spotify.audioAnalysis(it) }
                    if (session.track?.key == track.key) analysis.value = found
                }
            }
        }
        screen.value = Screen.Waiting
    }

    val refreshRate = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.displayMode.refreshRate
        .takeIf { it > 0 }?.toFloat() ?: 60f
    application {
        val windowState = rememberWindowState(size = DpSize(1100.dp, 760.dp))
        var page by remember { mutableStateOf(PageState()) }
        LaunchedEffect(page.mode) {
            windowState.placement = if (page.mode == ViewMode.Fullscreen) WindowPlacement.Fullscreen else WindowPlacement.Floating
        }
        Window(
            onCloseRequest = { source.close(); exitApplication() },
            state = windowState,
            title = "Spicy Lyrics",
            icon = remember { BitmapPainter(Image.makeFromEncoded(object {}.javaClass.getResource("/icon.png")!!.readBytes()).toComposeImageBitmap()) },
            onKeyEvent = { event -> pageKeyOf(event)?.let { page = page.onKey(it); true } ?: false },
        ) {
            CompositionLocalProvider(LocalDisplayRefreshRate provides refreshRate, LocalLyricsImageLoader provides PlaceholderAvatars) {
                val shown by screen.collectAsState()
                val isPlaying by playing.collectAsState()
                val coverFile by cover.collectAsState()
                val actions = remember {
                    PageActions(
                        onState = { page = it },
                        onPlayPause = {
                            session.track?.app?.let { app -> source.control(app, if (session.clock.isPlaying) Transport.PAUSE else Transport.PLAY) }
                        },
                        onSeek = { session.seek(source, it) },
                        onRetry = { session.track?.let { lookUp(it) } },
                        // TODO: open the settings panel once it lands.
                        onOpenSettings = {},
                    )
                }
                LyricsPage(
                    state = page,
                    content = when (val s = shown) {
                        Screen.Waiting -> PageContent.Waiting
                        is Screen.Track -> PageContent.Playing(s.track, s.lyrics)
                    },
                    cover = coverFile,
                    isPlaying = isPlaying,
                    positionMs = { session.clock.positionAt(monoMs()) },
                    capabilities = source.capabilities,
                    actions = actions,
                    background = {
                        DesktopKawarp(coverFile, isPlaying, speed = {
                            analysis.value?.let { BackgroundSpeed.at(session.clock.positionAt(monoMs()) / 1000f, it) }
                        })
                    },
                    lyricsView = { modifier, romanize, activeLineTopPx ->
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
                        )
                    },
                )
            }
            opts["--snap"]?.let { SnapAfter(window, File(it), ((opts["--snap-at"]?.toDouble() ?: 8.0) * 1000).toLong()) }
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
) {
    val config = RenderConfig.FULL
    val lines = remember(lyrics.lines) {
        val original = lyrics.lines.map { line ->
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
        buildDisplayTimeline(original, minimalMode = config.isMinimal, holdThroughShortGaps = when (lyrics.lyricsType) {
            LyricsType.Syllable -> config.isMinimal
            LyricsType.Line -> config.isSimple
            else -> false
        }).also(CjkFonts::useFor)
    }
    LyricsView(
        lines = lines,
        documentId = remember(trackKey, lyrics.lines) { "$trackKey:${lyrics.lines.hashCode()}" },
        currentTimeMs = currentTimeMs,
        onSeekWord = onSeek,
        modifier = modifier,
        romanize = romanize,
        activeLineTopPx = activeLineTopPx,
        isPlaying = isPlaying,
        focusAnchorFraction = 0.5f,
        restTopFraction = 0.25f,
        lyricsType = lyrics.lyricsType,
        config = config,
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
