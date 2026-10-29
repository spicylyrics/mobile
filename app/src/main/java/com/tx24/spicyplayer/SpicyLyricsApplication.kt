package com.tx24.spicyplayer

import android.app.Application
import com.tx24.spicyplayer.playback.CrashLog

/**
 * Process start, whatever started it: the lyrics screen, the widget, the notification listener or
 * the updater's receiver. Crashes are recorded from here so those without the screen count too.
 */
class SpicyLyricsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
