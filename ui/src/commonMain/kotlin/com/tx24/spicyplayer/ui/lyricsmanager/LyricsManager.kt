package com.tx24.spicyplayer.ui.lyricsmanager

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.tx24.spicyplayer.haptics.withHaptic
import com.tx24.spicyplayer.ui.components.LocalUiAnimations
import com.tx24.spicyplayer.ui.components.SpicyTextField
import com.tx24.spicyplayer.ui.components.focusRing
import com.tx24.spicyplayer.ui.components.screenSlide
import com.tx24.spicyplayer.ui.settings.CommonAppSettings
import com.tx24.spicyplayer.ui.theme.SpicyColors
import com.tx24.spicyplayer.ui.theme.SpicyMotion
import com.tx24.spicyplayer.ui.theme.SpicyRadii
import com.tx24.spicyplayer.ui.theme.SpicySpacing
import com.tx24.spicyplayer.ui.theme.SpicyType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * The Lyrics Manager: the songs you've given your own TTML, and the upload that adds one.
 * Opens on the list ("Local Lyrics DB"); Upload moves the same pop-up to the upload screen, and
 * back (or Back) returns to the list.
 */

/** A linked lyrics folder, as its card shows it. */
data class LinkedLyricsFolder(val name: String, val files: Int)

/** What the Lyrics Manager shows. */
data class LyricsManagerState(
    /** The saved songs, newest first. */
    val entries: List<LibraryEntry>,
    /** The playing song's id, to mark and lift it in the list; null when nothing's playing. */
    val currentId: String?,
    /** The playing song's title, which an upload is for. */
    val songTitle: String,
    /** The linked lyrics folder, or null when none is. */
    val folder: LinkedLyricsFolder? = null,
    val folderScanning: Boolean = false,
)

/**
 * What the Lyrics Manager does on each platform: its pickers, its storage, the lyrics it reloads.
 * Each says why itself when something fails.
 */
interface LyricsManagerActions {
    /** The cover saved with [id], or null. */
    suspend fun cover(id: String): ImageBitmap?

    /** Asks where to save [entry]'s TTML and writes it there. */
    fun export(entry: LibraryEntry)

    fun remove(id: String)

    /** Takes down lyrics applied just once, back to the song's usual ones. */
    fun resetOnce()

    /**
     * Asks for a .ttml or .lrc file for the playing song. Once one is picked, [onPicked] gets the
     * work that applies it, saved when [save]: true once it's shown.
     */
    fun pickUpload(save: Boolean, onPicked: (apply: suspend () -> Boolean) -> Unit)

    /** There's a lyrics folder to link: the list shows its card. */
    val folders: Boolean get() = false
    fun linkFolder() {}
    fun rescanFolder() {}
    fun unlinkFolder() {}
}

/**
 * The Lyrics Manager's screens, the list and the upload, for its own pop-up or quick settings.
 * [uploading] picks the screen; the upload slides in over the list and back out.
 */
@Composable
fun LyricsManagerContent(
    state: LyricsManagerState,
    actions: LyricsManagerActions,
    settings: CommonAppSettings,
    uploading: Boolean,
    onUploadingChange: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val animate = LocalUiAnimations.current
    AnimatedContent(
        targetState = uploading,
        transitionSpec = { screenSlide(forward = targetState, animate = animate) },
        label = "lyricsManagerScreen",
    ) { upload ->
        Column(verticalArrangement = Arrangement.spacedBy(SpicySpacing.S3)) {
            if (upload) {
                UploadScreen(
                    songName = state.songTitle,
                    settings = settings,
                    actions = actions,
                    // Once saved, back to the list with it; a just-once upload is done.
                    onDone = { saved -> if (saved) onUploadingChange(false) else onClose() },
                )
            } else {
                LibraryScreen(state, actions, onUpload = { onUploadingChange(true) })
            }
        }
    }
}

