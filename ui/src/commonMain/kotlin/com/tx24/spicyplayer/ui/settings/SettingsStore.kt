package com.tx24.spicyplayer.ui.settings

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * A saved key-value store for settings: `SharedPreferences` on Android, a file on the desktop.
 *
 * A getter returns null when the key is unset or holds another type (an old or hand-edited
 * backup), so a caller falls back to its default instead of crashing.
 */
interface SettingsStore {
    /** Every key that holds a value. */
    val keys: Set<String>

    operator fun contains(key: String): Boolean

    fun getBoolean(key: String): Boolean?
    fun getInt(key: String): Int?
    fun getLong(key: String): Long?
    fun getFloat(key: String): Float?
    fun getString(key: String): String?
    fun getStringSet(key: String): Set<String>?

    fun putBoolean(key: String, value: Boolean)
    fun putInt(key: String, value: Int)
    fun putLong(key: String, value: Long)
    fun putFloat(key: String, value: Float)
    fun putString(key: String, value: String)
    fun putStringSet(key: String, value: Set<String>)

    fun remove(key: String)

    /** Called with the key after any write, through this store or behind its back where the platform tells. */
    fun interface Listener {
        fun onChanged(key: String)
    }

    fun addListener(listener: Listener)
    fun removeListener(listener: Listener)
}

/** The keys written from now on, while collected. */
fun SettingsStore.changes(): Flow<String> = callbackFlow {
    val listener = SettingsStore.Listener { trySend(it) }
    addListener(listener)
    awaitClose { removeListener(listener) }
}

/** A store held in memory only: for tests and previews. */
class InMemorySettingsStore(initial: Map<String, Any> = emptyMap()) : SettingsStore {
    private val values = initial.toMutableMap()
    private val listeners = mutableListOf<SettingsStore.Listener>()

    override val keys: Set<String> get() = values.keys.toSet()
    override fun contains(key: String) = key in values

    override fun getBoolean(key: String) = values[key] as? Boolean
    override fun getInt(key: String) = values[key] as? Int
    override fun getLong(key: String) = values[key] as? Long
    override fun getFloat(key: String) = values[key] as? Float
    override fun getString(key: String) = values[key] as? String
    override fun getStringSet(key: String): Set<String>? =
        (values[key] as? Set<*>)?.takeIf { set -> set.all { it is String } }?.map { it as String }?.toSet()

    override fun putBoolean(key: String, value: Boolean) = put(key, value)
    override fun putInt(key: String, value: Int) = put(key, value)
    override fun putLong(key: String, value: Long) = put(key, value)
    override fun putFloat(key: String, value: Float) = put(key, value)
    override fun putString(key: String, value: String) = put(key, value)
    override fun putStringSet(key: String, value: Set<String>) = put(key, value.toSet())

    override fun remove(key: String) {
        if (values.remove(key) != null) notify(key)
    }

    override fun addListener(listener: SettingsStore.Listener) { listeners += listener }
    override fun removeListener(listener: SettingsStore.Listener) { listeners -= listener }

    private fun put(key: String, value: Any) {
        values[key] = value
        notify(key)
    }

    private fun notify(key: String) = listeners.toList().forEach { it.onChanged(key) }
}
