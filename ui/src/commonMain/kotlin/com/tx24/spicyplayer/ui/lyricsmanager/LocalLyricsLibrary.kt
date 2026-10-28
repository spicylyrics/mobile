package com.tx24.spicyplayer.ui.lyricsmanager

import androidx.compose.runtime.staticCompositionLocalOf

/** A song saved in the lyrics library, as the list shows it. [id] is the library's own key. */
data class LibraryEntry(val id: String, val title: String, val artist: String, val album: String, val savedAt: Long)

/** The playing song, as an upload is saved for it. */
data class LibraryTrack(val title: String, val artist: String, val album: String, val durationMs: Long)

/** What became of an upload: [Added] (saved, or applied once), or [Refused] with the reason to show. */
sealed interface AddResult {
    data class Added(val saved: Boolean) : AddResult
    data class Refused(val message: String) : AddResult
}

/**
 * The lyrics a user imported, kept per song until they delete them; a saved song's lyrics beat
 * every online source. Each platform keeps them its own way.
 */
interface LocalLyricsLibrary {
    /** Every saved song, newest first. */
    suspend fun entries(): List<LibraryEntry>

    /** The TTML saved under [id], or null. */
    suspend fun raw(id: String): String?

    /** The cover saved with [id] (an encoded image), or null. */
    suspend fun cover(id: String): ByteArray?

    /** The id [track] is (or would be) saved under, to find the playing song in the list. */
    fun idOf(track: LibraryTrack): String

    /**
     * Takes [text], a TTML or LRC file named [fileName], for [track]: saved when [save], else
     * shown for now only. Refused, with why, when it isn't lyrics that can be shown.
     */
    suspend fun add(track: LibraryTrack, text: String, fileName: String?, save: Boolean): AddResult

    suspend fun remove(id: String)

    /** Drops lyrics applied just once, so the song's usual lyrics come back. */
    fun clearOnce()
}

/** Opening and saving files for the library; each platform has its own pickers. */
interface LyricsFiles {
    /** Asks for a .ttml or .lrc file and hands back its name and text; nothing if none was picked. */
    fun open(onOpened: (name: String, text: String) -> Unit)

    /** Offers [text] to save as [suggestedName]; [onDone] says whether it was written, null if cancelled. */
    fun save(suggestedName: String, text: String, onDone: (Boolean?) -> Unit)
}

val LocalLyricsFiles = staticCompositionLocalOf<LyricsFiles?> { null }

/** Entries whose title, artist or id hold [query] (all of them for a blank one). */
fun filterLibrary(entries: List<LibraryEntry>, query: String): List<LibraryEntry> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return entries
    return entries.filter { needle in it.title.lowercase() || needle in it.artist.lowercase() || needle in it.id.lowercase() }
}

/** [entries] with the playing song's ([currentId]) moved to the top, the rest in their order. */
fun pinCurrent(entries: List<LibraryEntry>, currentId: String?): List<LibraryEntry> {
    val current = entries.firstOrNull { it.id == currentId } ?: return entries
    return listOf(current) + entries.filter { it !== current }
}

/**
 * A file name from [name], 100 characters at most: what file systems refuse (`<>:"/\|?*` and
 * control characters) becomes `_`, and trailing dots and spaces go. Letters in any script stay.
 */
fun sanitizeFilename(name: String): String {
    val cut = name.replace(Regex("""[<>:"/\\|?*\x00-\x1F\x7F]"""), "_").take(100)
    // Don't leave half of a character outside the Basic Multilingual Plane.
    val whole = if (cut.lastOrNull()?.isHighSurrogate() == true) cut.dropLast(1) else cut
    return whole.trimEnd('.', ' ').ifEmpty { "lyrics" }
}

/** Whether [fileName] is a kind of file the library takes. A name without an extension is let through to be read. */
fun acceptsFile(fileName: String?): Boolean {
    val extension = fileName?.substringAfterLast('.', "")?.lowercase().orEmpty()
    return extension.isEmpty() || extension == "ttml" || extension == "lrc" || extension == "xml"
}
