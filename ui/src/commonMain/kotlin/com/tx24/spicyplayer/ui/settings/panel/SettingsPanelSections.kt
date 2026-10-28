package com.tx24.spicyplayer.ui.settings.panel

import com.tx24.spicyplayer.lyrics.spicy.SyllableMerge
import com.tx24.spicyplayer.lyrics.spicy.canvas.PinnedFooterMode
import com.tx24.spicyplayer.lyrics.spicy.romanization.RomanizationMode
import com.tx24.spicyplayer.translation.TranslationMode
import com.tx24.spicyplayer.ui.components.ChromeTheme
import com.tx24.spicyplayer.ui.nowplaying.HeaderSize
import com.tx24.spicyplayer.ui.settings.BackgroundType
import com.tx24.spicyplayer.ui.settings.CommonAppSettings
import com.tx24.spicyplayer.ui.settings.ControlsHideDelay
import com.tx24.spicyplayer.ui.settings.LYRICS_SIZE_RANGE
import com.tx24.spicyplayer.ui.settings.LyricsFont
import com.tx24.spicyplayer.ui.settings.LyricsStyle
import com.tx24.spicyplayer.ui.settings.MAX_BACKGROUND_BLUR
import com.tx24.spicyplayer.ui.settings.DEFAULT_DYNAMIC_BLUR
import com.tx24.spicyplayer.ui.settings.ReleaseYearPosition

/** The background choices' names in the panel. */
val BACKGROUND_TYPE_LABELS: Map<BackgroundType, String> = mapOf(
    BackgroundType.Default to "Dynamic (Kawarp)",
    BackgroundType.Legacy to "Legacy",
    BackgroundType.Auto to "Artist Image",
    BackgroundType.ArtistHeader to "Artist Header",
    BackgroundType.CoverArt to "Cover Art",
    BackgroundType.Color to "Cover Colors",
)

/**
 * The panel's tabs over [settings]. Actions the platform has are passed in; a row whose action is
 * missing is left out.
 *
 * Links open through `LocalPlatformActions`.
 *
 * @param clearLyricsCache deletes every saved lyric.
 * @param version the app's version, for About.
 * @param links About's buttons, label to address.
 * @param lyricsManagerButton the page has a Lyrics Manager button to offer, under Interface → Controls.
 * @param pickLyricsFont asks for a font file and, picked, makes it the Custom lyrics font.
 */
class PanelActions(
    val clearLyricsCache: (() -> Unit)? = null,
    val version: String? = null,
    val links: List<Pair<String, String>> = emptyList(),
    val lyricsManagerButton: Boolean = false,
    val pickLyricsFont: (() -> Unit)? = null,
)

/**
 * [hiddenRows] are the ids of rows the platform leaves out because they do nothing there; a group
 * left empty goes too. The default shows every row.
 */
fun settingsPanelSections(
    settings: CommonAppSettings,
    actions: PanelActions = PanelActions(),
    hiddenRows: Set<String> = emptySet(),
): List<PanelSection> = listOf(
    lyricsSection(settings),
    backgroundSection(settings),
    playbackSection(settings),
    interfaceSection(settings, actions),
    appearanceSection(settings, actions),
    experimentsSection(settings),
    advancedSection(actions),
    PanelSection(PanelSectionId.About, "About", PanelIcons.About, secondary = true),
).map { it.without(hiddenRows) }

private fun PanelSection.without(hidden: Set<String>): PanelSection {
    if (hidden.isEmpty() || rows.none { it.id in hidden }) return this
    val kept = groups.map { group -> PanelGroup(group.title, group.rows.filter { it.id !in hidden }) }.filter { it.rows.isNotEmpty() }
    return PanelSection(id, title, icon, kept, secondary, intro, emptyText)
}

