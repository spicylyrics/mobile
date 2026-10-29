package com.tx24.spicyplayer.desktop

import com.tx24.spicyplayer.ui.settings.InMemorySettingsStore
import com.tx24.spicyplayer.ui.settings.SettingsPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SettingsPageTest {
    private fun host(sources: DesktopSourceSettings? = null) =
        DesktopSettingsHost(null, MediaSession(null, 0L), SyncDelays(InMemorySettingsStore()), sources)

    @Test
    fun `the desktop leaves out the pages only a phone has`() {
        val pages = SettingsPage.entries.filter(host()::hasPage)
        assertEquals(
            listOf(SettingsPage.Lyrics, SettingsPage.ScrollSync, SettingsPage.Background, SettingsPage.NowPlaying, SettingsPage.Controls, SettingsPage.Theme, SettingsPage.Device),
            pages,
        )
    }

    @Test
    fun `the desktop has no Lyrics Manager or song delay yet`() {
        assertFalse(host().lyricsManager)
        assertFalse(host().songDelay)
    }
}
