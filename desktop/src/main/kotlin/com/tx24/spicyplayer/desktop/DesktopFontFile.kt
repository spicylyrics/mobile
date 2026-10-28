package com.tx24.spicyplayer.desktop

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import com.tx24.spicyplayer.ui.settings.FileSettingsStore
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * A font file the user picked for the lyrics, copied into the app's data folder so it stays
 * readable if the original moves. Each pick gets a new name, as on the phone: a font is cached by
 * its name once loaded, so reusing one would keep drawing the previous font. The settings keep
 * the copy's name (`customFontFile`) and the font's (`customFontName`).
 */
object DesktopFontFile {
    class Imported(val fileName: String, val displayName: String)

    /** `fonts` in the app's data folder, beside the settings. */
    val defaultDir: File by lazy {
        File(FileSettingsStore.appDataDir(System.getProperty("os.name").orEmpty(), System::getenv, System.getProperty("user.home")), "fonts")
    }

    /** What the file picker offers. */
    val MIME_TYPES = listOf("font/ttf", "font/otf")

    /** Copies the font at [source] into [dir], or returns null when it isn't a TrueType or OpenType font. */
    fun import(source: File, dir: File = defaultDir): Imported? {
        if (!isFont(source)) return null
        val file = File(dir, "lyrics-font-${System.currentTimeMillis()}")
        try {
            dir.mkdirs()
            source.copyTo(file, overwrite = true)
        } catch (e: IOException) {
            System.err.println("[Fonts] Couldn't copy $source: $e")
            file.delete()
            return null
        }
        // Only the newest pick is kept.
        dir.listFiles()?.filter { it != file }?.forEach { it.delete() }
        return Imported(file.name, source.name.substringBeforeLast('.').ifBlank { source.name })
    }

    /** The picked font as a family, or null when the file has gone or isn't a font any more. */
    fun family(fileName: String, dir: File = defaultDir): FontFamily? {
        if (fileName.isBlank()) return null
        val file = File(dir, fileName)
        if (!isFont(file)) return null
        val bytes = try {
            file.readBytes()
        } catch (e: IOException) {
            System.err.println("[Fonts] Couldn't read $file: $e")
            return null
        }
        return FontFamily(Font(fileName, bytes, FontWeight.Normal))
    }

    /** A readable file that starts like an OpenType or TrueType font (or collection). */
    internal fun isFont(file: File): Boolean {
        if (!file.isFile) return false
        val tag = try {
            RandomAccessFile(file, "r").use { it.readInt() }
        } catch (_: IOException) {
            return false
        }
        return tag in FONT_TAGS
    }

    private val FONT_TAGS = setOf(0x00010000, 0x4F54544F /* OTTO */, 0x74727565 /* true */, 0x74746366 /* ttcf */)
}