private inline fun <reified E : Enum<E>> enumSelect(
    id: String,
    label: String,
    description: String?,
    keywords: String? = null,
    noinline optionLabel: (E) -> String,
    noinline disabled: () -> String? = { null },
    noinline get: () -> E,
    noinline set: (E) -> Unit,
) = PanelRow.Select(
    id, label, description, keywords = keywords, disabled = disabled,
    options = enumValues<E>().map { it.name },
    labels = enumValues<E>().map(optionLabel),
    value = { get().name },
    onChange = { name -> enumValues<E>().firstOrNull { it.name == name }?.let(set) },
)

private fun lyricsSection(s: CommonAppSettings) = PanelSection(
    PanelSectionId.Lyrics, "Lyrics", PanelIcons.Lyrics,
    groups = listOf(
        PanelGroup("Style", listOf(
            enumSelect<LyricsStyle>(
                "lyricsStyle", "Lyrics Style",
                "How the lyrics look and move. Apple Music lifts words as they're sung, with a soft wipe, and has lines follow one after another.",
                keywords = "apple music look",
                optionLabel = { it.label }, get = { s.lyricsStyle }, set = { s.lyricsStyle = it },
            ),
            PanelRow.Toggle(
                "simpleLyricsMode", "Simple Lyrics Mode", "Drop the extra visual effects from the lyrics.",
                keywords = "effects plain",
                checked = { s.simpleLyricsMode }, onChange = { s.simpleLyricsMode = it },
            ),
            PanelRow.Toggle(
                "minimalLyricsMode", "Minimal Lyrics Mode", "Hide lines that have already been sung.",
                checked = { s.minimalLyricsMode }, onChange = { s.minimalLyricsMode = it },
            ),
            enumSelect<SyllableMerge>(
                "syllableMerge", "Join Split Words",
                "Show a word that's sung in syllables as one word. Steadily sung words only joins the words whose parts have no gap and keep an even pace.",
                keywords = "syllables merge",
                optionLabel = { it.label }, get = { s.syllableMerge }, set = { s.syllableMerge = it },
            ),
            PanelRow.Toggle(
                "originalWordMotion", "Original Word Motion",
                "The desktop amount of grow and lift on sung words. Off, they move ${CommonAppSettings.WORD_MOTION_BOOST}× as much.",
                keywords = "grow lift animation",
                checked = { s.originalWordMotion }, onChange = { s.originalWordMotion = it },
            ),
        )),
        PanelGroup("Lines", listOf(
            PanelRow.Toggle(
                "duetLinePadding", "Duet Line Padding",
                "Indent each singer's lines toward their side, so duets read as two columns.",
                keywords = "indent voices",
                checked = { s.duetLinePadding }, onChange = { s.duetLinePadding = it },
            ),
            PanelRow.Toggle(
                "creditsFollowLastLine", "Credits Follow the Last Singer",
                "When a song ends on a duet line on the right, show the songwriter and provider lines on the right too.",
                keywords = "duet credits written by songwriters provided by provider align side",
                checked = { s.creditsFollowLastLine }, onChange = { s.creditsFollowLastLine = it },
            ),
            enumSelect<PinnedFooterMode>(
                "pinnedFooter", "Pinned Lyrics Footer", "Keep source and community credits visible. Full also pins writers.",
                keywords = "credits footer",
                optionLabel = { it.label }, get = { s.pinnedFooter }, set = { s.pinnedFooter = it },
            ),
            PanelRow.Toggle(
                "textCredits", "Translation & Romanization Credits",
                "Say in the credits who translated and romanized the lyrics on screen.",
                keywords = "credits",
                checked = { s.textCredits }, onChange = { s.textCredits = it },
            ),
            PanelRow.Toggle(
                "profilesInBrowser", "Open Profiles in Browser",
                "Open contributor profiles in your browser instead of inside the app.",
                keywords = "credits contributors",
                checked = { s.profilesInBrowser }, onChange = { s.profilesInBrowser = it },
            ),
        )),
        PanelGroup("Translation and Romanization", listOf(
            enumSelect<TranslationMode>(
                "translationMode", "Translation", "Where a translated line goes.",
                keywords = "translate language",
                optionLabel = { it.label }, get = { s.translationMode }, set = { s.translationMode = it },
            ),
            enumSelect<RomanizationMode>(
                "romanizationMode", "Romanization", "Where a romanized line goes.",
                keywords = "romanize romaji pinyin",
                optionLabel = { it.label }, get = { s.romanizationMode }, set = { s.romanizationMode = it },
            ),
        )),
    ),
)

