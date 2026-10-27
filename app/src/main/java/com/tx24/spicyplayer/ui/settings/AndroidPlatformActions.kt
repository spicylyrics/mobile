package com.tx24.spicyplayer.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * [PlatformActions] on the phone: intents for the browser and the system pages, the Storage Access
 * Framework for picking. Remembered in composition, since the pickers are activity results.
 */
@Composable
fun rememberAndroidPlatformActions(): PlatformActions {
    val context = LocalContext.current
    val pending = remember { PendingPick() }
    val file = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pending.finish(uri?.toString()) }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> pending.finish(uri?.toString()) }
    return remember(context, file, folder) {
        object : PlatformActions {
            override fun openUrl(url: String) = context.start(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

            override fun openNotificationAccessSettings() =
                context.start(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))

            override fun openAppSettings() =
                context.start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))

            override fun pickFile(mimeTypes: List<String>, onPicked: (String?) -> Unit) {
                pending.start(onPicked)
                runCatching { file.launch(mimeTypes.ifEmpty { listOf("*/*") }.toTypedArray()) }.onFailure { pending.finish(null) }
            }

            override fun pickFolder(onPicked: (String?) -> Unit) {
                pending.start(onPicked)
                runCatching { folder.launch(null) }.onFailure { pending.finish(null) }
            }
        }
    }
}

/** The one pick waiting for its result; a new pick answers the old one with null. */
private class PendingPick {
    private var callback: ((String?) -> Unit)? = null

    fun start(onPicked: (String?) -> Unit) {
        finish(null)
        callback = onPicked
    }

    fun finish(picked: String?) {
        val done = callback ?: return
        callback = null
        done(picked)
    }
}

private fun Context.start(intent: Intent): Boolean = try {
    if (this !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}
