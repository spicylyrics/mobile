package com.tx24.spicyplayer.ui.settings

import android.os.Build
import com.tx24.spicyplayer.ui.components.SpicyGlyphs
import com.tx24.spicyplayer.ui.components.drawBrandRamp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.hazeEffect
import com.tx24.spicyplayer.ui.components.focusRing
import com.tx24.spicyplayer.ui.theme.SpicyRadii
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.composed
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.zIndex
import com.tx24.spicyplayer.ui.components.LocalUiAnimations
import com.tx24.spicyplayer.ui.components.LocalSettingsQuery
import androidx.compose.runtime.saveable.rememberSaveable
import com.tx24.spicyplayer.ui.theme.SpicyMotion
import java.util.Collections
import kotlin.math.abs
import com.tx24.spicyplayer.ui.background.LEGACY_BLUR_DP
import com.tx24.spicyplayer.ui.components.ChromeTheme
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.automirrored.rounded.CallMerge

import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Sync
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import com.tx24.spicyplayer.backup.SettingsBackup
import com.tx24.spicyplayer.backup.SettingsBackupStore
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Tune
import com.tx24.spicyplayer.ui.nowplaying.HeaderSize
import com.tx24.spicyplayer.ui.nowplaying.StageArt
import com.tx24.spicyplayer.ui.nowplaying.LoopKind
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixOff
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.FormatAlignRight
import androidx.compose.material.icons.rounded.FormatIndentIncrease
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.VerticalAlignCenter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.tx24.spicyplayer.lyrics.spicy.canvas.PinnedFooterMode
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Flare
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Panorama
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.BlurLinear
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.MotionPhotosPaused
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Lyrics
import java.util.Locale
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.StayCurrentPortrait
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.KeyOff
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowUp
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import com.tx24.spicyplayer.PictureInPicture
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.spicy.SyllableMerge
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsType
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import com.tx24.spicyplayer.network.data.LyricsBlends
import com.tx24.spicyplayer.network.data.LyricsOrigin
import com.tx24.spicyplayer.network.data.LyricsOrigins
import com.tx24.spicyplayer.network.data.LyricsSourceSettings
import com.tx24.spicyplayer.network.data.SourceRanking
import com.tx24.spicyplayer.ui.components.SpicyMultiSelect
import com.tx24.spicyplayer.network.data.ProviderAttempt
import com.tx24.spicyplayer.network.data.ProviderAttemptOutcome
import com.tx24.spicyplayer.network.data.RemoteLyricsQuality
import com.tx24.spicyplayer.playback.ExternalPlaybackViewModel
import com.tx24.spicyplayer.playback.LOCAL_SOURCE
import com.tx24.spicyplayer.playback.UPLOADED_SOURCE
import com.tx24.spicyplayer.playback.PlayerUiState
import com.tx24.spicyplayer.ui.components.DISABLED_ALPHA
import com.tx24.spicyplayer.ui.components.DescriptionStyle
import com.tx24.spicyplayer.ui.components.RowLabel
import com.tx24.spicyplayer.ui.components.SpicySelect
import com.tx24.spicyplayer.ui.components.Searchable
import com.tx24.spicyplayer.ui.components.SettingRow
import com.tx24.spicyplayer.ui.components.SettingsSection
import com.tx24.spicyplayer.ui.components.SpicyBipolarSlider
import com.tx24.spicyplayer.ui.components.SpicyButton
import com.tx24.spicyplayer.ui.components.SpicyResettableValue
import com.tx24.spicyplayer.ui.components.SpicyIconButton
import com.tx24.spicyplayer.ui.components.SpicyTextField
import com.tx24.spicyplayer.ui.components.SpicyToggle
import com.tx24.spicyplayer.ui.components.ToggleRow
import com.tx24.spicyplayer.ui.components.outlinedCard
import com.tx24.spicyplayer.ui.theme.SpicyColors
import com.tx24.spicyplayer.ui.theme.SpicySpacing
import com.tx24.spicyplayer.ui.theme.SpicyType
import com.tx24.spicyplayer.update.UpdateViewModel
import com.tx24.spicyplayer.analytics.UsageCounter
import com.tx24.spicyplayer.network.data.SpicyLyricsKey
import com.tx24.spicyplayer.analytics.UsageStats

/*
 * What each group's page holds. Every page also shows up, row by row, in the search results, so a
 * page's first rows go without a section title (the page header says it) and later groups get one.
 */

