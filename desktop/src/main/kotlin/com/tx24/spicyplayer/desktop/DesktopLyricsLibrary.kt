package com.tx24.spicyplayer.desktop

import com.tx24.spicyplayer.lyrics.LocalLyricsStore
import com.tx24.spicyplayer.lyrics.LrcConverter
import com.tx24.spicyplayer.lyrics.RemoteLyricsAdapter
import com.tx24.spicyplayer.network.data.LyricsCapability
import com.tx24.spicyplayer.network.data.LyricsSourceDescriptor
import com.tx24.spicyplayer.network.data.ProviderAttempt
import com.tx24.spicyplayer.network.data.ProviderAttemptOutcome
import com.tx24.spicyplayer.network.data.RemoteLyricsPayload
import com.tx24.spicyplayer.network.data.RemoteLyricsResolution
import com.tx24.spicyplayer.network.data.RemoteLyricsSelection
import com.tx24.spicyplayer.network.data.measuredQuality
import com.tx24.spicyplayer.ui.lyricsmanager.AddResult
import com.tx24.spicyplayer.ui.lyricsmanager.LibraryEntry
import com.tx24.spicyplayer.ui.lyricsmanager.LibraryTrack
import com.tx24.spicyplayer.ui.lyricsmanager.LocalLyricsLibrary
import com.tx24.spicyplayer.ui.lyricsmanager.LyricsFiles
import com.tx24.spicyplayer.ui.settings.FileSettingsStore
import com.tx24.spicyplayer.ui.settings.PlatformActions
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Saved lyrics, shown as from here. */
val LOCAL_SOURCE = LyricsSourceDescriptor("local", "Local Lyrics DB", 0, setOf(LyricsCapability.WORD_SYNC))

/** Lyrics uploaded just once. */
val UPLOADED_SOURCE = LyricsSourceDescriptor("uploaded", "Uploaded TTML", 0, setOf(LyricsCapability.WORD_SYNC))

/**
 * The Lyrics Manager's songs on this computer: [LocalLyricsStore] files in [dir] (the app's data
 * folder by default), keyed on the player's title and artist, each with the cover [coverOf] gave
 * when it was saved. An upload applied just once lives in memory until it's reset or replaced.
 */