@Composable
private fun LibraryScreen(state: LyricsManagerState, actions: LyricsManagerActions, onUpload: () -> Unit) {
    var query by remember { mutableStateOf("") }

    val needle = query.trim().lowercase()
    val shown = state.entries
        .filter { needle.isEmpty() || needle in it.title.lowercase() || needle in it.artist.lowercase() }
        // The playing song goes first.
        .sortedByDescending { it.id == state.currentId }

    SpicyTextField(
        query,
        { query = it },
        placeholder = "Search saved lyrics…",
        modifier = Modifier.fillMaxWidth(),
        leading = { Icon(Icons.Rounded.Search, null, tint = SpicyColors.TextTertiary, modifier = Modifier.size(18.dp)) },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
        ToolbarButton(Icons.Rounded.RestartAlt, "Reset TTML", ToolbarStyle.Danger, actions::resetOnce, Modifier.weight(1f))
        ToolbarButton(Icons.Rounded.FileUpload, "Upload TTML", ToolbarStyle.Primary, onUpload, Modifier.weight(1f))
    }
    if (actions.folders) FolderCard(state, actions)
    if (shown.isEmpty()) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpicySpacing.S4),
        ) {
            Icon(Icons.Rounded.ErrorOutline, null, tint = SpicyColors.TextSecondary, modifier = Modifier.size(40.dp))
            Text(
                if (needle.isEmpty()) "No lyrics saved yet" else "No matching entries",
                style = SpicyType.Body.copy(color = SpicyColors.TextSecondary),
            )
        }
    } else {
        Column(
            Modifier.then(if (LocalUiAnimations.current) Modifier.animateContentSize(tween(SpicyMotion.MODAL_MS)) else Modifier),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            shown.forEach { entry ->
                TrackRow(
                    entry = entry,
                    actions = actions,
                    playing = entry.id == state.currentId,
                    onExport = { actions.export(entry) },
                    onDelete = { actions.remove(entry.id) },
                )
            }
        }
    }
}

@Composable
private fun TrackRow(
    entry: LibraryEntry,
    actions: LyricsManagerActions,
    playing: Boolean,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    // Delete asks twice: the first tap turns it into "Confirm" for 3 s.
    var confirmDelete by remember(entry.id) { mutableStateOf(false) }
    LaunchedEffect(confirmDelete) {
        if (confirmDelete) {
            delay(3_000)
            confirmDelete = false
        }
    }
    val shape = RoundedCornerShape(SpicyRadii.Md)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (playing) Modifier.background(SpicyColors.TintBg).border(1.dp, SpicyColors.HairlineStrong, shape)
                else Modifier,
            )
            .padding(horizontal = SpicySpacing.S3, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S3),
    ) {
        Cover(entry, actions)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (playing) {
                    Box(Modifier.padding(end = 6.dp).size(6.dp).background(SpicyColors.StatusSuccess, CircleShape))
                }
                Text(
                    entry.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = SpicyType.Body.copy(
                        color = if (playing) SpicyColors.TextPrimary else SpicyColors.TextPrimary.copy(alpha = 0.85f),
                        fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                )
            }
            if (entry.artist.isNotBlank()) {
                Text(entry.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = SpicyType.Caption.copy(color = SpicyColors.TextSecondary))
            }
        }
        RowAction(Icons.Rounded.FileDownload, "Download TTML", onClick = onExport)
        RowAction(
            Icons.Rounded.Delete,
            if (confirmDelete) "Confirm delete" else "Delete",
            label = if (confirmDelete) "Confirm" else null,
            danger = confirmDelete,
            onClick = {
                if (confirmDelete) {
                    confirmDelete = false
                    onDelete()
                } else confirmDelete = true
            },
        )
    }
}

/**
 * The lyrics folder: .ttml and .lrc files read where they are, picked up as they're added or
 * changed. Its songs aren't listed; a folder can hold thousands.
 */
