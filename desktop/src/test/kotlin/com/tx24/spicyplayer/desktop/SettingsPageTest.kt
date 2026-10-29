package com.tx24.spicyplayer.desktop

import com.tx24.spicyplayer.ui.settings.InMemorySettingsStore
import com.tx24.spicyplayer.ui.settings.SettingsPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsPageTest {
    @get:Rule val dir = TemporaryFolder()

    private fun host(sources: DesktopSourceSettings? = null) =
        DesktopSettingsHost(null, MediaSession(null, 0L), SyncDelays(InMemorySettingsStore()), sources, DesktopLyricsLibrary(dir.root))

    @Test
    fun `the desktop leaves out the pages only a phone has`() {
        val pages = SettingsPage.entries.filter(host()::hasPage)
        assertEquals(
            listOf(SettingsPage.Lyrics, SettingsPage.ScrollSync, SettingsPage.Background, SettingsPage.NowPlaying, SettingsPage.Controls, SettingsPage.Theme, SettingsPage.Device),
            pages,
        )
    }

    @Test
    fun `the desktop has the Lyrics Manager but no song delay yet`() {
        assertTrue(host().lyricsManager)
        assertFalse(host().songDelay)
    }
}
