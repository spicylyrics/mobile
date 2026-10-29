package com.tx24.spicyplayer.ui.lyricsmanager

import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tx24.spicyplayer.lyrics.LrcConverter
import com.tx24.spicyplayer.playback.ExternalPlaybackViewModel
import com.tx24.spicyplayer.playback.PlayerUiState
import com.tx24.spicyplayer.ui.components.BlockSkeleton
import com.tx24.spicyplayer.ui.components.SpicyModal
import com.tx24.spicyplayer.ui.components.TrackListSkeleton
import com.tx24.spicyplayer.ui.settings.AppSettings
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * The Lyrics Manager on the phone: its own pop-up, and the view model, pickers and covers behind
 * the shared screens ([LyricsManagerContent]).
 */

@Composable
fun LyricsManagerModal(
    visible: Boolean,
    state: PlayerUiState,
    viewModel: ExternalPlaybackViewModel,
    settings: AppSettings,
    backdrop: HazeState?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var uploading by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            uploading = false
            viewModel.refreshLocalLyrics()
        }
    }
    SpicyModal(
        visible = visible,
        onDismissRequest = onDismissRequest,
        backdrop = backdrop,
        modifier = modifier,
        title = if (uploading) "Upload TTML" else "Local Lyrics DB",
        onBack = if (uploading) ({ uploading = false }) else null,
        // The list decodes a cover per saved song: it comes in once the pop-up is open.
        skeleton = {
            BlockSkeleton()
            BlockSkeleton()
            BlockSkeleton(height = 56.dp)
            TrackListSkeleton(rows = 4)
        },
    ) {
        LyricsManagerContent(state, viewModel, settings, uploading, { uploading = it }, onDismissRequest)
    }
}

/** The Lyrics Manager's screens for the view model's state, for its own pop-up or quick settings. */
@Composable
fun LyricsManagerContent(
    state: PlayerUiState,
    viewModel: ExternalPlaybackViewModel,
    settings: AppSettings,
    uploading: Boolean,
    onUploadingChange: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val managerState = LyricsManagerState(
        entries = state.localLyrics.map { LibraryEntry(it.key, it.title, it.artist, it.album, it.savedAt) },
        currentId = state.localLyricsKey,
        songTitle = state.title,
        folder = state.lyricsFolder?.let { LinkedLyricsFolder(it.name, it.files) },
        folderScanning = state.lyricsFolderScanning,
    )
    LyricsManagerContent(managerState, rememberPhoneActions(viewModel), settings, uploading, onUploadingChange, onClose)
}

/** The phone's side of the Lyrics Manager: the system's file pickers and the view model. */
@Composable
private fun rememberPhoneActions(viewModel: ExternalPlaybackViewModel): LyricsManagerActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The song being exported, while the system's save screen is up.
    var exporting by remember { mutableStateOf<LibraryEntry?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(TTML_MIME)) { uri ->
        val entry = exporting ?: return@rememberLauncherForActivityResult
        exporting = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ttml = viewModel.localLyricsText(entry.id)
            val written = ttml != null && withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(ttml.toByteArray()) } != null }
                    .getOrDefault(false)
            }
            if (!written) viewModel.showMessage("Could not retrieve TTML for this track.")
        }
    }
    // The upload waiting on the picker: whether it saves, and what takes the picked file.
    var pending by remember { mutableStateOf<Pair<Boolean, (suspend () -> Boolean) -> Unit>?>(null) }
    val upload = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val (save, onPicked) = pending ?: return@rememberLauncherForActivityResult
        pending = null
        if (uri != null) onPicked { applyUpload(context, viewModel, uri, save) }
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) viewModel.linkLyricsFolder(tree)
    }
    return remember(viewModel, context, export, upload, pickFolder) {
        object : LyricsManagerActions {
            override suspend fun cover(id: String): ImageBitmap? = withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(viewModel.localLyricsCover(id).path)?.asImageBitmap() }.getOrNull()
            }

            override fun export(entry: LibraryEntry) {
                exporting = entry
                export.launch("${asciiFilename(entry.title)}.ttml")
            }

            override fun remove(id: String) = viewModel.removeLocalLyrics(id)
            override fun resetOnce() = viewModel.resetTtml()

            override fun pickUpload(save: Boolean, onPicked: (apply: suspend () -> Boolean) -> Unit) {
                pending = save to onPicked
                upload.launch(PICKABLE_TYPES)
            }

            override val folders: Boolean get() = true
            override fun linkFolder() = pickFolder.launch(null)
            override fun rescanFolder() = viewModel.rescanLyricsFolder(manual = true)
            override fun unlinkFolder() = viewModel.unlinkLyricsFolder()
        }
    }
}

/** Reads the picked file, converting an LRC, and applies it: true once it's shown. */
private suspend fun applyUpload(context: android.content.Context, viewModel: ExternalPlaybackViewModel, uri: Uri, save: Boolean): Boolean {
    val name = withContext(Dispatchers.IO) { displayName(context, uri) }
    val extension = name?.substringAfterLast('.', "")?.lowercase()
    val ttml = if (extension != null && extension != "ttml" && extension != "lrc") {
        viewModel.showMessage("Only .ttml and .lrc files are supported.")
        null
    } else withContext(Dispatchers.IO) {
        val text = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString().removePrefix("\uFEFF") } }.getOrNull()
        when {
            text == null -> null.also { viewModel.showMessage("Error reading the file.") }
            extension == "lrc" || LrcConverter.isLrc(text) ->
                LrcConverter.toTtml(text).also { if (it == null) viewModel.showMessage("That LRC file has no timed lines.") }
            else -> text
        }
    }
    return ttml != null && viewModel.importTtml(ttml, persistent = save)
}

private fun displayName(context: android.content.Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

/** The export's file name: letters, digits and a little punctuation, the rest `_`. */
private fun asciiFilename(name: String): String = name.replace(Regex("[^a-zA-Z0-9_\\- .]"), "_").take(100)

private const val TTML_MIME = "application/ttml+xml"

/**
 * What the file picker offers. It filters by type, not name: a .ttml is TTML to a phone that
 * knows the extension and a nameless blob of bytes to one that doesn't, so both are asked for
 * (with plain XML), which keeps music, pictures and documents out. A .lrc has no one type:
 * Android's own table calls it `application/lrc`, file managers and other tables use the rest.
 */
private val PICKABLE_TYPES = arrayOf(
    TTML_MIME, "application/xml", "text/xml", "text/plain", "application/octet-stream",
    "application/lrc", "application/x-lrc", "text/lrc", "text/x-lrc",
)
