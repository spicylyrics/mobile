package com.tx24.spicyplayer.desktop

import com.tx24.spicyplayer.ui.settings.LyricsFont
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DesktopFontFileTest {
    private val dir: File = Files.createTempDirectory("desktop-fonts").toFile()
    private val fonts = File(dir, "fonts")

    @After fun cleanUp() {
        dir.deleteRecursively()
    }

    /** A file that starts like a TrueType font; enough for the checks, never drawn. */
    private fun fakeFont(name: String) = File(dir, name).apply { writeBytes(byteArrayOf(0, 1, 0, 0) + ByteArray(64)) }

    @Test fun aPickedFontIsCopiedInAndOnlyTheNewestKept() {
        val first = DesktopFontFile.import(fakeFont("First Font.ttf"), fonts)!!
        assertEquals("First Font", first.displayName)
        assertTrue(File(fonts, first.fileName).isFile)
        Thread.sleep(5)
        val second = DesktopFontFile.import(fakeFont("Second.otf"), fonts)!!
        assertEquals(listOf(second.fileName), fonts.list()!!.toList())
    }

    @Test fun aFileThatIsntAFontIsRefused() {
        val text = File(dir, "notes.ttf").apply { writeText("not a font at all") }
        assertNull(DesktopFontFile.import(text, fonts))
        assertNull(DesktopFontFile.import(File(dir, "missing.ttf"), fonts))
        assertFalse(fonts.exists() && fonts.list()!!.isNotEmpty())
    }

    @Test fun customUsesThePickedFont() {
        val imported = DesktopFontFile.import(fakeFont("Mine.ttf"), fonts)!!
        val font = desktopLyricsFont(LyricsFont.Custom, imported.fileName, fonts)
        assertNotNull(font.family)
        assertEquals("Custom:${imported.fileName}", font.key)
    }

    @Test fun customFallsBackToOursWhenTheFileHasGoneOrIsntAFont() {
        val ours = desktopLyricsFont(LyricsFont.Default)
        assertEquals(ours, desktopLyricsFont(LyricsFont.Custom, "", fonts))
        assertEquals(ours, desktopLyricsFont(LyricsFont.Custom, "lyrics-font-1", fonts))
        val imported = DesktopFontFile.import(fakeFont("Mine.ttf"), fonts)!!
        File(fonts, imported.fileName).writeText("damaged")
        assertEquals(ours, desktopLyricsFont(LyricsFont.Custom, imported.fileName, fonts))
    }
}
