package com.tx24.spicyplayer.ui.settings

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * What the settings ask of the system around them. Each returns false (or hands back null) where
 * the platform has no such thing, so a page can say so instead of doing nothing.
 */
interface PlatformActions {
    /** Opens [url] in the browser. */
    fun openUrl(url: String): Boolean

    /** The system page that grants the app access to other apps' media sessions. */
    fun openNotificationAccessSettings(): Boolean

    /** The system page for this app (permissions, storage, battery). */
    fun openAppSettings(): Boolean

    /**
     * Asks for a file of one of [mimeTypes] (empty for any) and hands [onPicked] where it is (a
     * content URI on Android, a path on the desktop), or null if none was picked.
     */
    fun pickFile(mimeTypes: List<String>, onPicked: (String?) -> Unit)

    /** Asks for a folder, handed to [onPicked] like [pickFile]'s file. */
    fun pickFolder(onPicked: (String?) -> Unit)
}

/** A platform that does none of it: for tests and previews. */
object NoPlatformActions : PlatformActions {
    override fun openUrl(url: String) = false
    override fun openNotificationAccessSettings() = false
    override fun openAppSettings() = false
    override fun pickFile(mimeTypes: List<String>, onPicked: (String?) -> Unit) = onPicked(null)
    override fun pickFolder(onPicked: (String?) -> Unit) = onPicked(null)
}

/** The platform's actions, for the shared settings pages. */
val LocalPlatformActions = staticCompositionLocalOf<PlatformActions> { NoPlatformActions }
