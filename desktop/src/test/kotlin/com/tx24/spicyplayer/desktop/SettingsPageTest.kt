package com.tx24.spicyplayer.desktop

import com.tx24.spicyplayer.ui.settings.CommonAppSettings
import com.tx24.spicyplayer.ui.settings.InMemorySettingsStore
import com.tx24.spicyplayer.ui.settings.panel.PanelActions
import com.tx24.spicyplayer.ui.settings.panel.settingsPanelSections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPageTest {
    private val settings = CommonAppSettings(InMemorySettingsStore(), pipButtonByDefault = false, prereleasesByDefault = true)
    private fun rowIds(actions: PanelActions, hidden: Set<String> = emptySet()) =
        settingsPanelSections(settings, actions, hiddenRows = hidden).flatMap { it.rows }.map { it.id }

    @Test
    fun `the rows the desktop hides are real rows, and gone from its panel`() {
        val all = rowIds(PanelActions(lyricsManagerButton = true))
        assertTrue("A hidden id names no row: ${DESKTOP_HIDDEN_ROWS - all.toSet()}", all.containsAll(DESKTOP_HIDDEN_ROWS))
        val desktop = rowIds(PanelActions(lyricsManagerButton = true), DESKTOP_HIDDEN_ROWS)
        assertEquals(all.filter { it !in DESKTOP_HIDDEN_ROWS }, desktop)
        assertTrue("lyricsManagerButton" in desktop)
    }
}
