package com.tx24.spicyplayer.ui.settings

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FileSettingsStoreTest {
    private val dir: File = Files.createTempDirectory("settings-store").toFile()
    private val file = File(dir, "nested/settings.properties")

    @After fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test fun everyTypeSurvivesARestart() {
        FileSettingsStore(file).apply {
            putBoolean("glow", false)
            putInt("scrollLeadMs", 420)
            putLong("lastCheck", 1_761_600_000_000L)
            putFloat("scale", 1.25f)
            putString("lyricsStyle", "AppleMusic: with = and \\ and ünïcode\nand a new line")
            putStringSet("languages", setOf("ja", "ko", ""))
            putStringSet("nothing", emptySet())
        }
        val reopened = FileSettingsStore(file)
        assertEquals(false, reopened.getBoolean("glow"))
        assertEquals(420, reopened.getInt("scrollLeadMs"))
        assertEquals(1_761_600_000_000L, reopened.getLong("lastCheck"))
        assertEquals(1.25f, reopened.getFloat("scale"))
        assertEquals("AppleMusic: with = and \\ and ünïcode\nand a new line", reopened.getString("lyricsStyle"))
        assertEquals(setOf("ja", "ko", ""), reopened.getStringSet("languages"))
        assertEquals(emptySet<String>(), reopened.getStringSet("nothing"))
        assertEquals(setOf("glow", "scrollLeadMs", "lastCheck", "scale", "lyricsStyle", "languages", "nothing"), reopened.keys)
    }

    @Test fun aValueOfAnotherTypeReadsAsUnset() {
        val store = FileSettingsStore(file)
        store.putString("lowPerformance", "true")
        store.putInt("backgroundBlur", 3)
        assertNull(store.getBoolean("lowPerformance"))
        assertNull(store.getLong("backgroundBlur"))
        assertNull(store.getString("backgroundBlur"))
        assertNull(store.getInt("missing"))
        assertTrue("lowPerformance" in store)
        assertFalse("missing" in store)
    }

    @Test fun removeForgetsTheKeyOnDisk() {
        FileSettingsStore(file).apply {
            putBoolean("introDone", true)
            remove("introDone")
        }
        assertFalse("introDone" in FileSettingsStore(file))
    }

    @Test fun listenersHearChangedKeysOnly() {
        val store = FileSettingsStore(file)
        val heard = mutableListOf<String>()
        val listener = SettingsStore.Listener { heard += it }
        store.addListener(listener)
        store.putBoolean("glow", true)
        store.putBoolean("glow", true)
        store.putInt("scrollLeadMs", 1)
        store.remove("missing")
        store.removeListener(listener)
        store.putBoolean("glow", false)
        assertEquals(listOf("glow", "scrollLeadMs"), heard)
    }

    @Test fun anUnreadableFileStartsEmpty() {
        file.parentFile.mkdirs()
        file.writeText("broken=\\uZZZZ\n")
        assertTrue(FileSettingsStore(file).keys.isEmpty())
        assertTrue(FileSettingsStore(File(dir, "missing.properties")).keys.isEmpty())
    }

    @Test fun handEditedValuesWithoutATypeAreIgnored() {
        file.parentFile.mkdirs()
        file.writeText("glow=false\nscrollLeadMs=i:12\n")
        val store = FileSettingsStore(file)
        assertNull(store.getBoolean("glow"))
        assertEquals(12, store.getInt("scrollLeadMs"))
    }

    @Test fun theFileLivesInTheAppsOwnFolder() {
        val windows = FileSettingsStore.appDataDir("Windows 11", { if (it == "APPDATA") "C:\\Users\\me\\AppData\\Roaming" else null }, "C:\\Users\\me")
        assertEquals(File("C:\\Users\\me\\AppData\\Roaming", "Spicy Lyrics Desktop"), windows)
        assertEquals(File("/home/me/.config", "spicy-lyrics-desktop"), FileSettingsStore.appDataDir("Linux", { null }, "/home/me"))
        val xdg = FileSettingsStore.appDataDir("Linux", { if (it == "XDG_CONFIG_HOME") "/xdg" else null }, "/home/me")
        assertEquals(File("/xdg", "spicy-lyrics-desktop"), xdg)
        // APPDATA means nothing off Windows, and a blank XDG_CONFIG_HOME counts as unset.
        val linuxWithAppData = FileSettingsStore.appDataDir("Linux", { if (it == "APPDATA") "/wine" else if (it == "XDG_CONFIG_HOME") " " else null }, "/home/me")
        assertEquals(File("/home/me/.config", "spicy-lyrics-desktop"), linuxWithAppData)
        assertEquals("settings.properties", FileSettingsStore.FILE_NAME)
    }

    @Test fun theAppSharesOneStoreOverTheSettingsFile() {
        assertSame(FileSettingsStore.default(), FileSettingsStore.default())
    }

    @Test fun commonAppSettingsReadBackWhatTheyWrote() {
        CommonAppSettings(FileSettingsStore(file), pipButtonByDefault = false, prereleasesByDefault = false).apply {
            backgroundType = BackgroundType.Color
            lyricsSizePortrait = 140
            glow = false
        }
        val reopened = CommonAppSettings(FileSettingsStore(file), pipButtonByDefault = false, prereleasesByDefault = false)
        assertEquals(BackgroundType.Color, reopened.backgroundType)
        assertEquals(140, reopened.lyricsSizePortrait)
        assertFalse(reopened.glow)
        assertFalse(reopened.startedEmpty)
    }
}
