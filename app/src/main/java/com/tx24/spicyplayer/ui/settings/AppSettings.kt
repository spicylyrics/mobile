package com.tx24.spicyplayer.ui.settings

import android.content.SharedPreferences
import android.os.Build
import com.tx24.spicyplayer.BuildConfig
import com.tx24.spicyplayer.ui.nowplaying.LoopKind

/**
 * The phone's settings: the shared ones ([CommonAppSettings]) in the `ui` preferences, plus the
 * ones whose types live in the app (the launcher icon, the paused loops).
 */
class AppSettings(prefs: SharedPreferences) : CommonAppSettings(
    store = SharedPreferencesStore(prefs),
    pipButtonByDefault = Build.VERSION.SDK_INT < Build.VERSION_CODES.S,
    // On by default while the app itself is a pre-release (0.x).
    prereleasesByDefault = BuildConfig.VERSION_NAME.startsWith("0."),
) {
    /** The loops that hold their frame while the music is paused; both kinds by default. */
    var pauseWithMusic by setting("pauseWithMusic", String::class, {
        store.getString("pauseWithMusic")?.let(LoopKind::parse) ?: LoopKind.entries.toSet()
    }) { store.putString("pauseWithMusic", LoopKind.store(it)) }

    /** Which launcher icon shows (`AppIcon.apply`). */
    var appIcon by setting("appIcon", String::class, {
        store.getString("appIcon")?.let { name -> AppIcon.entries.firstOrNull { it.name == name } } ?: AppIcon.Purple
    }) { store.putString("appIcon", it.name) }

    companion object {
        /** Sung words grow and lift this much more than the desktop amount, which reads better on a phone. */
        const val WORD_MOTION_BOOST = CommonAppSettings.WORD_MOTION_BOOST
    }
}
