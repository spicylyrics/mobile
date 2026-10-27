package com.tx24.spicyplayer.ui.settings

import android.content.SharedPreferences

/**
 * [SettingsStore] over [prefs], written the way the app always wrote them (`edit().put…().apply()`),
 * so the same file and keys carry on.
 */
class SharedPreferencesStore(val prefs: SharedPreferences) : SettingsStore {
    // SharedPreferences holds its listeners weakly, so each wrapper is kept here.
    private val listeners = mutableMapOf<SettingsStore.Listener, SharedPreferences.OnSharedPreferenceChangeListener>()

    override val keys: Set<String> get() = prefs.all.keys.toSet()
    override fun contains(key: String) = prefs.contains(key)

    override fun getBoolean(key: String) = read(key) { prefs.getBoolean(key, false) }
    override fun getInt(key: String) = read(key) { prefs.getInt(key, 0) }
    override fun getLong(key: String) = read(key) { prefs.getLong(key, 0L) }
    override fun getFloat(key: String) = read(key) { prefs.getFloat(key, 0f) }
    override fun getString(key: String) = read(key) { prefs.getString(key, null) }
    override fun getStringSet(key: String) = read(key) { prefs.getStringSet(key, null)?.toSet() }

    override fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    override fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
    override fun putLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()
    override fun putFloat(key: String, value: Float) = prefs.edit().putFloat(key, value).apply()
    override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    override fun putStringSet(key: String, value: Set<String>) = prefs.edit().putStringSet(key, value).apply()
    override fun remove(key: String) = prefs.edit().remove(key).apply()

    override fun addListener(listener: SettingsStore.Listener) {
        val wrapper = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key != null) listener.onChanged(key) }
        listeners.put(listener, wrapper)?.let(prefs::unregisterOnSharedPreferenceChangeListener)
        prefs.registerOnSharedPreferenceChangeListener(wrapper)
    }

    override fun removeListener(listener: SettingsStore.Listener) {
        listeners.remove(listener)?.let(prefs::unregisterOnSharedPreferenceChangeListener)
    }

    /** Unset reads as null, and so does a value of another type, which SharedPreferences throws on. */
    private inline fun <T> read(key: String, get: () -> T): T? =
        if (!prefs.contains(key)) null else try { get() } catch (_: ClassCastException) { null }
}
