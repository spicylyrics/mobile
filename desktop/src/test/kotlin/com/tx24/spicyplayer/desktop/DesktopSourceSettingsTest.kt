package com.tx24.spicyplayer.desktop

import com.tx24.spicyplayer.network.data.LyricsSourceDescriptor
import com.tx24.spicyplayer.network.data.LyricsSourceSettings
import com.tx24.spicyplayer.network.data.RemoteLyricsPolicy
import com.tx24.spicyplayer.ui.settings.InMemorySettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopSourceSettingsTest {
    // Ids no built-in source lists, so each is a source of its own; "asks" starts off and asks first.
    private val providers = listOf(
        LyricsSourceDescriptor("own-a", "Own A", 0, emptySet()),
        LyricsSourceDescriptor("own-b", "Own B", 1, emptySet()),
        LyricsSourceDescriptor("asks", "Asks First", 2, emptySet(), defaultEnabled = false),
    )

    @Test fun nothingStoredMeansTheDefaults() {
        val sources = DesktopSourceSettings(InMemorySettingsStore(), providers)
        assertEquals(LyricsSourceSettings.normalized(RemoteLyricsPolicy(), providers), sources.policy())
        assertEquals(listOf("own-a", "own-b", "asks"), sources.origins().map { it.id })
        assertTrue(sources.isOn("own-a"))
        assertFalse(sources.isOn("asks"))
    }

    @Test fun aSwitchIsKeptUnderThePhonesKeysAndHandedToTheLookup() {
        val store = InMemorySettingsStore()
        var handed: RemoteLyricsPolicy? = null
        val sources = DesktopSourceSettings(store, providers) { handed = it }
        sources.setSourceEnabled("own-b", false)
        assertFalse(sources.isOn("own-b"))
        assertEquals(sources.policy(), handed)
        assertEquals("own-a,own-b,asks", store.getString(DesktopSourceSettings.SOURCE_ORDER))
        assertEquals(setOf("own-b"), store.getStringSet(DesktopSourceSettings.SOURCES_OFF))
        // A new run reads the same choices back.
        assertFalse(DesktopSourceSettings(store, providers).isOn("own-b"))
        assertTrue(DesktopSourceSettings(store, providers).isOn("own-a"))
    }

    @Test fun aSourceThatAsksFirstIsAskedOnceAndKeepsTheTranslationConsents() {
        val store = InMemorySettingsStore().apply { putString(DesktopLyrics.KEY_DISCLOSED, "translation_google") }
        val sources = DesktopSourceSettings(store, providers)
        assertNull(sources.disclosureFor("own-a"))
        val disclosure = sources.disclosureFor("asks")
        assertNotNull(disclosure)
        sources.agree(disclosure!!)
        sources.setSourceEnabled("asks", true)
        assertTrue(sources.isOn("asks"))
        assertNull(sources.disclosureFor("asks"))
        assertEquals("asks,translation_google", store.getString(DesktopLyrics.KEY_DISCLOSED))
    }
}
