package com.tx24.spicyplayer.ui.settings

import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.URI
import javax.swing.JFileChooser

/**
 * [PlatformActions] on the desktop: the default browser, and AWT's and Swing's pickers. Windows
 * has no notification-access or per-app settings page to open, so those say no.
 */
class DesktopPlatformActions(private val parent: Frame? = null) : PlatformActions {
    override fun openUrl(url: String): Boolean = try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
            true
        } else {
            false
        }
    } catch (_: Exception) {
        false
    }

    override fun openNotificationAccessSettings() = false
    override fun openAppSettings() = false

    override fun pickFile(mimeTypes: List<String>, onPicked: (String?) -> Unit) {
        val dialog = FileDialog(parent, "Open", FileDialog.LOAD)
        val extensions = extensionsFor(mimeTypes)
        if (extensions.isNotEmpty()) {
            // Windows filters by a pattern in the file name box; the other systems by the filter.
            dialog.file = extensions.joinToString(";") { "*.$it" }
            dialog.setFilenameFilter { _, name -> extensions.any { name.endsWith(".$it", ignoreCase = true) } }
        }
        dialog.isVisible = true
        val name: String? = dialog.file
        val directory: String? = dialog.directory
        onPicked(if (name == null || directory == null) null else File(directory, name).absolutePath)
    }

    override fun pickFolder(onPicked: (String?) -> Unit) {
        val chooser = JFileChooser()
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        val picked = chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION
        onPicked(if (picked) chooser.selectedFile?.absolutePath else null)
    }

    companion object {
        private val EXTENSIONS = mapOf(
            "font/ttf" to listOf("ttf"),
            "font/otf" to listOf("otf"),
            "font/*" to listOf("ttf", "otf", "ttc"),
            "application/x-font-ttf" to listOf("ttf"),
            "application/x-font-otf" to listOf("otf"),
            "application/json" to listOf("json"),
            "application/ttml+xml" to listOf("ttml", "xml"),
            "application/xml" to listOf("xml", "ttml"),
            "text/xml" to listOf("xml", "ttml"),
            "text/plain" to listOf("txt", "lrc"),
        )

        /** The file extensions a picker offers for [mimeTypes]; empty means any file. */
        fun extensionsFor(mimeTypes: List<String>): List<String> {
            if (mimeTypes.isEmpty() || "*/*" in mimeTypes) return emptyList()
            val known = mimeTypes.map { EXTENSIONS[it.lowercase()] }
            // A type with no known extension: offer every file rather than hide the one wanted.
            if (null in known) return emptyList()
            return known.filterNotNull().flatten().distinct()
        }
    }
}