@Composable
internal fun ThisSongContent(
    state: PlayerUiState,
    viewModel: ExternalPlaybackViewModel,
    host: SettingsHost,
    onOpenLyricsManager: () -> Unit,
    onOpenSpotifySearch: () -> Unit,
) {
    val context = LocalContext.current
    var spotifyInput by remember { mutableStateOf("") }
    LaunchedEffect(state.title, state.artist) { spotifyInput = "" }

    Searchable("This song", "Now playing", state.title, state.artist) {
        Column(
            Modifier.fillMaxWidth().outlinedCard(tinted = true).padding(SpicySpacing.S4),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(state.title, style = SpicyType.Headline)
            Text(state.artist, style = SpicyType.Caption.copy(color = SpicyColors.TextSecondary))
            Spacer(Modifier.height(SpicySpacing.S2))
            Text(lyricsSummary(state.lyrics), style = SpicyType.Caption)
            state.lookupStatus?.let { Text(it, style = DescriptionStyle) }
        }
        Spacer(Modifier.height(SpicySpacing.S2))
    }
    SettingRow(
        label = "Wrong lyrics?",
        description = "Paste the song's Spotify link and the lyrics will come from that recording.",
        icon = Icons.Rounded.Link,
        stacked = true,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
            SpicyTextField(spotifyInput, { spotifyInput = it }, placeholder = "Spotify link or track ID", modifier = Modifier.weight(1f))
            if (spotifyInput.isBlank()) {
                SpicyButton("Paste", onClick = {
                    val pasted = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                        ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                    spotifyInput = pasted
                    viewModel.overrideSpotifyId(pasted)
                })
            } else {
                SpicyButton("Use", onClick = { viewModel.overrideSpotifyId(spotifyInput) })
            }
        }
    }
    SettingRow(label = "Find on Spotify", description = "Pick the right recording from Spotify's results for this song.", icon = Icons.Rounded.Search) {
        SpicyButton("Search", onClick = onOpenSpotifySearch)
    }
    state.manualSpotifyId?.let { id ->
        SettingRow(label = "Using your Spotify link", description = id, icon = Icons.Rounded.Link) {
            SpicyButton("Auto-match", onClick = viewModel::clearSpotifyIdOverride)
        }
    }
    SettingRow(label = "Look again", description = "Ask the sources again instead of using the saved lyrics.", icon = Icons.Rounded.Refresh) {
        SpicyButton("Retry", onClick = { viewModel.loadLyrics(force = true) })
    }
    SongDelayRow(settingsState(state), host)
    SettingRow(
        label = "Lyrics Manager",
        description = "Use your own TTML for this song, once or saved, and manage the songs you've saved.",
        icon = Icons.Rounded.LibraryMusic,
    ) {
        SpicyButton("Open", onClick = onOpenLyricsManager)
    }
    state.status?.let { status ->
        Searchable("Status", status) {
            Text(status, style = DescriptionStyle, modifier = Modifier.padding(horizontal = SpicySpacing.S3, vertical = SpicySpacing.S2))
        }
    }
}


@Composable
internal fun SourcesContent(state: PlayerUiState, viewModel: ExternalPlaybackViewModel) {
    var clientKey by remember { mutableStateOf("") }
    val ranking = state.sourceRanking
    val names = state.sourceDescriptors.associate { it.id to it.displayName }
    Searchable("Sources", "Priority", "Order", "Providers", *ranking.origins.map { it.displayName }.toTypedArray()) {
        Text(
            "Each source is where lyrics come from; higher sources win when the timing is as good. Tap one to pick the services " +
                "that fetch it and the sync types it may give. Services that haven't said any app may use them say what they send " +
                "before they're switched on.",
            style = DescriptionStyle,
            modifier = Modifier.padding(start = 2.dp, end = 2.dp, bottom = SpicySpacing.S3),
        )
    }
    SourceList(ranking, names, viewModel)
    MoreOptions("Custom sources, background vocals in brackets, your own Spicy Lyrics key") {
        CustomSourcesSection(state, viewModel)
        SettingsSection("Reading lyrics") {
            ToggleRow(
                label = "Background vocals in brackets",
                description = "A \"(…)\" at the start or end of a synced line is shown as its background vocals.",
                checked = state.splitBackgroundVocals,
                onCheckedChange = viewModel::setSplitBackgroundVocals,
                icon = Icons.Rounded.RecordVoiceOver,
            )
        }
        SettingsSection("Spicy Lyrics key") {
            val context = LocalContext.current
            val ownKey = state.ownKeyHint
            SettingRow(
                label = "Get your own key",
                description = if (ownKey == null) {
                    "The built-in key works for everyone. If you want your own, sign in, tap Add, and paste the client key below."
                } else {
                    "You're using your own key, $ownKey. Its page on the developer site can pause or replace it."
                },
                icon = Icons.Rounded.Key,
            ) {
                SpicyButton(if (ownKey == null) "Get key" else "Open", onClick = { openUrl(context, SpicyLyricsKey.CATALOG_URL) })
            }
            SettingRow(
                label = "Your Spicy Lyrics key",
                description = if (ownKey == null) "Paste the client key (sl_pk_…). Empty uses the built-in key." else "Paste a new key to replace yours.",
                icon = Icons.Rounded.Key,
                stacked = true,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
                    SpicyTextField(clientKey, { clientKey = it }, placeholder = "sl_pk_…", password = true, modifier = Modifier.weight(1f))
                    SpicyButton("Use", onClick = {
                        viewModel.useApiKey(clientKey)
                        if (SpicyLyricsKey.check(clientKey) is SpicyLyricsKey.Check.Ok) clientKey = ""
                    })
                }
            }
            if (ownKey != null) {
                SettingRow(
                    label = "Use the built-in key",
                    description = "Forget your key on this phone. It keeps working on the developer site until you delete it there.",
                    icon = Icons.Rounded.KeyOff,
                ) {
                    SpicyButton("Remove", onClick = { viewModel.useApiKey("") })
                }
            }
        }
    }
}

