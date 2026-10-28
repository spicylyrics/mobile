package com.tx24.spicyplayer.ui.settings

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Collections
import java.util.Properties
import java.util.WeakHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * [SettingsStore] in a properties file. A change lands in memory at once; the file is rewritten
 * whole at most every [saveDelayMs] on one background thread (temp file, then moved over the old
 * one, so a crash mid-write leaves the last good copy), and [flush] or the app's exit writes what
 * is still waiting. Each value carries its type as a prefix (`b:true`, `i:42`, `s:text`), so
 * reading one as the wrong type gives null as on Android.
 *
 * A file rather than `java.util.prefs`: on Windows that is the registry, with its own escaping and
 * value-length limits, and a file can be read, backed up or deleted by hand.
 *
 * Each store reads its file once and writes all of it back on each save, so two stores over one
 * file overwrite each other's changes. Use [default] for `settings.properties`; never construct a
 * second store over the same file.
 */
class FileSettingsStore(private val file: File, private val saveDelayMs: Long = SAVE_DELAY_MS) : SettingsStore {
    private val lock = Any()
    /** Held while writing, so two writes never cross and the newest values land last. */
    private val writeLock = Any()
    private val values: MutableMap<String, String> = load(file)
    private val listeners = mutableListOf<SettingsStore.Listener>()
    /** Guarded by [lock]: [values] has changes not on disk yet, and whether a save is queued for them. */
    private var dirty = false
    private var saveQueued = false

    init {
        stores += this
    }

    override val keys: Set<String> get() = synchronized(lock) { values.keys.toSet() }
    override fun contains(key: String) = synchronized(lock) { key in values }

    override fun getBoolean(key: String) = raw(key, BOOLEAN)?.toBooleanStrictOrNull()
    override fun getInt(key: String) = raw(key, INT)?.toIntOrNull()
    override fun getLong(key: String) = raw(key, LONG)?.toLongOrNull()
    override fun getFloat(key: String) = raw(key, FLOAT)?.toFloatOrNull()
    override fun getString(key: String) = raw(key, STRING)
    override fun getStringSet(key: String) = raw(key, STRING_SET)?.let(::decodeSet)

    override fun putBoolean(key: String, value: Boolean) = put(key, BOOLEAN, value.toString())
    override fun putInt(key: String, value: Int) = put(key, INT, value.toString())
    override fun putLong(key: String, value: Long) = put(key, LONG, value.toString())
    override fun putFloat(key: String, value: Float) = put(key, FLOAT, value.toString())
    override fun putString(key: String, value: String) = put(key, STRING, value)
    override fun putStringSet(key: String, value: Set<String>) = put(key, STRING_SET, encodeSet(value))

    override fun remove(key: String) {
        val removed = synchronized(lock) { (values.remove(key) != null).also { if (it) queueSave() } }
        if (removed) notify(key)
    }

    override fun addListener(listener: SettingsStore.Listener) {
        synchronized(lock) { listeners += listener }
    }

    override fun removeListener(listener: SettingsStore.Listener) {
        synchronized(lock) { listeners -= listener }
    }

    private fun raw(key: String, type: Char): String? {
        val stored = synchronized(lock) { values[key] } ?: return null
        return if (stored.length >= 2 && stored[0] == type && stored[1] == ':') stored.substring(2) else null
    }

    private fun put(key: String, type: Char, value: String) {
        val stored = "$type:$value"
        val changed = synchronized(lock) { (values.put(key, stored) != stored).also { if (it) queueSave() } }
        if (changed) notify(key)
    }

    private fun notify(key: String) {
        val current = synchronized(lock) { listeners.toList() }
        current.forEach { it.onChanged(key) }
    }

    /**
     * Writes the changes still waiting now, on the caller's thread. A failed write is logged and
     * stays waiting, so the next change or the app's exit tries again; the values stay in memory
     * for this run either way.
     */
    fun flush() {
        synchronized(writeLock) {
            val snapshot = synchronized(lock) {
                saveQueued = false
                if (!dirty) return
                dirty = false
                values.toMap()
            }
            if (!write(snapshot)) synchronized(lock) { dirty = true }
        }
    }