/**
 * The lyrics font: Default or System, and Custom while a font file is picked (or already chosen),
 * so a custom font is never dropped by a panel that can't pick one.
 */
internal fun lyricsFontChoices(s: CommonAppSettings): List<LyricsFont> =
    LyricsFont.entries.filter { it != LyricsFont.Custom || s.customFontFile.isNotBlank() || s.lyricsFont == LyricsFont.Custom }

private fun lyricsFontRow(s: CommonAppSettings) = PanelRow.Select(
    "lyricsFont", "Lyrics Font", "Use your system font instead of the Spicy Lyrics font, or the font you picked.",
    keywords = "system default custom font typeface",
    options = lyricsFontChoices(s).map { it.name },
    labels = lyricsFontChoices(s).map { if (it == LyricsFont.Custom) s.customFontName.ifBlank { it.label } else it.label },
    value = { s.lyricsFont.name },
    onChange = { name -> LyricsFont.entries.firstOrNull { it.name == name }?.let { s.lyricsFont = it } },
)

private fun backgroundSection(s: CommonAppSettings) = PanelSection(
    PanelSectionId.Background, "Background", PanelIcons.Background,
    groups = listOf(
        PanelGroup("Lyrics View", listOf(
            enumSelect<BackgroundType>(
                "backgroundType", "Background",
                "Dynamic (Kawarp) swirls the cover art in time with the music. Artist Image uses the artist's header photo, or the cover when there isn't one.",
                keywords = "static animated kawarp artist header cover art color colors image legacy theme",
                optionLabel = { BACKGROUND_TYPE_LABELS.getValue(it) }, get = { s.backgroundType }, set = { s.backgroundType = it },
            ),
            PanelRow.Slider(
                "backgroundBlur", "Image Blur", "Softens the Artist Image, Artist Header and Cover Art backgrounds.",
                keywords = "static background blur",
                disabled = { if (s.backgroundType.image) null else "Only applies to Artist Image, Artist Header and Cover Art." },
                spec = SliderSpec(0, MAX_BACKGROUND_BLUR, 1, default = 0, unit = "dp"),
                value = { s.backgroundBlur }, onChange = { s.backgroundBlur = it },
            ),
            PanelRow.Slider(
                "dynamicBackgroundBlur", "Dynamic Blur", "How soft the moving cover is.",
                keywords = "kawarp background blur",
                disabled = { if (s.backgroundType == BackgroundType.Default) null else "Only applies to Dynamic (Kawarp)." },
                spec = SliderSpec(0, 100, 1, default = DEFAULT_DYNAMIC_BLUR, unit = "%"),
                value = { s.dynamicBackgroundBlur }, onChange = { s.dynamicBackgroundBlur = it },
            ),
            PanelRow.Slider(
                "legacyBackgroundBlur", "Legacy Blur", "How soft the Legacy background is.",
                keywords = "background blur",
                disabled = { if (s.backgroundType == BackgroundType.Legacy) null else "Only applies to Legacy." },
                spec = SliderSpec(0, MAX_BACKGROUND_BLUR, 1, unit = "dp"),
                value = { s.legacyBackgroundBlur }, onChange = { s.legacyBackgroundBlur = it },
            ),
        )),
        PanelGroup("Motion", listOf(
            PanelRow.Toggle(
                "staticBackground", "Still Background", "Hold the background still instead of animating it.",
                keywords = "static animation",
                disabled = { if (s.backgroundType.moving) null else "Only applies to Dynamic (Kawarp) and Legacy." },
                checked = { s.staticBackground }, onChange = { s.staticBackground = it },
            ),
            PanelRow.Toggle(
                "beatReactiveBackground", "Move with the Music",
                "Speed the background up and down with the song's tempo, loudness and beats, where Spotify has them.",
                keywords = "beat tempo reactive",
                disabled = { if (s.staticBackground) "Turn off Still Background to change this." else null },
                checked = { s.beatReactiveBackground }, onChange = { s.beatReactiveBackground = it },
            ),
        )),
    ),
)