/**
 * The sources as cards, in the user's order, reordered by dragging a card's handle: the card
 * follows the finger, the others make way, the page scrolls at its edges, and the order is saved
 * when it's dropped. Tapping a card opens it.
 */
@Composable
private fun SourceList(ranking: SourceRanking, names: Map<String, String>, viewModel: ExternalPlaybackViewModel) {
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    val byId = ranking.origins.associateBy { it.id }
    // While a card is dragged: the order shown, which card, and how far it is from its slot.
    var dragOrder by remember { mutableStateOf<List<String>?>(null) }
    var dragged by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableMapOf<String, Float>() }
    val tops = remember { mutableMapOf<String, Float>() }
    val density = LocalDensity.current
    val gap = with(density) { SpicySpacing.S2.toPx() }
    val edge = with(density) { DRAG_EDGE.toPx() }
    val windowHeight = LocalWindowInfo.current.containerSize.height.toFloat()
    val scroll = LocalPageScroll.current
    val haptics = LocalHapticFeedback.current
    val animate = LocalUiAnimations.current
    val saved = ranking.origins.map { it.id }
    val shown = dragOrder ?: saved
    // A card's drag handler outlives the composition it started in: it reads the order through
    // these, or a second drag starts from the order before the first one.
    val currentShown by rememberUpdatedState(shown)
    val currentSaved by rememberUpdatedState(saved)

    // Past half of a neighbour, the dragged card takes its place; its offset keeps it under the finger.
    fun settle() {
        val id = dragged ?: return
        val order = (dragOrder ?: return).toMutableList()
        var i = order.indexOf(id)
        var moved = false
        while (true) {
            val toward = if (dragOffset > 0f) i + 1 else i - 1
            if (toward !in order.indices) break
            val room = (heights[order[toward]] ?: break) + gap
            if (abs(dragOffset) <= room / 2f) break
            Collections.swap(order, i, toward)
            dragOffset += if (toward > i) -room else room
            i = toward
            moved = true
        }
        if (moved) {
            dragOrder = order
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    // Near the top or bottom of the screen, the page scrolls under the dragged card.
    LaunchedEffect(dragged) {
        val id = dragged ?: return@LaunchedEffect
        if (scroll == null || windowHeight <= 0f) return@LaunchedEffect
        while (dragged == id) {
            val top = (tops[id] ?: 0f) + dragOffset
            val bottom = top + (heights[id] ?: 0f)
            val speed = when {
                top < edge -> -(edge - top) / edge
                bottom > windowHeight - edge -> (bottom - windowHeight + edge) / edge
                else -> 0f
            }.coerceIn(-1f, 1f) * DRAG_SCROLL_PX
            if (speed != 0f) {
                dragOffset += scroll.scrollBy(speed)
                settle()
            }
            withFrameNanos { }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
        shown.forEachIndexed { index, id ->
            val origin = byId[id] ?: return@forEachIndexed
            val summary = origin.summary(ranking)
            val isDragged = dragged == id
            key(id) {
                Searchable("Sources", origin.displayName, summary, *origin.providerIds.map { names[it] ?: it }.toTypedArray()) {
                    SourceCard(
                        rank = index + 1,
                        origin = origin,
                        summary = summary,
                        ranking = ranking,
                        providerName = { names[it] ?: it },
                        expanded = id in expanded,
                        dragged = isDragged,
                        onExpand = { expanded = if (id in expanded) expanded - id else expanded + id },
                        onEnabledChange = { viewModel.setSourceEnabled(id, it) },
                        onProviderChange = { provider, on -> viewModel.setProviderEnabled(id, provider, on) },
                        onSyncTypesChange = { viewModel.setSourceSyncTypes(id, it) },
                        handle = Modifier.pointerInput(id) {
                            detectDragGestures(
                                onDragStart = {
                                    dragOrder = currentShown
                                    dragged = id
                                    dragOffset = 0f
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragEnd = {
                                    val order = dragOrder
                                    dragged = null
                                    dragOffset = 0f
                                    dragOrder = null
                                    if (order != null && order != currentSaved) viewModel.setSourceOrder(order)
                                },
                                onDragCancel = {
                                    dragged = null
                                    dragOffset = 0f
                                    dragOrder = null
                                },
                            ) { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                                settle()
                            }
                        },
                        modifier = Modifier
                            // Where the card's slot is, read before the drag moves it.
                            .onGloballyPositioned {
                                heights[id] = it.size.height.toFloat()
                                tops[id] = it.positionInWindow().y
                            }
                            .glidesToPlace(enabled = animate && dragged != null && !isDragged)
                            .zIndex(if (isDragged) 1f else 0f)
                            .graphicsLayer {
                                if (isDragged) {
                                    translationY = dragOffset
                                    scaleX = DRAGGED_SCALE
                                    scaleY = DRAGGED_SCALE
                                    // Lifted off the list: a shadow under it.
                                    shadowElevation = DRAGGED_ELEVATION.toPx()
                                    shape = RoundedCornerShape(SpicyRadii.Lg)
                                }
                            },
                    )
                }
            }
        }
    }
}

/** A card the reorder moved glides from where it was instead of jumping. */
private fun Modifier.glidesToPlace(enabled: Boolean): Modifier = composed {
    val shift = remember { Animatable(0f) }
    var lastY by remember { mutableStateOf<Float?>(null) }
    val scope = rememberCoroutineScope()
    val enabledNow by rememberUpdatedState(enabled)
    onPlaced { coordinates ->
        val y = coordinates.positionInParent().y
        val previous = lastY
        lastY = y
        if (enabledNow && previous != null && previous != y) {
            scope.launch {
                shift.snapTo(shift.value + previous - y)
                shift.animateTo(0f, tween(SpicyMotion.FAST_MS, easing = SpicyMotion.Standard))
            }
        }
    }.graphicsLayer { translationY = shift.value }
}

/**
 * A source's card: the rank in a ring, the name and what it gives, a chevron, the switch and the
 * drag handle. Tapping the card opens it to the providers that fetch the source and the sync
 * types it may give.
 */
@Composable
private fun SourceCard(
    rank: Int,
    origin: LyricsOrigin,
    summary: String,
    ranking: SourceRanking,
    providerName: (String) -> String,
    expanded: Boolean,
    dragged: Boolean,
    onExpand: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onProviderChange: (String, Boolean) -> Unit,
    onSyncTypesChange: (Set<RemoteLyricsQuality>) -> Unit,
    handle: Modifier,
    modifier: Modifier = Modifier,
) {
    val enabled = LyricsSourceSettings.isOn(ranking, origin.id)
    val animate = LocalUiAnimations.current
    val chevron by animateFloatAsState(
        if (expanded) 180f else 0f,
        if (animate) tween(SpicyMotion.FAST_MS, easing = SpicyMotion.Standard) else snap(),
        label = "sourceChevron",
    )
    // Dragged, the card is solid, or the cards it passes would show through its tint: it carries
    // the page's own backdrop (the blurred lyrics and the theme's wash), so it looks the same.
    val backdrop = LocalSettingsBackdrop.current
    val brand = SpicyColors.brand
    val lifted = if (!dragged) Modifier else Modifier
        .clip(RoundedCornerShape(SpicyRadii.Lg))
        .then(
            if (backdrop != null) {
                Modifier.hazeEffect(backdrop, SettingsBackdrop) { inputScale = HazeInputScale.Fixed(BACKDROP_INPUT_SCALE) }
            } else {
                Modifier.background(Color.Black.copy(alpha = 0.97f))
            },
        )
        .drawBehind {
            if (brand != null) {
                drawRect(brand.fieldDeep.copy(alpha = 0.14f))
                drawBrandRamp(brand, fromAlpha = 0.26f, toAlpha = 0.4f)
            }
        }
    Column(modifier.fillMaxWidth().then(lifted).outlinedCard(tinted = true)) {
        Row(
            Modifier.fillMaxWidth().padding(end = SpicySpacing.S1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2),
        ) {
          // What opens the source, beside its switch rather than around it: a remote's arrows only
          // move to what lies outside the focused control.
          Row(
            Modifier
                .weight(1f)
                .focusRing(RoundedCornerShape(SpicyRadii.Md))
                .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onExpand)
                .padding(start = SpicySpacing.S3, top = SpicySpacing.S3, bottom = SpicySpacing.S3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2),
          ) {
            Box(
                Modifier
                    .size(28.dp)
                    .background(RankFill, CircleShape)
                    .border(1.dp, SpicyColors.Hairline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("$rank", style = SpicyType.Caption.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
            }
            RowLabel(origin.displayName, summary, Modifier.weight(1f).alpha(if (enabled) 1f else DISABLED_ALPHA))
            Icon(
                Icons.Rounded.ExpandMore,
                if (expanded) "Close ${origin.displayName}" else "Open ${origin.displayName}",
                tint = SpicyColors.TextSecondary,
                modifier = Modifier.size(20.dp).rotate(chevron),
            )
          }
            SpicyToggle(enabled, onCheckedChange = onEnabledChange)
            Box(handle.size(32.dp, 40.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.DragIndicator, "Drag ${origin.displayName} to reorder", tint = SpicyColors.TextTertiary, modifier = Modifier.size(20.dp))
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = if (animate) expandVertically(tween(SpicyMotion.MODAL_MS, easing = SpicyMotion.Modal)) + fadeIn(tween(SpicyMotion.FAST_MS)) else EnterTransition.None,
            exit = if (animate) shrinkVertically(tween(SpicyMotion.FAST_MS, easing = SpicyMotion.Standard)) + fadeOut(tween(SpicyMotion.FAST_MS)) else ExitTransition.None,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(start = SpicySpacing.S3, end = SpicySpacing.S3, bottom = SpicySpacing.S3),
                verticalArrangement = Arrangement.spacedBy(SpicySpacing.S3),
            ) {
                origin.providerIds.forEach { provider ->
                    val on = ranking.pairOn(origin.id, provider)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
                        RowLabel(
                            providerName(provider),
                            providerNote(origin, provider),
                            Modifier.weight(1f).alpha(if (on && ranking.sourceOn(origin.id)) 1f else DISABLED_ALPHA),
                            icon = if (LyricsBlends.byId(provider) != null) Icons.AutoMirrored.Rounded.CallMerge else Icons.Rounded.Cloud,
                        )
                        SpicyToggle(on, onCheckedChange = { onProviderChange(provider, it) })
                    }
                }
                if (origin.qualities.size > 1) {
                    val off = ranking.policy.syncTypesOff[origin.id].orEmpty()
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
                        RowLabel("Sync types", "Answers in a type left out don't count.", Modifier.weight(1f), icon = Icons.Rounded.Sync)
                        SpicyMultiSelect(
                            selected = origin.qualities.filter { it !in off }.map { it.name }.toSet(),
                            options = origin.qualities.map { it.name },
                            labels = origin.qualities.map { it.typeLabel() },
                            onChange = { picked -> onSyncTypesChange(picked.map(RemoteLyricsQuality::valueOf).toSet()) },
                        )
                    }
                }
            }
        }
    }
}

/** How close to the screen's top or bottom a dragged card scrolls the page. */
private val DRAG_EDGE = 72.dp
/** The page's fastest scroll under a dragged card, per frame. */
private const val DRAG_SCROLL_PX = 18f
private const val DRAGGED_SCALE = 1.02f
private val DRAGGED_ELEVATION = 12.dp

/** The rank ring's fill. */
private val RankFill = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.09f)

/** What a source gives (the sync types it has on) and, when several can fetch it, how many do. */
private fun LyricsOrigin.summary(ranking: SourceRanking): String {
    val off = ranking.policy.syncTypesOff[id].orEmpty()
    val types = qualities.filter { it !in off }
    val synced = types.filter { it != RemoteLyricsQuality.PLAIN }.map { if (it == RemoteLyricsQuality.WORD_SYNCED) "word" else "line" }
    val gives = listOfNotNull(
        synced.takeIf { it.isNotEmpty() }?.joinToString(" and ", postfix = " synced"),
        "unsynced".takeIf { RemoteLyricsQuality.PLAIN in types },
    ).joinToString(", ").replaceFirstChar { it.uppercase() }.ifEmpty { "No sync type on" }
    if (providerIds.size <= 1) return gives
    return "$gives · ${providerIds.count { ranking.pairOn(id, it) }} of ${providerIds.size} on"
}

private fun RemoteLyricsQuality.typeLabel() = when (this) {
    RemoteLyricsQuality.WORD_SYNCED -> "Word"
    RemoteLyricsQuality.LINE_SYNCED -> "Line"
    else -> "Unsynced"
}

/** A short line on how [provider] gets [origin]'s lyrics, where that isn't plain from its name. */
private fun providerNote(origin: LyricsOrigin, provider: String): String? = when {
    LyricsBlends.byId(provider) != null -> "Lines from the sources above, word timing from its donors."
    provider == "rmm_revival" -> "Relays Spicy Lyrics' API by Apple Music ID."
    provider == "spicy_unison" -> "Spicy Lyrics' copy of Unison's lyrics, by the song's ISRC or YouTube video."
    provider == "spicy_lyrics" && origin.id != LyricsOrigins.SPICY_COMMUNITY -> "Passed on by Spicy Lyrics' API."
    provider == "lrc_red" && origin.id == LyricsOrigins.APPLE_MUSIC -> "Serves Apple Music's lyrics by ISRC."
    provider == "lrcmux" -> "Passes on what it finds at ${origin.displayName}."
    else -> null
}

@Composable
internal fun AdvancedContent(state: PlayerUiState, viewModel: ExternalPlaybackViewModel, updater: UpdateViewModel, settings: AppSettings) {
    SettingRow(
        label = "Clear All Caches for Current Song",
        description = "Remove all cached lyrics data for the currently playing track.",
        icon = Icons.Rounded.DeleteSweep,
    ) {
        SpicyButton("Clear", onClick = viewModel::clearCurrentSongCaches)
    }
    SettingRow(
        label = "Clear Stored Lyrics Cache",
        description = "Delete lyrics that have been cached for up to 3 days.",
        icon = Icons.Rounded.DeleteForever,
    ) {
        SpicyButton("Clear Cache", onClick = viewModel::clearStoredLyricsCache)
    }
    SettingRow(
        label = "Clear Current Song from Internal State",
        description = "Remove the current song's lyrics from the in-memory state only.",
        icon = Icons.Rounded.Memory,
    ) {
        SpicyButton("Clear State", onClick = viewModel::clearCurrentSongFromMemory)
    }
    SettingsSection("Backup") {
        SettingsBackupRows(viewModel, settings)
    }
    SettingsSection("Help") {
        SettingRow(
            label = "Show the Introduction",
            description = "Walk through the first-run screens again, gestures included.",
            icon = Icons.Rounded.School,
        ) {
            SpicyButton("Show", onClick = {
                settings.introDone = false
                settings.introReplay = true
            })
        }
    }
    if (updater.enabled) {
        SettingsSection("Updates") {
            ToggleRow(
                label = "Include pre-releases",
                checked = settings.includePrereleases,
                onCheckedChange = { settings.includePrereleases = it },
                description = "Also offer test builds, which get new things first and may be rough.",
                icon = Icons.Rounded.Science,
            )
        }
    }
    SettingsSection("Privacy") {
        val context = LocalContext.current
        ToggleRow(
            label = "Share anonymous usage stats",
            checked = settings.usageStats,
            onCheckedChange = {
                settings.usageStats = it
                UsageStats.onSettingChanged(context, it)
            },
            description = "How many people use the app, on which versions and devices, and which features they use. Never what you listen to.",
            icon = Icons.Rounded.Insights,
        )
    }
    SettingsSection("Debug info") {
        CopyDebugInfoRow(state)
        CopyExtraLogsRow()
    }
    MoreOptions("Session, these lyrics, translation and last lookup details") {
        SettingsSection("Session") { InfoLines(sessionLines(state)) }
        lyricsLines(state)?.let { details -> SettingsSection("These lyrics") { InfoLines(details) } }
        SettingsSection("Romanization and translation") {
            InfoLines(textLines(LocalContext.current, state))
            // What translation did last, newest last. The whole story goes in the extra logs.
            val log = com.tx24.spicyplayer.playback.TranslationTrace.lines(android.os.SystemClock.elapsedRealtime())
            if (log.isEmpty()) {
                Text("Nothing translated yet", style = DescriptionStyle, modifier = Modifier.padding(horizontal = SpicySpacing.S3, vertical = SpicySpacing.S1))
            }
            log.takeLast(SHOWN_TRANSLATION_LOG).forEach { line ->
                Searchable("Translation log", line, "Diagnostics") {
                    Text(line, style = SpicyType.Caption.copy(color = SpicyColors.TextSecondary),
                        modifier = Modifier.padding(horizontal = SpicySpacing.S3, vertical = SpicySpacing.S1))
                }
            }
        }
        SettingsSection("Last lookup") {
            Searchable("Last lookup", "Attempts", "Diagnostics") {
                if (state.providerAttempts.isEmpty()) {
                    Text("No lookup yet", style = DescriptionStyle, modifier = Modifier.padding(horizontal = SpicySpacing.S3, vertical = SpicySpacing.S1))
                }
                state.providerAttempts.forEach { attempt -> AttemptLine(attempt, state) }
            }
        }
    }
}

/** How many of the translation log's lines Advanced shows; Copy extra logs has them all. */
private const val SHOWN_TRANSLATION_LOG = 5

internal fun sessionLines(state: PlayerUiState): List<Pair<String, String>> = listOfNotNull(
    "Player" to (state.sourcePackage ?: "None"),
    "Output" to state.outputLabel,
    state.matchInfo?.let { "Match" to it },
    state.detectedSpotifyId?.let { "Spotify ID" to it },
    if (state.canSeek) null else "Seeking" to "This player doesn't allow it",
    state.lastCommandLatencyMs?.let { "Command answered" to "in $it ms" },
    state.clockDriftMs?.let { "Clock drift" to "${it.signed()} ms" },
)

/** Where the shown lyrics came from, or null while none are shown. */
internal fun lyricsLines(state: PlayerUiState): List<Pair<String, String>>? {
    val lyrics = state.lyrics as? LyricsState.Ready ?: return null
    return listOfNotNull(
        "Source" to lyrics.provider,
        lyrics.source?.let { "Origin" to it },
        lyrics.maker?.let { "Made by" to it.username },
        lyrics.uploader?.let { "Uploaded by" to it.username },
        lyrics.songwriters.takeIf { it.isNotEmpty() }?.let { "Writers" to it.joinToString() },
    )
}

internal fun attemptSourceName(attempt: ProviderAttempt, state: PlayerUiState): String =
    (state.sourceDescriptors + LOCAL_SOURCE + UPLOADED_SOURCE)
        .firstOrNull { it.id == attempt.sourceId }?.displayName ?: attempt.sourceId

internal fun ProviderAttemptOutcome.label(): String = when (this) {
    ProviderAttemptOutcome.HIT -> "Found"
    ProviderAttemptOutcome.MISS -> "Not found"
    ProviderAttemptOutcome.NEEDS_MATCH -> "Needs a Spotify link"
    ProviderAttemptOutcome.DISABLED -> "Switched off"
    ProviderAttemptOutcome.COOLING_DOWN -> "Resting after errors"
    ProviderAttemptOutcome.QUEUED, ProviderAttemptOutcome.PENDING -> "Asking…"
    ProviderAttemptOutcome.SKIPPED -> "Skipped"
    ProviderAttemptOutcome.FILTERED -> "Not counted"
    ProviderAttemptOutcome.UNAVAILABLE, ProviderAttemptOutcome.MALFORMED_HIT -> "Failed"
}

/** The attempt's quality, failure and retry time, joined; [time] formats the retry time. */
internal fun attemptDetail(attempt: ProviderAttempt, time: (java.util.Date) -> String): String = listOfNotNull(
    attempt.quality.takeIf { it != RemoteLyricsQuality.NONE }?.label(),
    attempt.failureCategory?.name?.lowercase()?.replace('_', ' '),
    attempt.message,
    attempt.retryAt?.let { "tries again at " + time(java.util.Date(it.toEpochMilli())) },
).joinToString(" · ")

/** Label and value pairs, searchable by their labels. */
@Composable
private fun InfoLines(lines: List<Pair<String, String>>) {
    lines.forEach { (label, value) ->
        Searchable(label, value, "Diagnostics") {
            Row(Modifier.fillMaxWidth().padding(horizontal = SpicySpacing.S3, vertical = SpicySpacing.S1)) {
                Text(label, style = SpicyType.Caption.copy(color = SpicyColors.TextSecondary), modifier = Modifier.width(INFO_LABEL_WIDTH))
                Text(value, style = SpicyType.Caption, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AttemptLine(attempt: ProviderAttempt, state: PlayerUiState) {
    val name = attemptSourceName(attempt, state)
    val outcome = attempt.outcome.label()
    val color = when (attempt.outcome) {
        ProviderAttemptOutcome.HIT -> SpicyColors.StatusSuccess
        ProviderAttemptOutcome.NEEDS_MATCH, ProviderAttemptOutcome.COOLING_DOWN -> SpicyColors.StatusWarning
        ProviderAttemptOutcome.QUEUED, ProviderAttemptOutcome.PENDING -> SpicyColors.StatusInfo
        ProviderAttemptOutcome.UNAVAILABLE, ProviderAttemptOutcome.MALFORMED_HIT -> SpicyColors.StatusDanger
        ProviderAttemptOutcome.MISS, ProviderAttemptOutcome.DISABLED, ProviderAttemptOutcome.SKIPPED,
        ProviderAttemptOutcome.FILTERED -> SpicyColors.TextTertiary
    }
    val context = LocalContext.current
    val locale = androidx.compose.ui.platform.LocalLocale.current.platformLocale
    val detail = attemptDetail(attempt) { date ->
        val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm:ss" else "h:mm:ss a"
        java.text.SimpleDateFormat(pattern, locale).format(date)
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = SpicySpacing.S3, vertical = SpicySpacing.S1)) {
        Text(name, style = SpicyType.Caption.copy(color = SpicyColors.TextSecondary), modifier = Modifier.width(INFO_LABEL_WIDTH))
        Column(Modifier.weight(1f)) {
            Text(outcome, style = SpicyType.Caption.copy(color = color, fontWeight = FontWeight.Medium))
            if (detail.isNotEmpty()) Text(detail, style = DescriptionStyle)
        }
    }
}

private fun RemoteLyricsQuality.label() = when (this) {
    RemoteLyricsQuality.WORD_SYNCED -> "word synced"
    RemoteLyricsQuality.LINE_SYNCED -> "line synced"
    RemoteLyricsQuality.PLAIN -> "unsynced"
    RemoteLyricsQuality.NONE -> "nothing"
}

/** The source, and for a source passing on another's lyrics, where they came from: "Spicy Lyrics (Apple Music)". */
private fun lyricsFrom(lyrics: LyricsState.Ready): String {
    val origin = lyrics.source?.takeIf { it.isNotBlank() && it != lyrics.provider } ?: return lyrics.provider
    return "${lyrics.provider} (${origin})"
}

/** One line on where the current lyrics stand. */
internal fun lyricsSummary(lyrics: LyricsState): String = when (lyrics) {
    is LyricsState.Ready -> lyricsFrom(lyrics) + ", " + when (lyrics.lyricsType) {
        LyricsType.Syllable -> "word synced"
        LyricsType.Line -> "line synced"
        LyricsType.Static -> "unsynced"
    }
    LyricsState.Loading -> "Looking for lyrics…"
    is LyricsState.Error -> lyrics.message
    LyricsState.Idle -> "No lyrics yet"
}

/**
 * [lyricsSummary], naming the source the lookup is still waiting on while the lyrics shown are a
 * stand-in that may yet be replaced.
 */
internal fun songSummary(state: PlayerUiState): String {
    val summary = lyricsSummary(state.lyrics) +
        if (state.songDelayMs != 0) " · ${state.songDelayMs.signed()} ms delay" else ""
    if (state.lyrics !is LyricsState.Ready) return summary
    val waiting = state.providerAttempts.firstOrNull { it.outcome == ProviderAttemptOutcome.PENDING } ?: return summary
    val name = state.sourceDescriptors.firstOrNull { it.id == waiting.sourceId }?.displayName ?: waiting.sourceId
    return "$summary · checking $name…"
}


private val INFO_LABEL_WIDTH = 116.dp

/** Saving the settings to a file and bringing them back from one, through the system's file picker. */
@Composable
private fun SettingsBackupRows(viewModel: ExternalPlaybackViewModel, settings: AppSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SettingsBackupStore(context.applicationContext) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(store.export().toByteArray()) } != null
                }.getOrDefault(false)
            }
            if (written) UsageStats.count(UsageCounter.BACKUP_EXPORT)
            viewModel.showMessage(if (written) "Settings saved." else "Couldn't save the settings there.")
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val error = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                        ?: throw SettingsBackup.InvalidBackup("Couldn't open that file.")
                    store.restore(text)
                }.exceptionOrNull()
            }
            if (error == null) {
                UsageStats.count(UsageCounter.BACKUP_RESTORE)
                settings.reload()
                viewModel.reloadSavedSettings()
                viewModel.showMessage("Settings restored.")
            } else {
                viewModel.showMessage((error as? SettingsBackup.InvalidBackup)?.message ?: "Couldn't read that backup.")
            }
        }
    }
    SettingRow(
        label = "Back up settings",
        description = "Save your settings, source order, delays and Spotify links to a file. Your custom font and Spicy Lyrics key stay out.",
        icon = Icons.Rounded.Save,
    ) {
        SpicyButton("Save", onClick = { export.launch("spicy-lyrics-mobile-settings-${java.time.LocalDate.now()}.json") })
    }
    SettingRow(
        label = "Restore settings",
        description = "Replace your settings with the ones in a backup file.",
        icon = Icons.Rounded.SettingsBackupRestore,
    ) {
        // Some file managers label .json as plain text or as nothing in particular.
        SpicyButton("Restore", onClick = { import.launch(arrayOf("application/json", "text/*", "application/octet-stream")) })
    }
}
