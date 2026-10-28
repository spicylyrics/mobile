package com.tx24.spicyplayer.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.tx24.spicyplayer.ui.bigscreen.PlayerCapabilities
import com.tx24.spicyplayer.ui.components.SpicyGlyphs
import com.tx24.spicyplayer.ui.nowplaying.NoticeActions
import com.tx24.spicyplayer.ui.nowplaying.NoticeIcons
import com.tx24.spicyplayer.ui.nowplaying.NowPlaying
import com.tx24.spicyplayer.ui.nowplaying.NowPlayingState
import com.tx24.spicyplayer.ui.nowplaying.NowPlayingTrack
import com.tx24.spicyplayer.ui.nowplaying.RepeatState
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI
import java.net.URLEncoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.skia.Image

/**
 * The desktop's media session as the shared screens' [NowPlaying]: [refresh] after each snapshot
 * [session] takes, and the transport goes to [source] for the commands it carries out.
 */
class DesktopNowPlaying(private val source: MediaSource, private val session: MediaSession) : NowPlaying {
    private val current = MutableStateFlow(NowPlayingState(NowPlayingTrack("", "")))
    override val state: StateFlow<NowPlayingState> = current

    private var coverKey: Pair<String, Long>? = null
    private var coverImage: ImageBitmap? = null

    /** Publishes what [session] now knows; the cover is decoded only when its file changes. */
    fun refresh() {
        val track = session.track ?: return
        current.value = NowPlayingState(
            track = NowPlayingTrack(track.title, track.artist, track.album, track.durationMs),
            player = track.app,
            artwork = cover(session.cover),
            isPlaying = session.clock.isPlaying,
            canSeek = Transport.SEEK in source.capabilities,
            shuffle = session.shuffle,
            repeat = repeatStateOf(session.repeat),
            repeatOneSupported = session.repeatOneSupported,
        )
    }

    /** Back to nothing playing, once the source has stopped reporting. */
    fun clear() {
        current.value = NowPlayingState(NowPlayingTrack("", ""))
    }

    private fun cover(file: File?): ImageBitmap? {
        if (file == null) return null
        val key = file.path to file.lastModified()
        if (key != coverKey) {
            coverKey = key
            coverImage = runCatching { Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap() }.getOrNull() ?: coverImage
        }
        return coverImage
    }

    override fun positionMs(): Long = session.clock.positionAt(monoMs())
    override fun playPause() = control(if (session.clock.isPlaying) Transport.PAUSE else Transport.PLAY)
    override fun skipNext() = control(Transport.NEXT)
    override fun skipPrevious() = control(Transport.PREVIOUS)
    override fun seekTo(positionMs: Long) = session.seek(source, positionMs)
    override fun sendCustomAction(action: String) = Unit
    override fun setShuffle(on: Boolean) = session.setShuffle(source, on)
    override fun setRepeat(mode: RepeatState) = session.setRepeat(source, repeatModeOf(mode))

    private fun control(action: Transport) {
        val app = session.track?.app ?: return
        if (action in source.capabilities) source.control(app, action)
    }
}

/**
 * What the page can offer with a source that takes [transport], and sets shuffle and repeat where it
 * [canShuffle] and [canRepeat]; a desktop window can always go fullscreen.
 */
fun capabilitiesOf(transport: Set<Transport>, canShuffle: Boolean = false, canRepeat: Boolean = false): PlayerCapabilities = PlayerCapabilities(
    playPause = Transport.PLAY in transport && Transport.PAUSE in transport,
    skipNext = Transport.NEXT in transport,
    skipPrevious = Transport.PREVIOUS in transport,
    seek = Transport.SEEK in transport,
    fullscreen = true,
    shuffle = canShuffle,
    repeat = canRepeat,
)

/** The player's repeat mode as the shared screens name it. */
fun repeatStateOf(mode: RepeatMode?): RepeatState? = when (mode) {
    null -> null
    RepeatMode.Off -> RepeatState.Off
    RepeatMode.All -> RepeatState.All
    RepeatMode.One -> RepeatState.One
}

fun repeatModeOf(state: RepeatState): RepeatMode = when (state) {
    RepeatState.Off -> RepeatMode.Off
    RepeatState.All -> RepeatMode.All
    RepeatState.One -> RepeatMode.One
}

private const val DISCORD_URL = "https://discord.com/invite/uqgXU5wh8j"

/**
 * The notice's buttons on the desktop: the system clipboard, the browser for help and for
 * searching Spotify, and the app's own line glyphs.
 */
fun desktopNoticeActions(track: () -> Track?, onRetry: () -> Unit, report: () -> String) = NoticeActions(
    onRetry = onRetry,
    report = report,
    onSearchSpotify = {
        // Finds the track's link to paste into the More menu's Spotify match.
        val query = track()?.let { "${it.title} ${it.artist}" }.orEmpty()
        browse("https://open.spotify.com/search/" + URLEncoder.encode(query, Charsets.UTF_8).replace("+", "%20"))
    },
    copyText = { text -> runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }.isSuccess },
    onOpenHelp = { browse(DISCORD_URL) },
    icons = NoticeIcons(
        retry = SpicyGlyphs.Resync,
        copy = SpicyGlyphs.LyricsManager,
        copied = SpicyGlyphs.LyricsManager,
        copyFailed = SpicyGlyphs.LyricsManager,
        help = SpicyGlyphs.Queue,
        search = SpicyGlyphs.Source,
    ),
)

private fun browse(url: String) {
    runCatching { if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(url)) }
}