private fun playbackSection(s: CommonAppSettings) = PanelSection(
    PanelSectionId.Playback, "Playback", PanelIcons.Playback,
    groups = listOf(
        PanelGroup("Timing", listOf(
            PanelRow.Toggle(
                "seekFadeCompensation", "Seek Fade-in Compensation",
                "Clicking a line starts 300 ms before it, so the player's fade-in doesn't clip the first word. Best for rap and fast songs.",
                keywords = "click jump",
                checked = { s.seekFadeCompensation }, onChange = { s.seekFadeCompensation = it },
            ),
        )),
        PanelGroup("Scrolling", listOf(
            PanelRow.Toggle(
                "smoothScrolling", "Smooth Scrolling", "Glide from line to line instead of jumping.",
                disabled = {
                    if (s.simpleLyricsMode && s.refreshedSimpleLyrics)
                        "Refreshed Simple Lyrics Mode scrolls the lyrics its own way while Simple Lyrics Mode is on."
                    else null
                },
                checked = { s.smoothScrolling }, onChange = { s.smoothScrolling = it },
            ),
            PanelRow.Toggle(
                "scrollLeadEnabled", "Early Scroll", "Start scrolling to the next line just before it's sung.",
                keywords = "lead ahead",
                checked = { s.scrollLeadEnabled }, onChange = { s.scrollLeadEnabled = it },
            ),
            PanelRow.Slider(
                "scrollLeadMs", "Early Scroll Time", "How far ahead of the line the scroll starts.",
                keywords = "lead ahead",
                disabled = { if (s.scrollLeadEnabled) null else "Turn on Early Scroll to change this." },
                spec = SliderSpec(0, 800, 10, default = 250, unit = "ms"),
                value = { s.scrollLeadMs }, onChange = { s.scrollLeadMs = it },
            ),
            PanelRow.Toggle(
                "staticAutoScroll", "Auto-scroll Unsynced Lyrics",
                "Lyrics without timing scroll through at the song's pace, from top to bottom.",
                keywords = "unsynced static plain",
                checked = { s.staticAutoScroll }, onChange = { s.staticAutoScroll = it },
            ),
            PanelRow.Toggle(
                "showScrollToActive", "Scroll to Active Button",
                "Show an arrow when the line being sung is scrolled out of view.",
                keywords = "jump back arrow",
                checked = { s.showScrollToActive }, onChange = { s.showScrollToActive = it },
            ),
        )),
    ),
)

