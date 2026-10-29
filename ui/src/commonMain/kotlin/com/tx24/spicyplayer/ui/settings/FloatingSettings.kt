package com.tx24.spicyplayer.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tx24.spicyplayer.ui.components.GlassButton
import com.tx24.spicyplayer.ui.components.SpicySearchBar
import com.tx24.spicyplayer.ui.components.focusRing
import com.tx24.spicyplayer.ui.theme.SpicyColors
import com.tx24.spicyplayer.ui.theme.SpicyMotion
import com.tx24.spicyplayer.ui.theme.SpicyRadii
import com.tx24.spicyplayer.ui.theme.SpicySpacing
import com.tx24.spicyplayer.ui.theme.SpicyType

/** The narrowest window that gets [FloatingSettings] instead of the full-screen pages. */
val FLOATING_SETTINGS_MIN_WIDTH = 760.dp

/**
 * Settings for a wide window: one panel floating over the lyrics, the pages listed down its side
 * and the chosen one beside them, with the search over the list. The same pages as the
 * full-screen settings; a page that opens another ([SubPageRow]) shows it in place.
 */
@Composable
internal fun FloatingSettings(
    state: SettingsState,
    host: SettingsHost,
    settings: CommonAppSettings,
    query: String,
    onQueryChange: (String) -> Unit,
    contentPadding: PaddingValues,
    onClose: () -> Unit,
    onOpenLyricsManager: () -> Unit,
    onOpenSpotifySearch: () -> Unit,
) {
    val pages = remember(host) { SettingsPage.entries.filter { it.parent == null && host.hasPage(it) } }
    // Null for the About card, the list's last entry.
    var selected by rememberSaveable { mutableStateOf<SettingsPage?>(pages.firstOrNull()) }
    Box(
        Modifier.fillMaxSize()
            // A click beside the panel closes it.
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose)
            .padding(contentPadding).padding(SpicySpacing.S6),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .widthIn(max = PANEL_MAX_WIDTH)
                .fillMaxWidth()
                .heightIn(max = PANEL_MAX_HEIGHT)
                .fillMaxHeight()
                .clip(PANEL_SHAPE)
                .background(PANEL_FILL)
                .border(1.dp, SpicyColors.Hairline, PANEL_SHAPE)
                // The panel takes its clicks; one beside it closes.
                .clickable(remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Column(
                Modifier.width(SIDEBAR_WIDTH).fillMaxHeight().background(SIDEBAR_FILL).padding(SpicySpacing.S3),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    "Settings",
                    style = SpicyType.Headline,
                    modifier = Modifier.padding(start = SpicySpacing.S2, top = SpicySpacing.S2, bottom = SpicySpacing.S2),
                )
                SpicySearchBar(query, onQueryChange, Modifier.fillMaxWidth().padding(bottom = SpicySpacing.S2))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    pages.forEach { page ->
                        SidebarItem(pageIcon(page, host), page.title, selected = query.isBlank() && (selected == page || selected?.parent == page)) {
                            onQueryChange("")
                            selected = page
                        }
                    }
                }
                SidebarItem(Icons.Rounded.Info, "About", selected = query.isBlank() && selected == null) {
                    onQueryChange("")
                    selected = null
                }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(SpicyColors.Hairline))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = SpicySpacing.S5, end = SpicySpacing.S3, top = SpicySpacing.S3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        when {
                            query.isNotBlank() -> "Search"
                            else -> selected?.path ?: "About"
                        },
                        style = SpicyType.Headline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    GlassButton(onClick = onClose, contentDescription = "Close settings", size = 36.dp) {
                        Icon(Icons.Rounded.Close, null, tint = SpicyColors.TextPrimary, modifier = Modifier.size(20.dp))
                    }
                }
                // A page opened from a row on another shows in place of it.
                CompositionLocalProvider(LocalOpenSettingsPage provides { selected = it }) {
                    key(query.isBlank(), selected) {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                .padding(horizontal = SpicySpacing.S5, vertical = SpicySpacing.S3),
                        ) {
                            when {
                                query.isNotBlank() -> SearchResults(query, state, host, settings, onOpenLyricsManager, onOpenSpotifySearch)
                                selected == null -> host.AboutCard()
                                else -> PageContent(selected!!, state, host, settings, onOpenLyricsManager, onOpenSpotifySearch)
                            }
                            Spacer(Modifier.height(SpicySpacing.S6))
                        }
                    }
                }
            }
        }
    }
}

/** A page in the side list: its glyph and name, lit while it's the one shown. */
@Composable
private fun SidebarItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val fill by animateColorAsState(
        when {
            selected -> SpicyColors.TintBgPressed
            pressed || hovered -> SpicyColors.TintBg
            else -> Color.Transparent
        },
        tween(SpicyMotion.FAST_MS, easing = SpicyMotion.Standard),
        label = "sidebarItem",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(ITEM_SHAPE)
            .background(fill)
            .focusRing(ITEM_SHAPE)
            .hoverable(interaction)
            .clickable(interaction, indication = null, role = Role.Tab, onClick = onClick)
            .padding(horizontal = SpicySpacing.S3, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) SpicyColors.TextPrimary else SpicyColors.TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(SpicySpacing.S3))
        Text(
            label,
            style = SpicyType.Body.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The glyph each page has on the first page of the full-screen settings. */
private fun pageIcon(page: SettingsPage, host: SettingsHost): ImageVector = when (page) {
    SettingsPage.ThisSong -> Icons.Rounded.MusicNote
    SettingsPage.Lyrics -> Icons.Rounded.TextFields
    SettingsPage.Translation -> Icons.Rounded.Translate
    SettingsPage.ScrollSync -> Icons.Rounded.Timer
    SettingsPage.Background -> Icons.Rounded.Palette
    SettingsPage.NowPlaying -> Icons.Rounded.Album
    SettingsPage.Controls -> Icons.Rounded.TouchApp
    SettingsPage.Theme -> Icons.Rounded.ColorLens
    SettingsPage.Sources -> Icons.Rounded.Layers
    SettingsPage.Device -> if (host.phoneDevice) Icons.Rounded.Smartphone else Icons.Rounded.Computer
    SettingsPage.FloatingLyrics -> Icons.Rounded.PictureInPicture
    SettingsPage.Advanced -> Icons.Rounded.Tune
}

private val PANEL_MAX_WIDTH = 980.dp
private val PANEL_MAX_HEIGHT = 720.dp
private val SIDEBAR_WIDTH = 240.dp
private val PANEL_SHAPE = RoundedCornerShape(SpicyRadii.Lg)
private val ITEM_SHAPE = RoundedCornerShape(SpicyRadii.Md)
private val PANEL_FILL = Color(0xF2141418)
private val SIDEBAR_FILL = Color.White.copy(alpha = 0.03f)