@Composable
private fun FolderCard(state: LyricsManagerState, actions: LyricsManagerActions) {
    // Unlinking asks twice, like deleting.
    var confirmUnlink by remember { mutableStateOf(false) }
    LaunchedEffect(confirmUnlink) {
        if (confirmUnlink) {
            delay(3_000)
            confirmUnlink = false
        }
    }
    val folder = state.folder
    val shape = RoundedCornerShape(SpicyRadii.Md)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SpicyColors.TintBg)
            .border(1.dp, SpicyColors.Hairline, shape)
            .padding(horizontal = SpicySpacing.S3, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S3),
    ) {
        Icon(Icons.Rounded.Folder, null, tint = SpicyColors.TextSecondary, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(folder?.name ?: "Lyrics folder", maxLines = 1, overflow = TextOverflow.Ellipsis, style = SpicyType.Body.copy(fontWeight = FontWeight.Medium))
            Text(
                when {
                    folder == null -> "Read .ttml and .lrc files from a folder."
                    state.folderScanning -> "Reading…"
                    else -> "${folder.files} ${if (folder.files == 1) "file" else "files"}"
                },
                maxLines = 2,
                style = SpicyType.Caption.copy(color = SpicyColors.TextSecondary),
            )
        }
        if (folder == null) {
            RowAction(Icons.Rounded.Folder, "Choose a lyrics folder", label = "Choose", onClick = actions::linkFolder)
        } else {
            if (state.folderScanning) {
                CircularProgressIndicator(Modifier.size(18.dp), color = SpicyColors.TextSecondary, strokeWidth = 2.dp)
            } else {
                RowAction(Icons.Rounded.Refresh, "Rescan lyrics folder", onClick = actions::rescanFolder)
            }
            RowAction(
                Icons.Rounded.LinkOff,
                if (confirmUnlink) "Confirm unlink" else "Unlink lyrics folder",
                label = if (confirmUnlink) "Confirm" else null,
                danger = confirmUnlink,
                onClick = {
                    if (confirmUnlink) {
                        confirmUnlink = false
                        actions.unlinkFolder()
                    } else confirmUnlink = true
                },
            )
        }
    }
}

/** The saved cover, or a note on a tinted square when the song had none. */
@Composable
private fun Cover(entry: LibraryEntry, actions: LyricsManagerActions) {
    val bitmap by produceState<ImageBitmap?>(null, entry.id, entry.savedAt) {
        value = runCatching { actions.cover(entry.id) }.getOrNull()
    }
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier.size(44.dp).clip(shape).background(SpicyColors.TintBgPressed),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(image, null, Modifier.size(44.dp), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Rounded.MusicNote, null, tint = SpicyColors.TextTertiary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun UploadScreen(
    songName: String,
    settings: CommonAppSettings,
    actions: LyricsManagerActions,
    onDone: (saved: Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val save = settings.ttmlUploadSaves
    // Picking the file is the upload.
    val pick = {
        actions.pickUpload(save) { apply ->
            if (!busy) {
                busy = true
                scope.launch {
                    val applied = apply()
                    busy = false
                    if (applied) onDone(save)
                }
            }
        }
    }

    // The two ways to apply it, as one segmented control; it reopens on the last one used.
    // Back to the list is the header's arrow.
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SpicyRadii.Sm))
            .background(SpicyColors.TintBg)
            .border(1.dp, SpicyColors.Hairline, RoundedCornerShape(SpicyRadii.Sm))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        ModeButton(Icons.Rounded.Storage, "Save", selected = save, enabled = !busy, Modifier.weight(1f)) { settings.ttmlUploadSaves = true }
        ModeButton(Icons.Rounded.Schedule, "Just once", selected = !save, enabled = !busy, Modifier.weight(1f)) { settings.ttmlUploadSaves = false }
    }
    Text(
        if (save) "Store in the local DB — survives restarts" else "Apply to the current song only, until refresh",
        style = SpicyType.Footnote.copy(color = SpicyColors.TextTertiary),
    )
    DropZone(
        busy = busy,
        subtitle = (if (save) "Saves to Local DB for " else "Applies once to ") + songName,
        onClick = pick,
    )
}

