package com.tx24.spicyplayer.ui.settings

import android.content.SharedPreferences
import com.tx24.spicyplayer.analytics.UsageStats
import com.tx24.spicyplayer.ui.nowplaying.LoopKind
import org.junit.Assert.assertEquals
import org.junit.Test

/** The phone's own settings keep their keys and defaults; the shared ones are pinned in `:ui`. */
class AppSettingsKeysTest {
    @Test fun theAppsOwnSettingsKeepTheirKeysAndDefaults() {
        val settings = AppSettings(Prefs(emptyMap()))
        assertEquals(LoopKind.entries.toSet(), settings.pauseWithMusic)
        assertEquals(AppIcon.Purple, settings.appIcon)
        assertEquals(String::class, settings.storedTypes["pauseWithMusic"])
        assertEquals(String::class, settings.storedTypes["appIcon"])
    }

    @Test fun theyReadWhatEarlierVersionsWrote() {
        val settings = AppSettings(Prefs(mapOf("pauseWithMusic" to "Canvas", "appIcon" to "Pride")))
        assertEquals(setOf(LoopKind.Canvas), settings.pauseWithMusic)
        assertEquals(AppIcon.Pride, settings.appIcon)
        assertEquals(AppIcon.Purple, AppSettings(Prefs(mapOf("appIcon" to 2))).appIcon)
    }

    @Test fun theSharedNamesMatchTheAppsTypes() {
        assertEquals(LoopKind.entries.map { it.name to it.label }, LOOP_KINDS.map { it.name to it.label })
        assertEquals(AppIcon.entries.map { it.name to it.label }, APP_ICONS.map { it.name to it.label })
    }

    @Test fun theTypedViewsShareTheStoredState() {
        val settings = AppSettings(Prefs(mapOf("pauseWithMusic" to "Canvas")))
        assertEquals(setOf("Canvas"), settings.pauseWithMusicKinds)
        assertEquals(setOf(LoopKind.Canvas), settings.pauseWithMusic)
        assertEquals("Purple", settings.appIconName)
    }

    @Test fun usageStatsReadsTheSameKeys() {
        assertEquals(UsageStats.KEY_ENABLED, CommonAppSettings.KEY_USAGE_STATS)
        assertEquals(UsageStats.KEY_ASKED, CommonAppSettings.KEY_USAGE_STATS_ASKED)
        assertEquals(CommonAppSettings.WORD_MOTION_BOOST, AppSettings.WORD_MOTION_BOOST)
    }

    /** Read-only, and casting like Android's: a value of another type throws. */
    private class Prefs(private val values: Map<String, Any>) : SharedPreferences {
        override fun getAll() = values
        override fun getString(key: String, defValue: String?) = values[key]?.let { it as String } ?: defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?) =
            @Suppress("UNCHECKED_CAST") (values[key] as Set<String>?)?.toMutableSet() ?: defValues
        override fun getInt(key: String, defValue: Int) = values[key]?.let { it as Int } ?: defValue
        override fun getLong(key: String, defValue: Long) = values[key]?.let { it as Long } ?: defValue
        override fun getFloat(key: String, defValue: Float) = values[key]?.let { it as Float } ?: defValue
        override fun getBoolean(key: String, defValue: Boolean) = values[key]?.let { it as Boolean } ?: defValue
        override fun contains(key: String) = key in values
        override fun edit(): SharedPreferences.Editor = error("read-only")
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }
}
