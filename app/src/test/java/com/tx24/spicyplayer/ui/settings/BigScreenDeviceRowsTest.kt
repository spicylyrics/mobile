package com.tx24.spicyplayer.ui.settings

import android.content.SharedPreferences
import com.tx24.spicyplayer.ui.settings.panel.PanelRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The big-screen panel's Device tab writes the same settings the phone's pages do. */
class BigScreenDeviceRowsTest {
    private val prefs = Prefs()
    private val settings = AppSettings(prefs)
    private var opened = 0
    private val rows = bigScreenDeviceGroups(settings, pipSupported = true, canPlayMusicHaptics = true, lockScreen = true) { opened++ }
        .flatMap { it.rows }

    @Test fun everyRowIsASettingOrTheWayToTheFullPages() {
        val known = settings.storedTypes.keys
        rows.forEach { row ->
            if (row is PanelRow.Button) return@forEach
            assertTrue("${row.id} → ${row.key} is not a setting", row.key in known)
        }
        assertEquals(rows.map { it.id }.distinct(), rows.map { it.id })
        (rows.single { it.id == "allSettingsPages" } as PanelRow.Button).onClick()
        assertEquals(1, opened)
    }

    @Test fun changingARowSavesItsSetting() {
        rows.forEach { row ->
            val key = row.key ?: return@forEach
            prefs.values.remove(key)
            when (row) {
                is PanelRow.Toggle -> row.onChange(!row.checked())
                is PanelRow.Select -> row.onChange(row.options.first { it != row.value() })
                is PanelRow.Slider -> row.onChange(if (row.value() == row.spec.max) row.spec.min else row.spec.max)
                is PanelRow.Button -> Unit
            }
            assertTrue("${row.id} didn't save $key", key in prefs.values)
        }
    }

    @Test fun rowsTheDeviceCantUseSayWhy() {
        val bare = bigScreenDeviceGroups(settings, pipSupported = false, canPlayMusicHaptics = false, lockScreen = false) {}
            .flatMap { it.rows }
        assertNotNull(bare.single { it.id == "autoPip" }.disabled())
        assertNotNull(bare.single { it.id == "musicHaptics" }.disabled())
        assertTrue(bare.none { it.id == "showOverLockScreen" })
        assertNull(rows.single { it.id == "autoPip" }.disabled())
    }

    /** Writable, and casting like Android's. */
    private class Prefs : SharedPreferences {
        val values = mutableMapOf<String, Any>()
        override fun getAll(): Map<String, *> = values
        override fun getString(key: String, defValue: String?) = values[key]?.let { it as String } ?: defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?) =
            @Suppress("UNCHECKED_CAST") (values[key] as Set<String>?)?.toMutableSet() ?: defValues
        override fun getInt(key: String, defValue: Int) = values[key]?.let { it as Int } ?: defValue
        override fun getLong(key: String, defValue: Long) = values[key]?.let { it as Long } ?: defValue
        override fun getFloat(key: String, defValue: Float) = values[key]?.let { it as Float } ?: defValue
        override fun getBoolean(key: String, defValue: Boolean) = values[key]?.let { it as Boolean } ?: defValue
        override fun contains(key: String) = key in values
        override fun edit(): SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        inner class Editor : SharedPreferences.Editor {
            private fun put(key: String?, value: Any?): SharedPreferences.Editor {
                if (key != null) {
                    if (value == null) values.remove(key) else values[key] = value
                }
                return this
            }
            override fun putString(key: String?, value: String?) = put(key, value)
            override fun putStringSet(key: String?, value: MutableSet<String>?) = put(key, value?.toSet())
            override fun putInt(key: String?, value: Int) = put(key, value)
            override fun putLong(key: String?, value: Long) = put(key, value)
            override fun putFloat(key: String?, value: Float) = put(key, value)
            override fun putBoolean(key: String?, value: Boolean) = put(key, value)
            override fun remove(key: String?) = put(key, null)
            override fun clear(): SharedPreferences.Editor {
                values.clear()
                return this
            }
            override fun commit() = true
            override fun apply() {}
        }
    }
}