/** The dashed box a tap on which opens the file picker. */
@Composable
private fun DropZone(busy: Boolean, subtitle: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val radius = SpicyRadii.Lg
    val edge = if (pressed) SpicyColors.TextTertiary else SpicyColors.HairlineStrong
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp)
            .clip(RoundedCornerShape(radius))
            .background(if (pressed) SpicyColors.TintBgPressed else SpicyColors.TintBg)
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    color = edge,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(radius.toPx()),
                    style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
                )
            }
            .focusRing(RoundedCornerShape(SpicyRadii.Md))
            .clickable(interaction, indication = null, enabled = !busy, role = Role.Button, onClick = withHaptic(HapticFeedbackType.ContextClick, onClick))
            .padding(SpicySpacing.S6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpicySpacing.S2, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(26.dp), color = SpicyColors.TextSecondary, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Rounded.FileUpload, null, tint = SpicyColors.TextSecondary, modifier = Modifier.size(30.dp))
            }
        }
        Spacer(Modifier.width(0.dp))
        Text(
            if (busy) "Uploading…" else "Tap to choose a .ttml or .lrc file",
            style = SpicyType.Body.copy(fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
        )
        Text(
            subtitle,
            style = SpicyType.Caption.copy(color = SpicyColors.TextSecondary, textAlign = TextAlign.Center),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private enum class ToolbarStyle { Primary, Danger }

@Composable
private fun ToolbarButton(icon: ImageVector, label: String, style: ToolbarStyle, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val touching by interaction.collectIsPressedAsState()
    // A quick tap is over in a few frames: the pressed look holds a moment past it, so it shows.
    var pressed by remember { mutableStateOf(false) }
    LaunchedEffect(touching) {
        if (touching) pressed = true else {
            delay(PRESS_HOLD_MS)
            pressed = false
        }
    }
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, tween(if (pressed) 120 else 260), label = "toolbarScale")
    val press by animateFloatAsState(if (pressed) 1f else 0f, tween(if (pressed) 120 else 260), label = "toolbarPress")
    val shape = RoundedCornerShape(SpicyRadii.Sm)
    val (fill, content) = when (style) {
        ToolbarStyle.Primary -> lerp(SpicyColors.Accent, Color.White.copy(alpha = 0.7f), press) to SpicyColors.TextOnFill
        ToolbarStyle.Danger -> SpicyColors.StatusDanger.copy(alpha = 0.16f + 0.22f * press) to SpicyColors.StatusDanger.copy(alpha = 1f)
    }
    Row(
        modifier
            .heightIn(min = 40.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(fill)
            .focusRing(RoundedCornerShape(SpicyRadii.Md))
            .clickable(interaction, indication = null, role = Role.Button, onClick = withHaptic(HapticFeedbackType.ContextClick, onClick))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icon, null, tint = content, modifier = Modifier.size(16.dp))
        Text(label, style = SpicyType.Caption.copy(color = content, fontWeight = FontWeight.SemiBold, letterSpacing = 0.01.em))
    }
}

@Composable
private fun ModeButton(icon: ImageVector, label: String, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(SpicyRadii.Sm - 3.dp)
    val color = if (selected) SpicyColors.TextPrimary else SpicyColors.TextSecondary
    Row(
        modifier
            .heightIn(min = 34.dp)
            .clip(shape)
            .background(if (selected) SpicyColors.TintBgPressed else Color.Transparent)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = withHaptic(HapticFeedbackType.SegmentTick, onClick))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
        Text(label, style = SpicyType.Caption.copy(color = color, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium))
    }
}

/** A row's small icon button; [label] shows beside the icon when set, e.g. "Confirm". */
@Composable
private fun RowAction(
    icon: ImageVector,
    description: String,
    label: String? = null,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(SpicyRadii.Sm)
    val tint = when {
        danger -> SpicyColors.StatusDanger.copy(alpha = 1f)
        pressed -> SpicyColors.TextPrimary
        else -> SpicyColors.TextSecondary
    }
    Row(
        Modifier
            .heightIn(min = 36.dp)
            .clip(shape)
            .background(
                when {
                    danger -> SpicyColors.StatusDanger.copy(alpha = 0.16f)
                    pressed -> SpicyColors.TintBgPressed
                    else -> SpicyColors.TintBg
                },
            )
            .focusRing(RoundedCornerShape(SpicyRadii.Md))
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClickLabel = description, onClick = withHaptic(HapticFeedbackType.ContextClick, onClick))
            .padding(horizontal = if (label != null) 10.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(18.dp))
        if (label != null) Text(label, style = SpicyType.Caption.copy(color = tint, fontWeight = FontWeight.SemiBold))
    }
}

/** How long a tapped toolbar button keeps its pressed look after the finger lifts. */
private const val PRESS_HOLD_MS = 300L