private fun interfaceSection(s: CommonAppSettings, actions: PanelActions) = PanelSection(
    PanelSectionId.Interface, "Interface", PanelIcons.Interface,
    groups = listOf(
        PanelGroup("Lyrics View", listOf(
            PanelRow.Toggle(
                "timelineOutsideCover", "Timeline Outside Media Box",
                "Show the playback timeline under the cover, above the song, instead of on it.",
                keywords = "progress bar seek",
                checked = { s.timelineOutsideCover }, onChange = { s.timelineOutsideCover = it },
            ),
            PanelRow.Toggle(
                "hideHeader", "Hide the Song Header", "Show only the lyrics, centred on the page, without the cover panel.",
                keywords = "now playing cover",
                checked = { s.hideHeader }, onChange = { s.hideHeader = it },
            ),
            enumSelect<HeaderSize>(
                "headerSize", "Song Header Size", "How tall the song header is. Small is a slim row.",
                keywords = "now playing",
                optionLabel = { it.label },
                disabled = { if (s.hideHeader) "Turn off Hide the Song Header to change this." else null },
                get = { s.headerSize }, set = { s.headerSize = it },
            ),
            enumSelect<ReleaseYearPosition>(
                "releaseYearPosition", "Release Year Position", "Show the release year beside the artists.",
                keywords = "date album",
                optionLabel = { it.label }, get = { s.releaseYearPosition }, set = { s.releaseYearPosition = it },
            ),
            PanelRow.Toggle(
                "expandWithoutLyrics", "Show the Cover Without Lyrics",
                "Grow the song header into the big cover when a song has no lyrics.",
                keywords = "unavailable missing big cover",
                checked = { s.expandWithoutLyrics }, onChange = { s.expandWithoutLyrics = it },
            ),
        )),
        PanelGroup("Controls", listOf(
            PanelRow.Toggle(
                "autoHideControls", "Hide Controls", "Fade the controls out a while after the pointer stops moving.",
                keywords = "auto hide buttons",
                checked = { s.autoHideControls }, onChange = { s.autoHideControls = it },
            ),
            enumSelect<ControlsHideDelay>(
                "controlsHideDelay", "Hide After", "How long the controls stay after the pointer stops.",
                keywords = "delay controls",
                optionLabel = { it.label },
                disabled = { if (s.autoHideControls) null else "Turn on Hide Controls to change this." },
                get = { s.controlsHideDelay }, set = { s.controlsHideDelay = it },
            ),
            PanelRow.Toggle(
                "hideControlsWhilePaused", "Hide While Paused",
                "Fade the controls while the song is paused too, not only while it plays.",
                keywords = "controls",
                disabled = { if (s.autoHideControls) null else "Turn on Hide Controls to change this." },
                checked = { s.hideControlsWhilePaused }, onChange = { s.hideControlsWhilePaused = it },
            ),
        ) + listOfNotNull(
            PanelRow.Toggle(
                "lyricsManagerButton", "Lyrics Manager Button", "Show a button beside the view controls that opens the Lyrics Manager.",
                keywords = "ttml upload saved lyrics library maker",
                checked = { s.lyricsManagerButton }, onChange = { s.lyricsManagerButton = it },
            ).takeIf { actions.lyricsManagerButton },
        )),
    ),
)

private fun appearanceSection(s: CommonAppSettings, actions: PanelActions) = PanelSection(
    PanelSectionId.Appearance, "Appearance", PanelIcons.Appearance,
    groups = listOf(
        PanelGroup("Text", listOfNotNull(
            lyricsFontRow(s),
            actions.pickLyricsFont?.let { pick ->
                PanelRow.Button(
                    "lyricsFontFile", "Font File", "Pick a .ttf or .otf font file for the lyrics.",
                    keywords = "custom font typeface ttf otf", text = "Choose", onClick = pick,
                )
            },
            PanelRow.Slider(
                "lyricsSizeLandscape", "Lyrics Size", "How big the lyrics are, against the window's default.",
                keywords = "text scale bigger smaller",
                spec = SliderSpec(LYRICS_SIZE_RANGE.first, LYRICS_SIZE_RANGE.last, 5, default = 100, unit = "%"),
                value = { s.lyricsSizeLandscape }, onChange = { s.lyricsSizeLandscape = it },
            ),
        )),
        PanelGroup("Effects", listOf(
            PanelRow.Toggle(
                "distanceBlur", "Blur Distant Lines", "Soften the lines further from the one being sung.",
                keywords = "blur focus",
                disabled = { if (s.lowPerformance) "Low Performance Mode turns this off." else null },
                checked = { s.distanceBlur }, onChange = { s.distanceBlur = it },
            ),
            PanelRow.Toggle(
                "glow", "Glow", "Let sung words glow.",
                disabled = { if (s.lowPerformance) "Low Performance Mode turns this off." else null },
                checked = { s.glow }, onChange = { s.glow = it },
            ),
        )),
        PanelGroup("Theme", listOf(
            enumSelect<ChromeTheme>(
                "settingsTheme", "Settings Theme", "Dress settings in Spicy Lyrics' purple, or keep the plain glass.",
                keywords = "purple classic color",
                optionLabel = { it.label }, get = { s.settingsTheme }, set = { s.settingsTheme = it },
            ),
            enumSelect<ChromeTheme>(
                "popupTheme", "Pop-up Theme", "Dress the pop-ups and notices the same way.",
                keywords = "purple classic color toast",
                optionLabel = { it.label }, get = { s.popupTheme }, set = { s.popupTheme = it },
            ),
        )),
        PanelGroup("Performance", listOf(
            PanelRow.Toggle(
                "lowPerformance", "Low Performance Mode",
                "Stills the background and cover and turns off blur and glow, for smoother lyrics on slower computers.",
                keywords = "fps cpu gpu smooth",
                checked = { s.lowPerformance }, onChange = { s.lowPerformance = it },
            ),
            PanelRow.Toggle(
                "uiAnimations", "Interface Animations", "Animate the playback buttons and pop-ups.",
                keywords = "motion",
                disabled = { if (s.lowPerformance) "Low Performance Mode turns this off." else null },
                checked = { s.uiAnimations }, onChange = { s.uiAnimations = it },
            ),
        )),
    ),
)