class DesktopLyricsLibrary(
    dir: File = defaultDir(),
    private val coverOf: () -> File? = { null },
) : LocalLyricsLibrary {
    private val store = LocalLyricsStore(dir)

    /** The song id and TTML of an upload applied just once. */
    @Volatile private var once: Pair<String, String>? = null

    override suspend fun entries(): List<LibraryEntry> = withContext(Dispatchers.IO) {
        store.entries().map { LibraryEntry(it.key, it.title, it.artist, it.album, it.savedAt) }
    }

    override suspend fun raw(id: String): String? = withContext(Dispatchers.IO) { store.raw(id) }

    override suspend fun cover(id: String): ByteArray? = withContext(Dispatchers.IO) {
        store.coverFile(id).takeIf(File::isFile)?.let { runCatching { it.readBytes() }.getOrNull() }
    }

    override fun idOf(track: LibraryTrack): String = LocalLyricsStore.keyOf(track.title, track.artist)

    override suspend fun add(track: LibraryTrack, text: String, fileName: String?, save: Boolean): AddResult {
        val ttml = when (val converted = toTtml(text, fileName)) {
            is Converted.Ttml -> converted.ttml
            is Converted.Refused -> return AddResult.Refused(converted.message)
        }
        if (!usable(ttml, track.durationMs)) return AddResult.Refused("Failed to parse TTML.")
        if (save) {
            withContext(Dispatchers.IO) {
                val entry = store.put(track.title, track.artist, track.title, track.artist, track.album, ttml)
                coverOf()?.takeIf(File::isFile)?.let { runCatching { it.copyTo(store.coverFile(entry.key), overwrite = true) } }
            }
            // A saved song beats what was applied once.
            if (once?.first == idOf(track)) once = null
        } else {
            once = idOf(track) to ttml
        }
        return AddResult.Added(saved = save)
    }

    override suspend fun remove(id: String) = withContext(Dispatchers.IO) { store.remove(id) }

    override fun clearOnce() {
        once = null
    }

    /** What the lookup asks first: [track]'s upload applied once, else its saved lyrics, else null. */
    fun resolutionFor(track: Track): RemoteLyricsResolution.Found? {
        val id = LocalLyricsStore.keyOf(track.title, track.artist)
        val (source, ttml) = once?.takeIf { it.first == id }?.let { UPLOADED_SOURCE to it.second }
            ?: store.raw(id)?.let { LOCAL_SOURCE to it }
            ?: return null
        val payload = RemoteLyricsPayload(ttmlLyrics = ttml, sourceId = source.id)
        val selection = RemoteLyricsSelection(source, payload, payload.measuredQuality())
        val message = if (source == UPLOADED_SOURCE) "applied once" else "saved"
        return RemoteLyricsResolution.Found(
            selection,
            listOf(ProviderAttempt(source.id, ProviderAttemptOutcome.HIT, selection.quality, message = message)),
        )
    }

    companion object {
        /** `lyrics` in the app's data folder, beside the settings. */
        fun defaultDir(): File = File(
            FileSettingsStore.appDataDir(System.getProperty("os.name").orEmpty(), System::getenv, System.getProperty("user.home")),
            "lyrics",
        )

        /** Whether [ttml] draws as lyrics at all. */
        internal fun usable(ttml: String, durationMs: Long): Boolean = runCatching {
            val payload = RemoteLyricsPayload(ttmlLyrics = ttml, sourceId = LOCAL_SOURCE.id)
            RemoteLyricsAdapter.render(RemoteLyricsSelection(LOCAL_SOURCE, payload, payload.measuredQuality()), durationMs)
        }.getOrNull()?.lines?.isNotEmpty() == true

        /** [text] as TTML: an LRC file (by name or by look) converted, anything else taken as TTML. */
        internal fun toTtml(text: String, fileName: String?): Converted {
            val extension = fileName?.substringAfterLast('.', "")?.lowercase().orEmpty()
            return if (extension == "lrc" || (extension != "ttml" && LrcConverter.isLrc(text))) {
                LrcConverter.toTtml(text)?.let(Converted::Ttml) ?: Converted.Refused("That LRC file has no timed lines.")
            } else {
                Converted.Ttml(text)
            }
        }
    }

    internal sealed interface Converted {
        data class Ttml(val ttml: String) : Converted
        data class Refused(val message: String) : Converted
    }
}

/**
 * [LyricsFiles] on the desktop: picks through [platform] (read as UTF-8), saves through AWT's
 * save dialog over [parent].
 */
class DesktopLyricsFiles(private val platform: PlatformActions, private val parent: Frame?) : LyricsFiles {
    override fun open(onOpened: (name: String, text: String) -> Unit) {
        DebugControl.takePick()?.let { path -> return File(path).let { onOpened(it.name, it.readText()) } }
        platform.pickFile(emptyList()) { path ->
            val file = path?.let(::File) ?: return@pickFile
            runCatching { file.readText() }.getOrNull()?.let { onOpened(file.name, it) }
        }
    }

    override fun save(suggestedName: String, text: String, onDone: (Boolean?) -> Unit) {
        val dialog = FileDialog(parent, "Save lyrics", FileDialog.SAVE)
        dialog.file = suggestedName
        dialog.isVisible = true
        val name: String? = dialog.file
        val directory: String? = dialog.directory
        if (name == null || directory == null) return onDone(null)
        onDone(runCatching { File(directory, name).writeText(text) }.isSuccess)
    }
}

/** The playing song as the library keys it. */
fun Track.asLibraryTrack() = LibraryTrack(title, artist, album, durationMs)
