package com.tx24.spicyplayer.ui.bigscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tx24.spicyplayer.lyrics.spicy.romanization.RomanizationMode
import com.tx24.spicyplayer.translation.TranslationMode
import com.tx24.spicyplayer.ui.theme.SpicyColors

/** Which of the More menu's entries is open. */
internal enum class MoreEntry { Source, Spotify, Translation, Romanization }

/**
 * The More menu's entries: the lyrics source, the Spotify match, translation, and romanization style, each opening
 * in place onto its choices. Without [actions] they show as unavailable.
 */
@Composable
internal fun MoreMenuEntries(actions: LyricsActions?) {
    if (actions == null) {
        for (entry in listOf("Lyrics source", "Spotify match", "Translate lyrics", "Romanization style")) {
            EntryHeader(entry, "Not available here", open = false, enabled = false) {}
        }
        return
    }
    val options by actions.options.collectAsState()
    var open by remember { mutableStateOf<MoreEntry?>(null) }
    fun toggle(entry: MoreEntry) { open = if (open == entry) null else entry }

    EntryHeader("Lyrics source", sourceSummary(options), open == MoreEntry.Source) { toggle(MoreEntry.Source) }
    if (open == MoreEntry.Source) {
        Choices {
            for (row in sourceRows(options)) {
                Choice(row.title, row.detail, row.selected, row.enabled) { actions.useSource(row.id) }
            }
        }
    }

    EntryHeader("Spotify match", spotifySummary(options), open == MoreEntry.Spotify) { toggle(MoreEntry.Spotify) }
    if (open == MoreEntry.Spotify) SpotifyLinkField(options, actions)

    EntryHeader("Translate lyrics", translationSummary(options), open == MoreEntry.Translation) { toggle(MoreEntry.Translation) }
    if (open == MoreEntry.Translation) {
        Choice("Translate", if (options.translating) "On" else "Off", selected = options.translating) {
            actions.setTranslating(!options.translating)
        }
        for (mode in TranslationMode.entries) {
            Choice(mode.label, null, selected = options.translationMode == mode) { actions.setTranslationMode(mode) }
        }
        SectionLabel("Into")
        Choices {
            for (language in options.languages) {
                Choice(language.name, null, selected = options.targetLanguage == language.code) { actions.setTargetLanguage(language.code) }
            }
        }
    }

    EntryHeader("Romanization style", romanizationSummary(options), open == MoreEntry.Romanization) { toggle(MoreEntry.Romanization) }
    if (open == MoreEntry.Romanization) {
        for (mode in RomanizationMode.entries) {
            Choice(mode.label, null, selected = options.romanizationMode == mode) { actions.setRomanizationMode(mode) }
        }
    }

    options.message?.let { message ->
        BasicText(
            message,
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            style = TextStyle(color = SpicyColors.TextSecondary, fontSize = 12.sp),
        )
    }
}

/** Paste the song's Spotify track link when the automatic match is wrong or missing. */
@Composable
private fun SpotifyLinkField(options: LyricsOptions, actions: LyricsActions) {
    var link by remember { mutableStateOf("") }
    SectionLabel("Spotify track link or ID")
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 14.dp, top = 2.dp, bottom = 4.dp)
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        if (link.isEmpty()) {
            BasicText("open.spotify.com/track/…", style = TextStyle(color = SpicyColors.TextTertiary, fontSize = 13.sp))
        }
        BasicTextField(
            value = link,
            onValueChange = { link = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = TextStyle(color = SpicyColors.TextPrimary, fontSize = 13.sp),
            cursorBrush = SolidColor(SpicyColors.TextPrimary),
        )
    }
    Choice("Use this track", "Looks the lyrics up again with it", selected = false, enabled = link.isNotBlank()) {
        if (actions.setSpotifyLink(link)) link = ""
    }
    if (options.spotifyTrackId != null) {
        Choice("Match automatically", "Forget the track set by hand", selected = false) { actions.clearSpotifyLink() }
    }
}

@Composable
private fun EntryHeader(title: String, summary: String, open: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            val color = if (enabled) SpicyColors.TextPrimary else SpicyColors.TextTertiary
            BasicText(title, style = TextStyle(color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
            BasicText(summary, style = TextStyle(color = SpicyColors.TextTertiary, fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (enabled) BasicText(if (open) "−" else "+", style = TextStyle(color = SpicyColors.TextSecondary, fontSize = 16.sp))
    }
}

/** A long list of choices, scrolled within the menu. */
@Composable
private fun Choices(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState())) { content() }
}

@Composable
private fun SectionLabel(text: String) {
    BasicText(
        text,
        Modifier.padding(start = 24.dp, top = 6.dp, bottom = 2.dp),
        style = TextStyle(color = SpicyColors.TextTertiary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
    )
}

@Composable
private fun Choice(title: String, detail: String?, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(start = 24.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            val color = when {
                !enabled -> SpicyColors.TextTertiary
                selected -> SpicyColors.TextPrimary
                else -> SpicyColors.TextSecondary
            }
            BasicText(title, style = TextStyle(color = color, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal))
            detail?.let { BasicText(it, style = TextStyle(color = SpicyColors.TextTertiary, fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (selected) BasicText("✓", Modifier.width(16.dp), style = TextStyle(color = SpicyColors.TextPrimary, fontSize = 13.sp))
    }
}
