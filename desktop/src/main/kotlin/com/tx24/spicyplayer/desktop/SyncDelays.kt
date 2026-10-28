package com.tx24.spicyplayer.desktop

import com.tx24.spicyplayer.ui.settings.FileSettingsStore
import com.tx24.spicyplayer.ui.settings.SettingsStore
import java.io.File

/**
 * The saved lyric offset per player, in ms (positive shows the lyrics earlier), under
 * `desktop.offset.<app id>`. A [flagMs] from `--offset` wins for every player while given.
 * Unsaved players start at 0: the per-player leads measured so far are corrected in [MediaSession].
 */
class SyncDelays(private val store: SettingsStore, private val flagMs: Long? = null) {
    /** Whether `--offset` overrides the saved values. */
    val flagged get() = flagMs != null

    fun offsetFor(app: String): Long = flagMs ?: store.getLong(key(app)) ?: 0L

    fun set(app: String, offsetMs: Long) = store.putLong(key(app), offsetMs)

    companion object {
        fun key(app: String) = "desktop.offset.${app.trim().lowercase()}"

        /**
         * The offsets' own file beside the settings file. Not `settings.properties` itself: each
         * [FileSettingsStore] rewrites its whole file from what it holds, so two on one file undo
         * each other's writes.
         */
        fun defaultStore(): FileSettingsStore = FileSettingsStore(
            File(FileSettingsStore.appDataDir(System.getProperty("os.name").orEmpty(), System::getenv, System.getProperty("user.home")), FILE_NAME),
        )

        const val FILE_NAME = "sync-delay.properties"
    }
}
