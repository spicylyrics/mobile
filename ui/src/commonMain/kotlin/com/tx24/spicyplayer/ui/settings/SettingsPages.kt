package com.tx24.spicyplayer.ui.settings

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
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Tune
import com.tx24.spicyplayer.ui.nowplaying.HeaderSize
import com.tx24.spicyplayer.ui.nowplaying.StageArt
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.tx24.spicyplayer.network.data.SpicyLyricsKey

/*
 * What each group's page holds. Every page also shows up, row by row, in the search results, so a
 * page's first rows go without a section title (the page header says it) and later groups get one.
 */

/**
 * A page's less-used rows, folded behind one row that names them until it's opened, so a page
 * shows first what people actually change. Search shows them unfolded (each row still matches
 * on its own), so a folded row is never harder to find.
 */
@Composable
fun MoreOptions(summary: String, content: @Composable () -> Unit) {
    if (LocalSettingsQuery.current.isNotBlank()) return content()
    var open by rememberSaveable { mutableStateOf(false) }
    val animate = LocalUiAnimations.current
    Column(Modifier.fillMaxWidth().padding(vertical = SpicySpacing.S2).outlinedCard()) {
        GroupRow(
            Icons.Rounded.Tune,
            if (open) "Fewer options" else "More options",
            summary,
            trailing = if (open) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
        ) { open = !open }
    }
    AnimatedVisibility(
        visible = open,
        enter = if (animate) expandVertically(tween(SpicyMotion.MODAL_MS, easing = SpicyMotion.Modal)) + fadeIn(tween(SpicyMotion.FAST_MS)) else EnterTransition.None,
        exit = if (animate) shrinkVertically(tween(SpicyMotion.FAST_MS, easing = SpicyMotion.Standard)) + fadeOut(tween(SpicyMotion.FAST_MS)) else ExitTransition.None,
    ) {
        Column(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
fun LyricsContent(host: SettingsHost, settings: CommonAppSettings) {
    SettingRow(
        label = "Lyrics Style",
        description = "How the lyrics look and move. Apple Music lifts words as they're sung, with a soft wipe, and has lines follow one after another.",
        icon = Icons.Rounded.Style,
    ) {
        SpicySelect(
            value = settings.lyricsStyle.name,
            options = LyricsStyle.entries.map { it.name },
            labels = LyricsStyle.entries.map { it.label },
            onChange = { settings.lyricsStyle = LyricsStyle.valueOf(it) },
        )
    }
    // Simple, Minimal and the word motion are the Spicy Lyrics style's own.
    val spicyStyle = settings.lyricsStyle == LyricsStyle.Spicy
    ToggleRow(
        label = "Simple Lyrics Mode",
        checked = settings.simpleLyricsMode,
        onCheckedChange = { settings.simpleLyricsMode = it },
        description = "Remove extra visual effects from lyrics.",
        icon = Icons.Rounded.AutoFixOff,
        enabled = spicyStyle,
    )
    ToggleRow(
        label = "Refreshed Simple Lyrics",
        checked = settings.refreshedSimpleLyrics,
        onCheckedChange = { settings.refreshedSimpleLyrics = it },
        description = "Softer fill, flowing letters and rippling scroll.",
        icon = Icons.Rounded.AutoFixOff,
        enabled = spicyStyle && settings.simpleLyricsMode,
    )
    ToggleRow(
        label = "Minimal Lyrics Mode",
        checked = settings.minimalLyricsMode,
        onCheckedChange = { settings.minimalLyricsMode = it },
        description = "Hides sung lyrics lines.",
        icon = Icons.Rounded.VisibilityOff,
        enabled = spicyStyle,
    )
    ToggleRow(
        label = "Original word motion",
        checked = settings.originalWordMotion,
        onCheckedChange = { settings.originalWordMotion = it },
        description = "The desktop amount of grow and lift on sung words. Off: ${CommonAppSettings.WORD_MOTION_BOOST}×, which reads better on a phone.",
        icon = Icons.Rounded.Height,
        enabled = spicyStyle,
    )
    SettingsSection("Text") {
        SettingRow(
            label = "Lyrics size",
            description = "With the phone upright, against the screen's default.",
            icon = Icons.Rounded.FormatSize,
            stacked = true,
            searchDescription = "Make the lyrics smaller or bigger than the screen's default. Portrait.",
        ) {
            SpicyBipolarSlider(
                value = settings.lyricsSizePortrait,
                range = LYRICS_SIZE_RANGE,
                step = 5,
                onValueChange = { settings.lyricsSizePortrait = it },
                default = 100,
                unit = "%",
            )
        }
        SettingRow(
            label = "Lyrics size in landscape",
            description = "With the phone on its side, against the screen's default.",
            icon = Icons.Rounded.ScreenRotation,
            stacked = true,
            searchDescription = "Make the lyrics smaller or bigger in landscape.",
        ) {
            SpicyBipolarSlider(
                value = settings.lyricsSizeLandscape,
                range = LYRICS_SIZE_RANGE,
                step = 5,
                onValueChange = { settings.lyricsSizeLandscape = it },
                default = 100,
                unit = "%",
            )
        }
        LyricsFontRows(host, settings)
    }
    MoreOptions("Merge syllables, duet padding, credits, blur and glow") {
        SettingRow(
            label = "Merge syllables",
            description = "Draw a word split into syllables as one word. Steadily sung words only merges the words whose parts have no gap and keep an even pace. Held words only merges the words that are then long enough for the held-word letter effect.",
            icon = Icons.Rounded.CallMerge,
        ) {
            SpicySelect(
                value = settings.syllableMerge.name,
                options = SyllableMerge.entries.map { it.name },
                labels = SyllableMerge.entries.map { it.label },
                onChange = { settings.syllableMerge = SyllableMerge.valueOf(it) },
            )
        }
        ToggleRow(
            label = "Duet Line Padding",
            checked = settings.duetLinePadding,
            onCheckedChange = { settings.duetLinePadding = it },
            description = "Indents lyrics lines on the side they lean away from when a song has duet lines, so the two voices read as separate columns. Disable to give every line the same slight padding.",
            icon = Icons.Rounded.FormatIndentIncrease,
        )
        ToggleRow(
            label = "Credits Follow the Last Singer",
            checked = settings.creditsFollowLastLine,
            onCheckedChange = { settings.creditsFollowLastLine = it },
            description = "When a song ends on a duet line sung from the right, move the credits under it to the right too.",
            icon = Icons.Rounded.FormatAlignRight,
        )
        ToggleRow(
            label = "Keep Overlapping Lines",
            checked = settings.keepOverlappingLines,
            onCheckedChange = { settings.keepOverlappingLines = it },
            description = "In Minimal Lyrics Mode, keep a line that ends while one above it is still sung on screen, dimmed, instead of fading it out early.",
            icon = Icons.Rounded.Layers,
        )
        ToggleRow(
            label = "Background Vocal Pop-Out",
            checked = settings.backgroundPop,
            onCheckedChange = { settings.backgroundPop = it },
            description = "Keep background vocal lines tucked under their line until it is sung, then slide them out.",
            icon = Icons.Rounded.UnfoldMore,
        )
        ToggleRow(
            label = "Refined Credits",
            checked = settings.refinedCredits,
            onCheckedChange = { settings.refinedCredits = it },
            description = "Show the credits as quiet type close under the last line, dim until the song has been sung.",
            icon = Icons.Rounded.Notes,
        )
        SettingsSection("Effects") {
            ToggleRow(
                label = "Blur distant lines",
                checked = settings.distanceBlur && !settings.lowPerformance,
                onCheckedChange = { settings.distanceBlur = it },
                description = "Soften the lines further from the one being sung.",
                icon = Icons.Rounded.BlurOn,
                enabled = !settings.lowPerformance,
            )
            ToggleRow(
                label = "Glow",
                checked = settings.glow && !settings.lowPerformance,
                onCheckedChange = { settings.glow = it },
                description = "Let sung words glow.",
                icon = Icons.Rounded.Flare,
                enabled = !settings.lowPerformance,
            )
        }
    }
}

/**
 * The lyrics font: ours, the phone's, or a font file picked from the phone. Choosing Custom with
 * nothing picked yet opens the picker.
 */
@Composable
private fun LyricsFontRows(host: SettingsHost, settings: CommonAppSettings) {
    var failed by remember { mutableStateOf(false) }
    val pick = host.rememberLyricsFontPicker { failed = it }
    val hasCustom = settings.customFontFile.isNotBlank()
    SettingRow(label = "Lyrics Font", description = "Draw the lyrics in their own font, your phone's, or any font file on your phone.", icon = Icons.Rounded.FontDownload) {
        SpicySelect(
            value = settings.lyricsFont.name,
            options = LyricsFont.entries.map { it.name },
            labels = LyricsFont.entries.map { if (it == LyricsFont.Custom && hasCustom) settings.customFontName else it.label },
            onChange = { choice ->
                val font = LyricsFont.valueOf(choice)
                if (font == LyricsFont.Custom && !hasCustom) pick() else settings.lyricsFont = font
            },
        )
    }
    SettingRow(
        label = "Font File",
        description = when {
            failed -> "That file isn't a font Android can read. Pick a .ttf or .otf file."
            hasCustom -> "Using ${settings.customFontName}."
            else -> "Pick a .ttf or .otf font file from your phone."
        },
        icon = Icons.Rounded.FolderOpen,
    ) {
        SpicyButton(if (hasCustom) "Change" else "Choose", onClick = pick)
    }
}

@Composable
fun ScrollSyncContent(state: SettingsState, host: SettingsHost, settings: CommonAppSettings) {
    SettingRow(
        label = "Lyric delay",
        description = "Saved for ${state.outputLabel}. Move it right if the lyrics run ahead of the song.",
        icon = Icons.Rounded.Timer,
        stacked = true,
    ) {
        DelayControl(state.lyricDelayMs, host::setLyricDelay)
    }
    host.DelayCalibrationRow()
    SettingsSection("Scrolling") {
        ToggleRow(
            label = "Early Scroll",
            checked = settings.scrollLeadEnabled,
            onCheckedChange = { settings.scrollLeadEnabled = it },
            description = "Start scrolling to the next line slightly before it becomes active, so the move feels less abrupt.",
            icon = Icons.Rounded.KeyboardDoubleArrowUp,
        )
        SettingRow(
            label = "Early Scroll Time",
            description = "How early the next line is scrolled to, before it becomes active.",
            icon = Icons.Rounded.Timer,
            enabled = settings.scrollLeadEnabled,
            stacked = true,
        ) {
            SpicyBipolarSlider(
                value = settings.scrollLeadMs,
                range = 0..800,
                step = 10,
                onValueChange = { settings.scrollLeadMs = it },
                default = 250,
                unit = "ms",
                enabled = settings.scrollLeadEnabled,
            )
        }
        ToggleRow(
            label = "Smooth Scrolling",
            checked = settings.smoothScrolling,
            onCheckedChange = { settings.smoothScrolling = it },
            description = "Makes the lyrics scroll smoothly.",
            icon = Icons.Rounded.Waves,
        )
    }
    MoreOptions("Seek fade-in compensation, scroll-to-active button, auto-scroll") {
        ToggleRow(
            label = "Seek Fade-in Compensation",
            checked = settings.seekFadeCompensation,
            onCheckedChange = { settings.seekFadeCompensation = it },
            description = "Tapping a line jumps 300ms before it, so the player's fade-in doesn't cut off the start. Best for rap or fast-paced songs.",
            icon = Icons.Rounded.FastRewind,
        )
        ToggleRow(
            label = "Show Scroll to Active Button",
            checked = settings.showScrollToActive,
            onCheckedChange = { settings.showScrollToActive = it },
            description = "Show an arrow when the active lyric is outside the viewport.",
            icon = Icons.Rounded.VerticalAlignCenter,
        )
        ToggleRow(
            label = "Auto-scroll Unsynced Lyrics",
            checked = settings.staticAutoScroll,
            onCheckedChange = { settings.staticAutoScroll = it },
            description = "Lyrics without timing scroll through at the song's pace, from top to bottom.",
            icon = Icons.Rounded.SwapVert,
        )
    }
}

@Composable
fun BackgroundContent(settings: CommonAppSettings, host: SettingsHost) {
    val type = settings.backgroundType
    SettingRow(
        label = "Background Type",
        description = "Choose the dynamic, legacy, static image, or color background.",
        icon = Icons.Rounded.Wallpaper,
    ) {
        SpicySelect(
            value = type.name,
            options = BackgroundType.entries.map { it.name },
            labels = BackgroundType.entries.map { it.label },
            onChange = { settings.backgroundType = BackgroundType.valueOf(it) },
        )
    }
    // Each background keeps its own blur: they're measured differently and start from different looks.
    if (type != BackgroundType.Color) {
        SettingRow(
            label = "Background Blur",
            description = if (type.image) "Soften the static background image." else "How soft the moving cover is.",
            icon = Icons.Rounded.BlurLinear,
            stacked = true,
        ) {
            when (type) {
                BackgroundType.Default -> SpicyBipolarSlider(
                    value = settings.dynamicBackgroundBlur,
                    range = 0..100,
                    step = 1,
                    onValueChange = { settings.dynamicBackgroundBlur = it },
                    default = DEFAULT_DYNAMIC_BLUR,
                    unit = "%",
                )
                BackgroundType.Legacy -> SpicyBipolarSlider(
                    value = settings.legacyBackgroundBlur,
                    range = 0..MAX_BACKGROUND_BLUR,
                    step = 1,
                    onValueChange = { settings.legacyBackgroundBlur = it },
                    default = LEGACY_BLUR_DP,
                    unit = "dp",
                )
                else -> SpicyBipolarSlider(
                    value = settings.backgroundBlur,
                    range = 0..MAX_BACKGROUND_BLUR,
                    step = 1,
                    onValueChange = { settings.backgroundBlur = it },
                    unit = "dp",
                )
            }
        }
    }
    if (type == BackgroundType.CoverArt) {
        ToggleRow(
            label = "Animated Background",
            checked = settings.animatedBackground && !settings.lowPerformance,
            onCheckedChange = { settings.animatedBackground = it },
            description = "Play the album's animated cover from Apple Music behind the lyrics, where it has one.",
            icon = Icons.Rounded.Animation,
            enabled = !settings.lowPerformance,
        )
    }
    // Next to the animated cover's rows: the other video a background can show.
    ToggleRow(
        label = "Show Canvas Where Available",
        checked = settings.canvasBackground && !settings.lowPerformance,
        onCheckedChange = { on ->
            // Canvas asks a third-party service, so it says what it sends first.
            if (on) host.chooseCanvas { settings.canvasBackground = true } else settings.canvasBackground = false
        },
        description = "Where the song has a Spotify Canvas, the background is that Canvas, blurred. Other songs keep your background.",
        icon = Icons.Rounded.VideoLibrary,
        enabled = !settings.lowPerformance,
    )
    MoreOptions("Refined background, still background, moving with the music") {
        ToggleRow(
            label = "Refined Background",
            checked = settings.refinedBackground,
            onCheckedChange = { settings.refinedBackground = it },
            description = "Crossfade background changes, keep the image's edges, and deepen Cover Colors.",
            icon = Icons.Rounded.AutoAwesome,
        )
        if (type.moving) {
            ToggleRow(
                label = "Still Background",
                checked = settings.staticBackground || settings.lowPerformance,
                onCheckedChange = { settings.staticBackground = it },
                description = "Hold the background still instead of animating it.",
                icon = Icons.Rounded.MotionPhotosPaused,
                enabled = !settings.lowPerformance,
            )
        }
        if (type == BackgroundType.Default) {
            ToggleRow(
                label = "Move with the Music",
                checked = settings.beatReactiveBackground && !settings.staticBackground && !settings.lowPerformance,
                onCheckedChange = { settings.beatReactiveBackground = it },
                description = "Speed the background up and down with the song's tempo, loudness and beats, where Spotify has them.",
                icon = Icons.Rounded.GraphicEq,
                enabled = !settings.staticBackground && !settings.lowPerformance,
            )
            val held = settings.staticBackground || settings.lowPerformance
            ToggleRow(
                label = "Follow the Animated Cover",
                checked = settings.backgroundFollowsMotion && settings.animatedCover && !held,
                onCheckedChange = { settings.backgroundFollowsMotion = it },
                description = when {
                    held -> "Off while the background is held still."
                    !settings.animatedCover -> "Needs Animated Cover (Now Playing) on, whose video it follows."
                    else -> "When the song has an animated cover, the background moves with it."
                },
                icon = Icons.Rounded.SlowMotionVideo,
                enabled = settings.animatedCover && !held,
                searchDescription = "When the song has an animated cover, the background moves with it.",
            )
        }
    }
}

@Composable
fun NowPlayingContent(settings: CommonAppSettings, host: SettingsHost) {
    SettingRow(
        label = "Song Header Size",
        description = "How tall the song header is. Small is a slim row, like Apple Music's.",
        icon = Icons.Rounded.Height,
        enabled = !settings.hideHeader,
    ) {
        SpicySelect(
            value = settings.headerSize.name,
            options = HeaderSize.entries.map { it.name },
            labels = HeaderSize.entries.map { it.label },
            onChange = { settings.headerSize = HeaderSize.valueOf(it) },
        )
    }
    SettingRow(
        label = "Release Year Position",
        description = "Show the release year beside the artists.",
        icon = Icons.Rounded.CalendarMonth,
    ) {
        SpicySelect(
            value = settings.releaseYearPosition.name,
            options = ReleaseYearPosition.entries.map { it.name },
            labels = ReleaseYearPosition.entries.map { it.label },
            onChange = { settings.releaseYearPosition = ReleaseYearPosition.valueOf(it) },
        )
    }
    ToggleRow(
        label = "Animated Cover",
        checked = settings.animatedCover && !settings.lowPerformance,
        onCheckedChange = { settings.animatedCover = it },
        description = "Play the album's animated cover from Apple Music, where it has one.",
        icon = Icons.Rounded.Animation,
        enabled = !settings.lowPerformance,
    )
    SettingRow(
        label = "Cover stage (landscape)",
        description = if (!settings.animatedCover && StageArt.AnimatedCover in settings.stageArt) {
            "In landscape, the first of these the song has fills its side of the screen. The animated cover needs Animated Cover on."
        } else {
            "In landscape, the first of these the song has fills its side of the screen, with the song docked along its bottom. None picked is off."
        },
        icon = Icons.Rounded.Panorama,
        enabled = !settings.lowPerformance,
        searchDescription = "In landscape, the song's Spotify Canvas, animated cover or cover art fills its side of the screen, with the song docked along its bottom.",
    ) {
        SpicyMultiSelect(
            selected = settings.stageArt.mapTo(mutableSetOf()) { it.name },
            options = StageArt.entries.map { it.name },
            labels = StageArt.entries.map { it.label },
            none = "Off",
            enabled = !settings.lowPerformance,
            onChange = { picked ->
                val arts = StageArt.entries.filter { it.name in picked }.toSet()
                // Canvas asks a third-party service, so it says what it sends first.
                if (StageArt.Canvas in arts && StageArt.Canvas !in settings.stageArt) {
                    host.chooseCanvas { settings.stageArt = arts }
                } else {
                    settings.stageArt = arts
                }
            },
        )
    }
    SettingRow(
        label = "Big cover (portrait)",
        description = if (!settings.animatedCover && StageArt.AnimatedCover in settings.bigCoverArt) {
            "In portrait, the first of these the song has takes the big cover's place. The animated cover needs Animated Cover on."
        } else {
            "In portrait, the first of these the song has takes the big cover's place: a Canvas fills the screen, a cover spreads edge to edge. None picked is the big cover as it is."
        },
        icon = Icons.Rounded.Fullscreen,
        enabled = !settings.lowPerformance,
        searchDescription = "In portrait, the song's Spotify Canvas, animated cover or cover art takes the big cover's place, with the song docked under it.",
    ) {
        SpicyMultiSelect(
            selected = settings.bigCoverArt.mapTo(mutableSetOf()) { it.name },
            options = StageArt.entries.map { it.name },
            labels = StageArt.entries.map { it.label },
            none = "Off",
            enabled = !settings.lowPerformance,
            onChange = { picked ->
                val arts = StageArt.entries.filter { it.name in picked }.toSet()
                // Canvas asks a third-party service, so it says what it sends first.
                if (StageArt.Canvas in arts && StageArt.Canvas !in settings.bigCoverArt) {
                    host.chooseCanvas { settings.bigCoverArt = arts }
                } else {
                    settings.bigCoverArt = arts
                }
            },
        )
    }
    MoreOptions("Hiding the header, cover without lyrics, blurred Canvas, pausing, credits") {
        ToggleRow(
            label = "Hide the song header",
            checked = settings.hideHeader,
            onCheckedChange = { settings.hideHeader = it },
            description = "Show only the lyrics, centred on the page (in landscape, without the cover panel). The expand button still opens the big cover.",
            icon = Icons.Rounded.HideImage,
        )
        ToggleRow(
            label = "Show the cover without lyrics",
            checked = settings.expandWithoutLyrics,
            onCheckedChange = { settings.expandWithoutLyrics = it },
            description = "Grow the song header into the big cover when a song has no lyrics.",
            icon = Icons.Rounded.Album,
        )
        val showsCanvas = StageArt.Canvas in settings.stageArt || StageArt.Canvas in settings.bigCoverArt
        ToggleRow(
            label = "Blurred Canvas Behind",
            checked = settings.canvasBackdrop && showsCanvas && !settings.lowPerformance,
            onCheckedChange = { settings.canvasBackdrop = it },
            description = if (showsCanvas) {
                "While the stage or the big cover shows a Canvas, the background becomes it, blurred."
            } else {
                "Needs Spotify Canvas on the cover stage or the big cover."
            },
            icon = Icons.Rounded.BlurOn,
            enabled = showsCanvas && !settings.lowPerformance,
            searchDescription = "While the stage or the big cover shows a Canvas, the background becomes it, blurred.",
        )
        SettingRow(
            label = "Pause with the Music",
            description = "These hold their frame while the music is paused.",
            icon = Icons.Rounded.PauseCircle,
            searchDescription = "Animated covers and Canvas videos hold their frame while the music is paused.",
        ) {
            SpicyMultiSelect(
                selected = LOOP_KINDS.filter { it.name in settings.pauseWithMusicKinds }.mapTo(mutableSetOf()) { it.name },
                options = LOOP_KINDS.map { it.name },
                labels = LOOP_KINDS.map { it.label },
                none = "Neither",
                onChange = { picked -> settings.pauseWithMusicKinds = LOOP_KINDS.filter { it.name in picked }.mapTo(mutableSetOf()) { it.name } },
            )
        }
        SettingsSection("Credits") {
            SettingRow(
                label = "Pinned Lyrics Footer",
                description = "Keep source and community credits visible. Full also pins writers.",
                icon = Icons.Rounded.PushPin,
            ) {
                SpicySelect(
                    value = settings.pinnedFooter.name,
                    options = PinnedFooterMode.entries.map { it.name },
                    labels = PinnedFooterMode.entries.map { it.label },
                    onChange = { settings.pinnedFooter = PinnedFooterMode.valueOf(it) },
                )
            }
            ToggleRow(
                label = "Translation & Romanization Credits",
                checked = settings.textCredits,
                onCheckedChange = { settings.textCredits = it },
                description = "Say in the credits who translated and romanized the lyrics on screen.",
                icon = Icons.Rounded.Translate,
            )
            ToggleRow(
                label = "Open Profiles in Browser",
                checked = settings.profilesInBrowser,
                onCheckedChange = { settings.profilesInBrowser = it },
                description = "Open contributor profiles in your browser instead of inside the app.",
                icon = Icons.Rounded.OpenInBrowser,
            )
        }
    }
}

@Composable
fun ControlsContent(settings: CommonAppSettings, host: SettingsHost) {
    ToggleRow(
        label = "Timeline Outside Cover",
        checked = settings.timelineOutsideCover,
        onCheckedChange = { settings.timelineOutsideCover = it },
        description = "In landscape, show the timeline under the cover, above the song, where it always stays.",
        icon = Icons.Rounded.LinearScale,
    )
    SettingsSection("Floating Buttons") {
        ToggleRow(
            label = "Player buttons",
            checked = settings.playerButtons,
            onCheckedChange = { settings.playerButtons = it },
            description = "Show the extra buttons the player offers, like Like or Save. Shuffle and repeat always stay.",
            icon = Icons.Rounded.Widgets,
        )
        ToggleRow(
            label = "Floating lyrics button",
            checked = settings.pipButton,
            onCheckedChange = { settings.pipButton = it },
            description = "Show the button that shrinks the lyrics into a small floating window, over other apps.",
            icon = SpicyGlyphs.PictureInPicture,
            enabled = host.pictureInPicture,
        )
        ToggleRow(
            label = "Lyrics Manager button",
            checked = settings.lyricsManagerButton,
            onCheckedChange = { settings.lyricsManagerButton = it },
            description = "Show its own button for the Lyrics Manager. It's always in quick settings and This song.",
            icon = SpicyGlyphs.LyricsManager,
        )
        ToggleRow(
            label = "Queue button",
            checked = settings.queueButton,
            onCheckedChange = { settings.queueButton = it },
            description = "Show its own button for the player's queue, while it shares one. It's always in quick settings.",
            icon = SpicyGlyphs.Queue,
        )
    }
    MoreOptions("Hiding the controls, and the resync, expand, quick settings, rotate and source buttons") {
        ToggleRow(
            label = "Hide controls",
            checked = settings.autoHideControls,
            onCheckedChange = { settings.autoHideControls = it },
            description = "Fade the controls out a while after the last touch. A touch brings them back.",
            icon = Icons.Rounded.TouchApp,
        )
        SettingRow(
            label = "Hide After",
            description = "How long the controls stay after the last touch.",
            icon = Icons.Rounded.Timer,
            enabled = settings.autoHideControls,
        ) {
            SpicySelect(
                value = settings.controlsHideDelay.name,
                options = ControlsHideDelay.entries.map { it.name },
                labels = ControlsHideDelay.entries.map { it.label },
                onChange = { settings.controlsHideDelay = ControlsHideDelay.valueOf(it) },
            )
        }
        ToggleRow(
            label = "Hide while paused",
            checked = settings.hideControlsWhilePaused,
            onCheckedChange = { settings.hideControlsWhilePaused = it },
            description = "Fade the controls while the song is paused too, not only while it plays.",
            icon = Icons.Rounded.PauseCircle,
            enabled = settings.autoHideControls,
        )
        SettingsSection("More Buttons") {
            ToggleRow(
                label = "Resync button",
                checked = settings.resyncButton,
                onCheckedChange = { settings.resyncButton = it },
                description = "Show the button that snaps the lyrics back in time with the player.",
                icon = SpicyGlyphs.Resync,
            )
            ToggleRow(
                label = "Expand button",
                checked = settings.expandButton,
                onCheckedChange = { settings.expandButton = it },
                description = "Show the button that opens the big cover. It stays while the cover is open, to get back.",
                icon = SpicyGlyphs.Expand,
            )
            ToggleRow(
                label = "Quick settings button",
                checked = settings.quickSettingsButton,
                onCheckedChange = { settings.quickSettingsButton = it },
                description = "Show the button that opens the delays, the Lyrics Manager and the queue without leaving the lyrics.",
                icon = SpicyGlyphs.QuickSettings,
            )
            ToggleRow(
                label = "Rotate button",
                checked = settings.rotateButton,
                onCheckedChange = { settings.rotateButton = it },
                description = "Show the button that turns the lyrics to landscape and back, even with rotation locked.",
                icon = SpicyGlyphs.Rotate,
            )
            ToggleRow(
                label = "Lyrics source button",
                checked = settings.sourceButton,
                onCheckedChange = { settings.sourceButton = it },
                description = "Show its own button for choosing this song's lyrics source. It's always in quick settings.",
                icon = SpicyGlyphs.Source,
            )
        }
    }
}

@Composable
fun ThemeContent(settings: CommonAppSettings, host: SettingsHost) {
    SettingRow(
        label = "Settings Theme",
        description = "Dress this screen in Spicy Lyrics' purple, or keep the plain glass.",
        icon = Icons.Rounded.Tune,
    ) {
        SpicySelect(
            value = settings.settingsTheme.name,
            options = ChromeTheme.entries.map { it.name },
            labels = ChromeTheme.entries.map { it.label },
            onChange = { settings.settingsTheme = ChromeTheme.valueOf(it) },
        )
    }
    SettingRow(
        label = "Pop-up Theme",
        description = "Dress the pop-ups, like quick settings, the queue and update notices, the same way.",
        icon = Icons.Rounded.Widgets,
    ) {
        SpicySelect(
            value = settings.popupTheme.name,
            options = ChromeTheme.entries.map { it.name },
            labels = ChromeTheme.entries.map { it.label },
            onChange = { settings.popupTheme = ChromeTheme.valueOf(it) },
        )
    }
    host.AppIconRow()
    ToggleRow(
        label = "Peek While Adjusting",
        checked = settings.peekWhileAdjusting,
        onCheckedChange = { settings.peekWhileAdjusting = it },
        description = "Clear this screen away while you drag a slider, so you see the change on the lyrics.",
        icon = Icons.Rounded.Visibility,
    )
}

@Composable
fun DeviceContent(settings: CommonAppSettings, host: SettingsHost) {
    ToggleRow(
        label = "Smoother motion",
        checked = settings.highRefreshRate,
        onCheckedChange = { settings.highRefreshRate = it },
        description = "Draw at your screen's full refresh rate instead of 60 Hz. Uses slightly more battery.",
        icon = Icons.Rounded.Animation,
    )
    if (host.lockScreen) ToggleRow(
        label = "Show over lock screen",
        checked = settings.showOverLockScreen,
        onCheckedChange = { settings.showOverLockScreen = it },
        description = "The power button brings the lyrics back without unlocking. Settings still ask to unlock.",
        icon = Icons.Rounded.ScreenLockPortrait,
    )
    ToggleRow(
        label = "Ambient mode",
        checked = settings.ambientMode,
        onCheckedChange = { settings.ambientMode = it },
        description = "Left untouched while music plays, dim to the lyrics and the song on black.",
        searchDescription = "Untouched for 30 seconds while music plays, dim to the lyrics alone on black.",
        icon = Icons.Rounded.DarkMode,
    )
    SettingRow(
        label = "Dim After",
        description = "How long ambient mode waits after the last touch.",
        icon = Icons.Rounded.Timer,
        enabled = settings.ambientMode,
    ) {
        SpicySelect(
            value = settings.ambientDelay.name,
            options = AmbientDelay.entries.map { it.name },
            labels = AmbientDelay.entries.map { it.label },
            onChange = { settings.ambientDelay = AmbientDelay.valueOf(it) },
        )
    }
    SubPageCard {
        val supported = host.pictureInPicture
        SubPageRow(
            SettingsPage.FloatingLyrics,
            SpicyGlyphs.PictureInPicture,
            when {
                !supported -> "Not available on this phone"
                settings.autoPip -> "Opens when you leave the app · ${settings.pipShape.name}"
                else -> "Off"
            },
        )
    }
    host.HapticsSection()
    MoreOptions("Keeping the screen on, system bars, low performance mode, interface animations") {
        ToggleRow(
            label = "Keep the screen on",
            checked = settings.keepScreenOn,
            onCheckedChange = { settings.keepScreenOn = it },
            description = "Stop the screen from turning off while music plays.",
            icon = Icons.Rounded.StayCurrentPortrait,
        )
        SettingRow(
            label = "System bars",
            description = "Whether the status and navigation bars stay on screen over the lyrics.",
            icon = Icons.Rounded.Fullscreen,
        ) {
            SpicySelect(
                value = settings.systemBars.name,
                options = SystemBarsMode.entries.map { it.name },
                labels = SystemBarsMode.entries.map { it.label },
                onChange = { settings.systemBars = SystemBarsMode.valueOf(it) },
            )
        }
        SettingsSection("Performance") {
            ToggleRow(
                label = "Low performance mode",
                checked = settings.lowPerformance,
                onCheckedChange = { settings.lowPerformance = it },
                description = "Stills the background and cover and turns off blur and glow, for smoother lyrics on slower phones.",
                icon = Icons.Rounded.Speed,
            )
            ToggleRow(
                label = "Interface animations",
                checked = settings.uiAnimations && !settings.lowPerformance,
                onCheckedChange = { settings.uiAnimations = it },
                description = "Animate the playback buttons, the queue and pop-up screens. Off in low performance mode.",
                icon = Icons.Rounded.Animation,
                enabled = !settings.lowPerformance,
            )
        }
    }
}

@Composable
fun FloatingLyricsContent(settings: CommonAppSettings, host: SettingsHost) {
    val supported = host.pictureInPicture
    ToggleRow(
        label = "Floating lyrics",
        checked = settings.autoPip,
        onCheckedChange = { settings.autoPip = it },
        description = "Leave the app while a song plays and the lyrics shrink into a small floating window.",
        icon = SpicyGlyphs.PictureInPicture,
        enabled = supported,
    )
    SettingRow(
        label = "Shape",
        description = "The floating window's width to height.",
        icon = Icons.Rounded.AspectRatio,
        enabled = supported,
    ) {
        SpicySelect(
            value = settings.pipShape.name,
            options = PipShape.entries.map { it.name },
            labels = PipShape.entries.map { it.label },
            onChange = { settings.pipShape = PipShape.valueOf(it) },
            enabled = supported,
        )
    }
    if (settings.pipShape == PipShape.Custom) {
        SettingRow(
            label = "Custom shape",
            description = "Width to height, from tall (0.42 : 1) to wide (2.39 : 1).",
            icon = Icons.Rounded.Crop,
            enabled = supported,
            stacked = true,
        ) {
            SpicyBipolarSlider(
                value = settings.pipCustomRatio,
                range = PIP_RATIO_RANGE,
                step = 1,
                onValueChange = { settings.pipCustomRatio = it },
                default = 100,
                enabled = supported,
                valueText = { "${twoDecimals(it / 100f)} : 1" },
            )
        }
    }
    ToggleRow(
        label = "Show lyrics",
        checked = settings.pipShowLyrics,
        onCheckedChange = { settings.pipShowLyrics = it },
        description = "Off shows the song instead: cover, title and artist.",
        icon = Icons.Rounded.Lyrics,
        enabled = supported,
    )
    SettingRow(
        label = "Lyrics size",
        description = "The floating window's own text size, apart from the full screen's.",
        icon = Icons.Rounded.FormatSize,
        enabled = supported && settings.pipShowLyrics,
        stacked = true,
        searchDescription = "Make the floating lyrics smaller or bigger. Picture-in-picture text size.",
    ) {
        SpicyBipolarSlider(
            value = settings.pipLyricsSize,
            range = LYRICS_SIZE_RANGE,
            step = 5,
            onValueChange = { settings.pipLyricsSize = it },
            default = 100,
            unit = "%",
            enabled = supported && settings.pipShowLyrics,
        )
    }
    ToggleRow(
        label = "Song bar",
        checked = settings.pipDock,
        onCheckedChange = { settings.pipDock = it },
        description = "Square and tall windows show the title, artist and timeline under the lyrics.",
        icon = Icons.Rounded.MusicNote,
        enabled = supported && settings.pipShowLyrics,
    )
    ToggleRow(
        label = "Cover in the song bar",
        checked = settings.pipDockCover,
        onCheckedChange = { settings.pipDockCover = it },
        description = "A small cover beside the title and artist.",
        icon = Icons.Rounded.Album,
        enabled = supported && settings.pipShowLyrics && settings.pipDock,
    )
    SettingRow(
        label = "Without lyrics, show",
        description = "What the window shows for a song that has no lyrics.",
        icon = Icons.Rounded.Album,
        enabled = supported && settings.pipShowLyrics,
    ) {
        SpicySelect(
            value = settings.pipWithoutLyrics.name,
            options = PipWithoutLyrics.entries.map { it.name },
            labels = PipWithoutLyrics.entries.map { it.label },
            onChange = { settings.pipWithoutLyrics = PipWithoutLyrics.valueOf(it) },
            enabled = supported && settings.pipShowLyrics,
        )
    }
}
/**
 * A delay's slider, with a stepper under it: −100, −10, the delay (tap to reset), +10, +100.
 * Used for both the output's delay and the song's.
 */
@Composable
fun DelayControl(valueMs: Int, onValueChange: (Int) -> Unit, enabled: Boolean = true) {
    fun nudge(deltaMs: Int) {
        if (enabled) onValueChange((valueMs + deltaMs).coerceIn(-DELAY_RANGE_MS, DELAY_RANGE_MS))
    }
    Column(verticalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
        SpicyBipolarSlider(
            value = valueMs,
            range = -DELAY_RANGE_MS..DELAY_RANGE_MS,
            step = DELAY_STEP_MS,
            onValueChange = onValueChange,
            enabled = enabled,
            showValue = false,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2), verticalAlignment = Alignment.CenterVertically) {
            val step = Modifier.weight(1f)
            SpicyButton("−$DELAY_BIG_STEP_MS", { nudge(-DELAY_BIG_STEP_MS) }, step, enabled, horizontalPadding = 0.dp)
            SpicyButton("−$DELAY_STEP_MS", { nudge(-DELAY_STEP_MS) }, step, enabled, horizontalPadding = 0.dp)
            SpicyResettableValue(
                "${valueMs.signed()} ms",
                atDefault = valueMs == 0,
                onReset = { if (enabled) onValueChange(0) },
                modifier = Modifier.weight(1.6f),
                enabled = enabled,
            )
            SpicyButton("+$DELAY_STEP_MS", { nudge(DELAY_STEP_MS) }, step, enabled, horizontalPadding = 0.dp)
            SpicyButton("+$DELAY_BIG_STEP_MS", { nudge(DELAY_BIG_STEP_MS) }, step, enabled, horizontalPadding = 0.dp)
        }
    }
}

/** This song's own delay, added to the output's. */
@Composable
fun SongDelayRow(state: SettingsState, host: SettingsHost) {
    SettingRow(
        label = "Song delay",
        description = if (!state.hasSongKey) "Play a song to give it its own delay."
            else "Only for this song, added to the delay for ${state.outputLabel}. For lyrics timed a little off.",
        icon = Icons.Rounded.MusicNote,
        stacked = true,
    ) {
        DelayControl(state.songDelayMs, host::setSongDelay, enabled = state.hasSongKey)
    }
}

/** The delay slider's reach and step; the buttons nudge by one step. */
private const val DELAY_RANGE_MS = com.tx24.spicyplayer.playback.MAX_DELAY_MS
private const val DELAY_STEP_MS = 10
private const val DELAY_BIG_STEP_MS = 100

/** [value] with two decimals, rounded half up: "1.78". */
private fun twoDecimals(value: Float): String {
    val hundredths = kotlin.math.round(value * 100.0).toLong()
    return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
}
