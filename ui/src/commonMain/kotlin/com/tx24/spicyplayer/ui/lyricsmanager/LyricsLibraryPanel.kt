@file:OptIn(ExperimentalResourceApi::class)

package com.tx24.spicyplayer.ui.lyricsmanager

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tx24.spicyplayer.ui.components.ChromeTheme
import com.tx24.spicyplayer.ui.components.SpicyToastHost
import com.tx24.spicyplayer.ui.components.drawBrandMark
import com.tx24.spicyplayer.ui.components.drawBrandRamp
import com.tx24.spicyplayer.ui.components.rememberBrandGlyph
import com.tx24.spicyplayer.ui.settings.panel.Glyph
import com.tx24.spicyplayer.ui.settings.panel.PanelIcons
import com.tx24.spicyplayer.ui.settings.panel.RoundButton
import com.tx24.spicyplayer.ui.settings.panel.SearchBar
import com.tx24.spicyplayer.ui.settings.panel.titleStyle
import com.tx24.spicyplayer.ui.theme.LocalSpicyPalette
import com.tx24.spicyplayer.ui.theme.SpicyColors
import com.tx24.spicyplayer.ui.theme.SpicyRadii
import com.tx24.spicyplayer.ui.theme.SpicySpacing
import com.tx24.spicyplayer.ui.theme.SpicyType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * The lyrics library for big screens: the saved songs with search, the playing one first and
 * marked, each exportable and deletable (asking twice), plus resetting the playing song's lyrics
 * and uploading a TTML or LRC file for it, saved or just once ([saveUploads], kept by the caller).
 * Files open and save through [LocalLyricsFiles]. [onLyricsChanged] is told when the playing
 * song's lyrics may have changed, to look them up again.
 */
@Composable
fun LyricsLibraryPanel(
    library: LocalLyricsLibrary,
    current: LibraryTrack?,
    theme: ChromeTheme,
    saveUploads: Boolean,
    onSaveUploadsChange: (Boolean) -> Unit,
    onLyricsChanged: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = theme.palette
    CompositionLocalProvider(LocalSpicyPalette provides palette) {
        val messages = remember { MutableSharedFlow<String>(extraBufferCapacity = 4) }
        var uploading by remember { mutableStateOf(false) }
        val brand = palette.brand
        val glyph = rememberBrandGlyph()
        Box(
            modifier
                .fillMaxSize()
                .drawBehind {
                    if (brand != null) {
                        drawBrandRamp(brand)
                        val width = size.minDimension * 0.9f
                        drawBrandMark(glyph, brand.tint, alpha = 0.55f, width = width, right = -width * 0.3f, top = -width * 0.25f, degrees = -14f)
                    } else {
                        drawRect(palette.BgElevated.copy(alpha = 1f))
                    }
                },
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val narrow = maxWidth < 600.dp
                val gutter = if (narrow) SpicySpacing.S4 else 32.dp
                Column(Modifier.fillMaxSize()) {
                    Header(if (uploading) "Upload Lyrics" else "Lyrics Manager", gutter, onBack = if (uploading) ({ uploading = false }) else null, onClose)
                    Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = gutter)) {
                        if (uploading) {
                            UploadPage(library, current, saveUploads, onSaveUploadsChange, messages) { _ ->
                                uploading = false
                                onLyricsChanged()
                            }
                        } else {
                            LibraryPage(library, current, narrow, messages, onLyricsChanged, onUpload = { uploading = true })
                        }
                    }
                }
            }
            SpicyToastHost(messages, Modifier.padding(top = SpicySpacing.S4))
        }
    }
}

@Composable
private fun Header(title: String, gutter: androidx.compose.ui.unit.Dp, onBack: (() -> Unit)?, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = if (onBack != null) 12.dp else gutter, end = 18.dp, top = 22.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S3),
    ) {
        onBack?.let { RoundButton(PanelIcons.Back, 12.dp, it) }
        BasicText(title, style = titleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        RoundButton(PanelIcons.Close, 12.dp, onClose)
    }
}