private fun experimentsSection(s: CommonAppSettings) = PanelSection(
    PanelSectionId.Experiments, "Experiments", PanelIcons.Experiments,
    intro = "Features still being shaped. Turn one off if you prefer how things worked before.",
    emptyText = "No experiments right now.",
    groups = listOf(
        PanelGroup(null, listOf(
            PanelRow.Toggle(
                "refinedBackground", "Refined Background",
                "Background changes crossfade in place, including switching styles in Settings, and Cover Colors use the cover's deeper tones. Image backgrounds keep their edges instead of being cropped. Disable to bring back the sliding image transition and the brighter Cover Colors.",
                keywords = "experiment",
                checked = { s.refinedBackground }, onChange = { s.refinedBackground = it },
            ),
            PanelRow.Toggle(
                "refreshedSimpleLyrics", "Refreshed Simple Lyrics Mode",
                "In Simple Lyrics Mode, the sung part of a line fades in behind one soft edge, words float up as they're sung, and lines follow the song in a ripple. Its scrolling takes over from Smooth Scrolling while it's on. Disable to bring back the previous Simple Lyrics look.",
                keywords = "experiment",
                checked = { s.refreshedSimpleLyrics }, onChange = { s.refreshedSimpleLyrics = it },
            ),
            PanelRow.Toggle(
                "refinedCredits", "Refined Credits",
                "The credits under the lyrics become quiet type close under the last line, with no box: the songwriters, then the lyrics source beneath. Disable to bring back the previous credit lines.",
                keywords = "experiment",
                checked = { s.refinedCredits }, onChange = { s.refinedCredits = it },
            ),
            PanelRow.Toggle(
                "backgroundLinePop", "Background Vocal Pop-Out",
                "Background vocal lines stay tucked away under their line until it's sung, then slide out from beneath it. Only applies in Simple Lyrics Mode with Refreshed Simple Lyrics Mode on.",
                key = "backgroundPop",
                keywords = "experiment",
                checked = { s.backgroundPop }, onChange = { s.backgroundPop = it },
            ),
            PanelRow.Toggle(
                "keepOverlappingLines", "Keep Overlapping Lines",
                "In Minimal Lyrics Mode, a line that ends while one above it is still being sung stays on screen, dimmed, instead of fading out early.",
                keywords = "experiment",
                checked = { s.keepOverlappingLines }, onChange = { s.keepOverlappingLines = it },
            ),
        )),
    ),
)

private fun advancedSection(actions: PanelActions) = PanelSection(
    PanelSectionId.Advanced, "Advanced", PanelIcons.Advanced,
    secondary = true,
    groups = listOfNotNull(
        actions.clearLyricsCache?.let { clear ->
            PanelGroup("Lyrics Cache", listOf(
                PanelRow.Button(
                    "clearStoredLyricsCache", "Clear Stored Lyrics", "Delete all saved lyrics.",
                    keywords = "clear lyrics cache", text = "Clear", onClick = clear,
                ),
            ))
        },
    ),
)