    /** Called holding [lock]: one save at most per [saveDelayMs], however many changes come in. */
    private fun queueSave() {
        dirty = true
        if (saveQueued) return
        saveQueued = true
        saver.schedule({ flush() }, saveDelayMs, TimeUnit.MILLISECONDS)
    }

    private fun write(snapshot: Map<String, String>): Boolean {
        val properties = Properties()
        snapshot.forEach { (k, v) -> properties.setProperty(k, v) }
        return try {
            file.absoluteFile.parentFile?.mkdirs()
            val temp = File(file.absolutePath + ".tmp")
            temp.outputStream().use { properties.store(it, "Spicy Lyrics settings") }
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            true
        } catch (e: IOException) {
            System.err.println("[Settings] Couldn't save $file: $e")
            false
        }
    }

    companion object {
        private const val BOOLEAN = 'b'
        private const val INT = 'i'
        private const val LONG = 'l'
        private const val FLOAT = 'f'
        private const val STRING = 's'
        private const val STRING_SET = 'S'

        /** The shortest time between two writes of one file: a dragged slider writes once or twice, not a hundred times. */
        const val SAVE_DELAY_MS = 150L

        /** The one thread every store writes on; a daemon, so it never keeps the app open. */
        private val saver = Executors.newSingleThreadScheduledExecutor { Thread(it, "settings-saver").apply { isDaemon = true } }

        /** Every store made, so the app's exit writes what each still has waiting. */
        private val stores: MutableSet<FileSettingsStore> = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))

        init {
            Runtime.getRuntime().addShutdownHook(Thread({ synchronized(stores) { stores.toList() }.forEach { it.flush() } }, "settings-flush"))
        }

        /** Ends each element of a stored set, so an empty set and a set of one empty string differ. */
        private const val SET_END = '\u0000'

        private val shared by lazy {
            FileSettingsStore(File(appDataDir(System.getProperty("os.name").orEmpty(), System::getenv, System.getProperty("user.home")), FILE_NAME))
        }

        /** The one store over the settings file in the app's own folder ([appDataDir]), shared by the whole app. */
        fun default(): FileSettingsStore = shared

        const val FILE_NAME = "settings.properties"

        /**
         * The app's data folder: `%APPDATA%/Spicy Lyrics Desktop` on Windows, else
         * `$XDG_CONFIG_HOME/spicy-lyrics-desktop` or `~/.config/spicy-lyrics-desktop`.
         */
        fun appDataDir(osName: String, env: (String) -> String?, home: String): File {
            val appData = env("APPDATA")?.takeIf(String::isNotBlank)
            if (osName.startsWith("Windows", ignoreCase = true) && appData != null) return File(appData, "Spicy Lyrics Desktop")
            val config = env("XDG_CONFIG_HOME")?.takeIf(String::isNotBlank) ?: File(home, ".config").path
            return File(config, "spicy-lyrics-desktop")
        }

        private fun load(file: File): MutableMap<String, String> {
            val properties = Properties()
            try {
                if (file.isFile) file.inputStream().use { properties.load(it) }
            } catch (_: IOException) {
            } catch (_: IllegalArgumentException) {
                // A malformed \u escape: start empty rather than not start.
            }
            val loaded = mutableMapOf<String, String>()
            for (name in properties.stringPropertyNames()) loaded[name] = properties.getProperty(name)
            return loaded
        }

        internal fun encodeSet(set: Set<String>): String = set.joinToString("") { it + SET_END }

        internal fun decodeSet(stored: String): Set<String>? {
            if (stored.isEmpty()) return emptySet()
            if (!stored.endsWith(SET_END)) return null
            return stored.dropLast(1).split(SET_END).toSet()
        }
    }
}