@Composable
private fun LibraryPage(
    library: LocalLyricsLibrary,
    current: LibraryTrack?,
    narrow: Boolean,
    messages: MutableSharedFlow<String>,
    onLyricsChanged: () -> Unit,
    onUpload: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val files = LocalLyricsFiles.current
    var query by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<LibraryEntry>?>(null) }
    var version by remember { mutableIntStateOf(0) }
    val covers = remember { mutableStateMapOf<String, ImageBitmap>() }
    val focus = remember { FocusRequester() }
    LaunchedEffect(version) {
        val loaded = runCatching { library.entries() }.getOrDefault(emptyList())
        entries = loaded
        loaded.forEach { entry ->
            if (entry.id !in covers) {
                runCatching { library.cover(entry.id)?.decodeToImageBitmap() }.getOrNull()?.let { covers[entry.id] = it }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val currentId = current?.let(library::idOf)

    Column(Modifier.fillMaxSize()) {
        val reset: () -> Unit = {
            if (current == null) messages.tryEmit("No song is playing.")
            else {
                library.clearOnce()
                onLyricsChanged()
                messages.tryEmit("TTML has been reset.")
            }
        }
        if (narrow) {
            SearchBar(query, { query = it }, focus, placeholder = "Search saved lyrics…")
            Spacer(Modifier.height(SpicySpacing.S2))
            Row(horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
                ToolbarButton(PanelIcons.Reset, "Reset TTML", ButtonStyle.Danger, reset, Modifier.weight(1f))
                ToolbarButton(PanelIcons.Upload, "Upload TTML", ButtonStyle.Primary, onUpload, Modifier.weight(1f))
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
                Box(Modifier.weight(1f)) { SearchBar(query, { query = it }, focus, placeholder = "Search saved lyrics…") }
                ToolbarButton(PanelIcons.Reset, "Reset TTML", ButtonStyle.Danger, reset)
                ToolbarButton(PanelIcons.Upload, "Upload TTML", ButtonStyle.Primary, onUpload)
            }
        }
        Spacer(Modifier.height(SpicySpacing.S4))
        val list = entries
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when {
                list == null -> repeat(5) { SkeletonRow() }
                else -> {
                    val shown = pinCurrent(filterLibrary(list, query), currentId)
                    if (shown.isEmpty()) {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 72.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(SpicySpacing.S4),
                        ) {
                            Glyph(PanelIcons.Notice, SpicyColors.TextSecondary, 40.dp)
                            BasicText(
                                if (query.isBlank()) "No lyrics saved yet" else "No matching entries",
                                style = SpicyType.Body.copy(color = SpicyColors.TextSecondary, textAlign = TextAlign.Center),
                            )
                        }
                    } else shown.forEach { entry ->
                        androidx.compose.runtime.key(entry.id) {
                            TrackRow(
                                entry = entry,
                                cover = covers[entry.id],
                                playing = entry.id == currentId,
                                onExport = {
                                    scope.launch {
                                        val ttml = library.raw(entry.id)
                                        if (ttml == null || files == null) {
                                            messages.tryEmit("Could not retrieve TTML for this track.")
                                            return@launch
                                        }
                                        files.save("${sanitizeFilename(entry.title.ifBlank { entry.id })}.ttml", ttml) { written ->
                                            if (written == false) messages.tryEmit("Could not save that file.")
                                        }
                                    }
                                },
                                onDelete = {
                                    scope.launch {
                                        val removed = runCatching { library.remove(entry.id) }.isSuccess
                                        messages.tryEmit(if (removed) "Removed from Local DB." else "Failed to remove entry.")
                                        if (removed) {
                                            covers.remove(entry.id)
                                            version++
                                            // Only the playing song's lyrics change with its entry.
                                            if (entry.id == currentId) onLyricsChanged()
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(entry: LibraryEntry, cover: ImageBitmap?, playing: Boolean, onExport: () -> Unit, onDelete: () -> Unit) {
    // Delete asks twice: the first press turns it into "Confirm" for 3 s.
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(confirmDelete) {
        if (confirmDelete) {
            delay(3_000)
            confirmDelete = false
        }
    }
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val shape = RoundedCornerShape(SpicyRadii.Md)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                when {
                    playing && hovered -> SpicyColors.TintBgPressed
                    playing || hovered -> SpicyColors.TintBg
                    else -> Color.Transparent
                },
            )
            .then(if (playing) Modifier.border(1.dp, SpicyColors.HairlineStrong, shape) else Modifier)
            .hoverable(hover)
            .padding(horizontal = SpicySpacing.S3, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S3),
    ) {
        Cover(cover)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
                if (playing) PlayingDot()
                BasicText(
                    entry.title.ifBlank { entry.id },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = SpicyType.Body.copy(fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Medium),
                )
            }
            if (entry.artist.isNotBlank()) {
                BasicText(entry.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = SpicyType.Caption.copy(color = SpicyColors.TextSecondary))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ToolbarButton(PanelIcons.Download, null, ButtonStyle.Default, onExport, compact = true)
            ToolbarButton(
                PanelIcons.Trash,
                if (confirmDelete) "Confirm" else null,
                if (confirmDelete) ButtonStyle.Danger else ButtonStyle.Default,
                onClick = {
                    if (confirmDelete) {
                        confirmDelete = false
                        onDelete()
                    } else confirmDelete = true
                },
                compact = true,
            )
        }
    }
}

/** The saved cover, or a tinted square. */
@Composable
private fun Cover(cover: ImageBitmap?) {
    val shape = RoundedCornerShape(6.dp)
    Box(Modifier.size(44.dp).clip(shape).background(SpicyColors.TintBgPressed)) {
        if (cover != null) Image(cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

/** A small dot that breathes: this one is playing. */
@Composable
private fun PlayingDot() {
    val pulse = rememberInfiniteTransition()
    val t by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1_600), RepeatMode.Reverse))
    Box(
        Modifier
            .size(6.dp)
            .graphicsLayer { alpha = 1f - 0.45f * t; scaleX = 1f - 0.08f * t; scaleY = scaleX }
            .background(SpicyColors.TextPrimary, CircleShape),
    )
}

@Composable
private fun SkeletonRow() {
    val pulse = rememberInfiniteTransition()
    val t by pulse.animateFloat(0.5f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse))
    Row(
        Modifier.fillMaxWidth().padding(horizontal = SpicySpacing.S3, vertical = 10.dp).alpha(t),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S3),
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(SpicyColors.TintBgPressed))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.fillMaxWidth(0.6f).height(12.dp).clip(RoundedCornerShape(3.dp)).background(SpicyColors.TintBgPressed))
            Box(Modifier.fillMaxWidth(0.4f).height(10.dp).clip(RoundedCornerShape(3.dp)).background(SpicyColors.TintBgPressed))
        }
    }
}

@Composable
private fun UploadPage(
    library: LocalLyricsLibrary,
    current: LibraryTrack?,
    save: Boolean,
    onSaveChange: (Boolean) -> Unit,
    messages: MutableSharedFlow<String>,
    onDone: (saved: Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val files = LocalLyricsFiles.current
    var busy by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(SpicySpacing.S3)) {
        // The mode: saved for good, or shown once.
        Row(
            Modifier
                .clip(RoundedCornerShape(SpicyRadii.Sm))
                .background(SpicyColors.TintBg)
                .border(1.dp, SpicyColors.Hairline, RoundedCornerShape(SpicyRadii.Sm))
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ModeButton(PanelIcons.Database, "Save", save, enabled = !busy) { onSaveChange(true) }
            ModeButton(PanelIcons.Clock, "Just once", !save, enabled = !busy) { onSaveChange(false) }
        }
        val hover = remember { MutableInteractionSource() }
        val hovered by hover.collectIsHoveredAsState()
        val shape = RoundedCornerShape(SpicyRadii.Md)
        val songName = current?.title?.ifBlank { null } ?: "Unknown Song"
        Column(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(shape)
                .background(if (hovered && !busy) SpicyColors.TintBgPressed else SpicyColors.TintBg)
                .border(1.dp, if (hovered) SpicyColors.HairlineStrong else SpicyColors.Hairline, shape)
                .hoverable(hover)
                .clickable(interactionSource = hover, indication = null, enabled = !busy) {
                    when {
                        current == null -> messages.tryEmit("No track is currently playing.")
                        files == null -> messages.tryEmit("Files can't be opened here.")
                        else -> files.open { name, text ->
                            if (!acceptsFile(name)) {
                                messages.tryEmit("Only .ttml and .lrc files are supported.")
                                return@open
                            }
                            busy = true
                            scope.launch {
                                val result = runCatching { library.add(current, text, name, save) }
                                    .getOrElse { AddResult.Refused("Upload failed.") }
                                busy = false
                                when (result) {
                                    is AddResult.Refused -> messages.tryEmit(result.message)
                                    is AddResult.Added -> {
                                        messages.tryEmit(if (result.saved) "TTML saved to Local DB!" else "Lyrics parsed and applied!")
                                        onDone(result.saved)
                                    }
                                }
                            }
                        }
                    }
                }
                .pointerHoverIcon(if (busy) PointerIcon.Default else PointerIcon.Hand)
                .padding(SpicySpacing.S6),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpicySpacing.S2, Alignment.CenterVertically),
        ) {
            if (busy) Spinner() else Glyph(PanelIcons.Upload, SpicyColors.TextSecondary, 26.dp)
            BasicText(
                if (busy) "Uploading…" else "Click to choose a .ttml or .lrc file",
                style = SpicyType.Body.copy(fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
            )
            BasicText(
                (if (save) "Saves to Local DB for " else "Applies once to ") + songName,
                style = SpicyType.Caption.copy(color = SpicyColors.TextSecondary, textAlign = TextAlign.Center),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Spinner() {
    val turn = rememberInfiniteTransition()
    val degrees by turn.animateFloat(0f, 360f, infiniteRepeatable(tween(800, easing = androidx.compose.animation.core.LinearEasing)))
    Box(Modifier.size(26.dp)) {
        Glyph(PanelIcons.Clock, SpicyColors.TextSecondary.copy(alpha = 0.25f), 26.dp)
        Box(Modifier.graphicsLayer { rotationZ = degrees }) { Glyph(PanelIcons.Spinner, SpicyColors.TextSecondary, 26.dp) }
    }
}

@Composable
private fun ModeButton(icon: ImageVector, label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val ink = if (selected || hovered) SpicyColors.TextPrimary else SpicyColors.TextSecondary
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) SpicyColors.TintBgPressed else Color.Transparent)
            .hoverable(hover, enabled)
            .clickable(interactionSource = hover, indication = null, enabled = enabled, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Glyph(icon, ink, 14.dp)
        BasicText(label, style = SpicyType.Caption.copy(color = ink, fontWeight = FontWeight.SemiBold))
    }
}

private enum class ButtonStyle { Default, Primary, Danger }

/** A tinted button, an icon and maybe a label: neutral, the filled accent, or the danger red. */
@Composable
private fun ToolbarButton(
    icon: ImageVector,
    label: String?,
    style: ButtonStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val danger = SpicyColors.StatusDanger
    val (fill, ink, ring) = when (style) {
        ButtonStyle.Default -> Triple(
            if (hovered) SpicyColors.TintBgPressed else SpicyColors.TintBg,
            if (hovered) SpicyColors.TextPrimary else SpicyColors.TextSecondary,
            if (hovered) SpicyColors.HairlineStrong else SpicyColors.Hairline,
        )
        ButtonStyle.Primary -> Triple(if (hovered) SpicyColors.AccentPressed else SpicyColors.Accent, SpicyColors.TextOnFill, Color.Transparent)
        ButtonStyle.Danger -> Triple(danger.copy(alpha = if (hovered) 0.18f else 0.1f), danger, danger.copy(alpha = 0.3f))
    }
    val shape = RoundedCornerShape(SpicyRadii.Sm)
    Row(
        modifier
            .height(if (compact) 32.dp else 36.dp)
            .clip(shape)
            .background(fill)
            .border(1.dp, ring, shape)
            .hoverable(hover)
            .clickable(interactionSource = hover, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = if (compact && label == null) 9.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2, Alignment.CenterHorizontally),
    ) {
        Glyph(icon, ink, 14.dp)
        label?.let { BasicText(it, style = SpicyType.Caption.copy(color = ink, fontWeight = FontWeight.SemiBold), maxLines = 1) }
    }
}
