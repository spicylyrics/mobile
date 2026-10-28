package com.tx24.spicyplayer.ui.settings.panel

import com.tx24.spicyplayer.ui.settings.CommonAppSettings
import com.tx24.spicyplayer.ui.settings.InMemorySettingsStore
import com.tx24.spicyplayer.ui.settings.LyricsFont
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPanelSectionsTest {
    private val store = InMemorySettingsStore()
    private val settings = CommonAppSettings(store, pipButtonByDefault = false, prereleasesByDefault = false)
    private val sections = settingsPanelSections(settings, PanelActions(clearLyricsCache = {}))
    private val rows = sections.flatMap { it.rows }

    @Test
    fun `tabs are in sidebar order with About and Advanced below the divider`() {
        assertEquals(PanelSectionId.entries.toList(), sections.map { it.id })
        assertEquals(listOf(PanelSectionId.Advanced, PanelSectionId.About), sections.filter { it.secondary }.map { it.id })
    }

    @Test
    fun `every setting row names a real setting`() {
        val known = settings.storedTypes.keys
        rows.filter { it !is PanelRow.Button }.forEach { row ->
            assertNotNull("${row.id} has no key", row.key)
            assertTrue("${row.id} → ${row.key} is not a setting", row.key in known)
        }
    }

    @Test
    fun `no two rows share an id or a setting`() {
        assertEquals(rows.map { it.id }.distinct(), rows.map { it.id })
        val keys = rows.mapNotNull { it.key }
        assertEquals(keys.distinct(), keys)
    }

    /** Changing the row has to write its own key, or the row is bound to the wrong setting. */
    @Test
    fun `changing a row saves its setting`() {
        rows.forEach { row ->
            store.remove(row.key ?: return@forEach)
            when (row) {
                is PanelRow.Toggle -> row.onChange(!row.checked())
                is PanelRow.Select -> row.onChange(row.options.first { it != row.value() })
                is PanelRow.Slider -> row.onChange(if (row.value() == row.spec.max) row.spec.min else row.spec.max)
                is PanelRow.Button -> Unit
            }
            assertTrue("${row.id} didn't save ${row.key}", row.key!! in store.keys)
        }
    }

    @Test
    fun `selects show one label per option and the stored value is one of them`() {
        rows.filterIsInstance<PanelRow.Select>().forEach { row ->
            assertEquals(row.id, row.options.size, row.labels.size)
            assertTrue(row.id, row.value() in row.options)
        }
    }

    @Test
    fun `slider defaults sit inside their range`() {
        rows.filterIsInstance<PanelRow.Slider>().forEach { row ->
            row.spec.default?.let { assertEquals(row.id, it, row.spec.clamp(it)) }
            assertEquals(row.id, row.value(), row.spec.clamp(row.value()))
        }
    }

    @Test
    fun `lyrics font offers custom only when there is one`() {
        val row = rows.single { it.id == "lyricsFont" } as PanelRow.Select
        assertEquals(listOf("Default", "System"), row.options)
        row.onChange("System")
        assertEquals(LyricsFont.System, settings.lyricsFont)
        row.onChange("Default")
        assertEquals(LyricsFont.Default, settings.lyricsFont)
    }

    @Test
    fun `a custom font stays custom when the panel leaves it alone`() {
        val withFont = CommonAppSettings(
            InMemorySettingsStore(mapOf("lyricsFont" to "Custom", "customFontFile" to "fonts/mine.ttf", "customFontName" to "Mine")),
            pipButtonByDefault = false, prereleasesByDefault = false,
        )
        val row = settingsPanelSections(withFont).flatMap { it.rows }.single { it.id == "lyricsFont" } as PanelRow.Select
        assertEquals(listOf("Default", "System", "Custom"), row.options)
        assertEquals("Mine", row.labels.last())
        assertEquals("Custom", row.value())
        assertEquals(LyricsFont.Custom, withFont.lyricsFont)
        assertEquals("fonts/mine.ttf", withFont.customFontFile)
    }

    @Test
    fun `dependent rows say why they are off`() {
        val leadTime = rows.single { it.id == "scrollLeadMs" }
        settings.scrollLeadEnabled = false
        assertEquals("Turn on Early Scroll to change this.", leadTime.disabled())
        settings.scrollLeadEnabled = true
        assertNull(leadTime.disabled())
    }

    @Test
    fun `advanced has no rows without platform actions`() {
        val bare = settingsPanelSections(settings).single { it.id == PanelSectionId.Advanced }
        assertTrue(bare.rows.isEmpty())
    }

    @Test
    fun `hidden rows leave the panel, and a group left empty goes with them`() {
        assertEquals(rows.map { it.id }, settingsPanelSections(settings, PanelActions(clearLyricsCache = {}), emptySet()).flatMap { it.rows }.map { it.id })
        val hidden = setOf("headerSize", "profilesInBrowser")
        val shown = settingsPanelSections(settings, PanelActions(clearLyricsCache = {}), hidden)
        assertEquals(rows.map { it.id }.filter { it !in hidden }, shown.flatMap { it.rows }.map { it.id })
        val advanced = settingsPanelSections(settings, PanelActions(clearLyricsCache = {}), setOf("clearStoredLyricsCache"))
            .single { it.id == PanelSectionId.Advanced }
        assertTrue(advanced.groups.isEmpty())
    }

    @Test
    fun `the lyrics manager button row shows only where the platform has the button`() {
        assertTrue(rows.none { it.id == "lyricsManagerButton" })
        val withButton = settingsPanelSections(settings, PanelActions(lyricsManagerButton = true)).flatMap { it.rows }
        val row = withButton.single { it.id == "lyricsManagerButton" } as PanelRow.Toggle
        row.onChange(true)
        assertTrue(settings.lyricsManagerButton)
    }

    @Test
    fun `the font file button shows only where the platform can pick one`() {
        assertTrue(rows.none { it.id == "lyricsFontFile" })
        var picked = 0
        val withPicker = settingsPanelSections(settings, PanelActions(pickLyricsFont = { picked++ })).flatMap { it.rows }
        val ids = withPicker.map { it.id }
        assertEquals(ids.indexOf("lyricsFont") + 1, ids.indexOf("lyricsFontFile"))
        (withPicker.single { it.id == "lyricsFontFile" } as PanelRow.Button).onClick()
        assertEquals(1, picked)
    }
}
