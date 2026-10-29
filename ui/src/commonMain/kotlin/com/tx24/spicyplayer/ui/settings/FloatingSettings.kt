package com.tx24.spicyplayer.ui.settings

import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeInputScale
import com.tx24.spicyplayer.ui.theme.LocalSpicyPalette
import com.tx24.spicyplayer.ui.components.drawBrandMark
import com.tx24.spicyplayer.ui.components.drawBrandRamp
import com.tx24.spicyplayer.ui.components.rememberBrandGlyph
import com.tx24.spicyplayer.ui.components.SpicyBrandLine
import com.tx24.spicyplayer.ui.components.modalShadow
import com.tx24.spicyplayer.ui.components.rowCards
import com.tx24.spicyplayer.ui.components.LocalRowCards
import com.tx24.spicyplayer.ui.components.RowCards
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.tx24.spicyplayer.ui.components.LocalRowIcons
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
    val backdrop = LocalSettingsBackdrop.current
    val brand = LocalSpicyPalette.current.brand
    val glyph = rememberBrandGlyph()
    val cards = remember { RowCards() }
    BoxWithConstraints(
        Modifier.fillMaxSize()
            // A click beside the panel closes it.
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose)
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        // A fixed size, so switching pages never resizes it, and the screen shows around it.
        val width = minOf(PANEL_MAX_WIDTH, maxWidth * PANEL_WIDTH_SHARE)
        val height = minOf(PANEL_MAX_HEIGHT, maxHeight * PANEL_HEIGHT_SHARE)
        Row(
            Modifier
                .size(width, height)
                .modalShadow()
                .clip(PANEL_SHAPE)
                // Glass: the lyrics blurred behind it, under the theme's tint, its ramp and the mark.
                .then(
                    if (backdrop != null) Modifier.hazeEffect(backdrop, panelGlass(brand?.fieldDeep)) { inputScale = HazeInputScale.Fixed(BACKDROP_INPUT_SCALE) }
                    else Modifier.background(PANEL_FILL),
                )
                .drawBehind {
                    brand ?: return@drawBehind
                    drawBrandRamp(brand, fromAlpha = 0.18f, toAlpha = 0.32f)
                    val markWidth = size.width * 0.45f
                    drawBrandMark(glyph, brand.tint, alpha = 0.16f, width = markWidth, right = -markWidth * 0.3f, top = -markWidth * 0.35f, degrees = -14f)
                }
                .border(1.dp, SpicyColors.Hairline, PANEL_SHAPE)
                // The panel takes its clicks; one beside it closes.
                .clickable(remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Column(
                Modifier.width(SIDEBAR_WIDTH).fillMaxHeight().background(SIDEBAR_FILL)
                    .padding(start = 12.dp, end = 12.dp, top = 22.dp, bottom = SpicySpacing.S4),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                SpicyBrandLine(Modifier.padding(start = 10.dp, bottom = 2.dp), name = host.appName)
                Text(
                    "Settings",
                    style = PANEL_TITLE,
                    modifier = Modifier.padding(start = 10.dp, bottom = SpicySpacing.S3),
                )
                SpicySearchBar(query, onQueryChange, Modifier.fillMaxWidth().padding(bottom = SpicySpacing.S2))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    pages.forEach { page ->
                        SidebarItem(pageIcon(page, host), page.title, selected = query.isBlank() && (selected == page || selected?.parent == page)) {
                            onQueryChange("")
                            selected = page
                        }
                    }
                    Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = SpicySpacing.S2).height(1.dp).background(SpicyColors.Hairline))
                    SidebarItem(Icons.Rounded.Info, "About", selected = query.isBlank() && selected == null) {
                        onQueryChange("")
                        selected = null
                    }
                }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(SpicyColors.Hairline))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                // A new scroll for each page, so a page opens at its top.
                val scroll = key(query.isBlank(), selected) { rememberScrollState() }
                val hairline = SpicyColors.Hairline
                Row(
                    Modifier.fillMaxWidth()
                        .drawBehind {
                            // No divider at rest; a hairline once the page scrolls beneath.
                            if (scroll.value > 0) drawRect(hairline, Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx()))
                        }
                        .padding(start = 32.dp, end = 18.dp, top = 22.dp, bottom = 14.dp)
                        .heightIn(min = 34.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        when {
                            query.isNotBlank() -> "Search"
                            else -> selected?.path ?: "About"
                        },
                        style = PANEL_TITLE,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    CloseButton(onClose)
                }
                // A page opened from a row on another shows in place of it.
                CompositionLocalProvider(LocalOpenSettingsPage provides { selected = it }, LocalRowIcons provides false, LocalRowCards provides cards) {
                    key(query.isBlank(), selected) {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(scroll)
                                .padding(start = 32.dp, end = 32.dp, top = SpicySpacing.S2, bottom = 32.dp)
                                .rowCards(cards, fill = SpicyColors.TintBg, line = SpicyColors.Hairline),
                        ) {
                            when {
                                query.isNotBlank() -> SearchResults(query, state, host, settings, onOpenLyricsManager, onOpenSpotifySearch)
                                selected == null -> host.AboutCard()
                                else -> PageContent(selected!!, state, host, settings, onOpenLyricsManager, onOpenSpotifySearch)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The pane's close button: a small tinted pill. */
@Composable
private fun CloseButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        Modifier.size(30.dp).clip(RoundedCornerShape(50))
            .background(if (hovered) SpicyColors.TintBgPressed else SpicyColors.TintBg)
            .hoverable(interaction)
            .focusRing(RoundedCornerShape(50))
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Close, "Close settings", tint = if (hovered) SpicyColors.TextPrimary else SpicyColors.TextSecondary, modifier = Modifier.size(18.dp))
    }
}

/** A page in the side list: its glyph and name, lit while it's the one shown. */
@Composable
private fun SidebarItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    // The selection swaps at once: pages are switched too often for it to travel. Hover still eases.
    val hover by animateColorAsState(
        if (pressed || hovered) SpicyColors.TintBg else Color.Transparent,
        tween(SpicyMotion.FAST_MS, easing = SpicyMotion.Standard),
        label = "sidebarItem",
    )
    val fill = if (selected) SpicyColors.TintBgPressed else hover
    Row(
        Modifier
            .fillMaxWidth()
            .clip(ITEM_SHAPE)
            .background(fill)
            .focusRing(ITEM_SHAPE)
            .hoverable(interaction)
            .clickable(interaction, indication = null, role = Role.Tab, onClick = onClick)
            .height(ITEM_HEIGHT)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) SpicyColors.TextPrimary else SpicyColors.TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(10.dp))
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

/** The panel's titles: 24, bold, set a little tight. */
private val PANEL_TITLE @Composable get() = SpicyType.Title.copy(fontSize = 24.sp, lineHeight = 1.15.em, letterSpacing = (-0.025).em)

private val PANEL_MAX_WIDTH = 940.dp
private val PANEL_MAX_HEIGHT = 700.dp
private const val PANEL_WIDTH_SHARE = 0.94f
private const val PANEL_HEIGHT_SHARE = 0.88f
private val SIDEBAR_WIDTH = 216.dp
private val ITEM_HEIGHT = 34.dp
private val PANEL_SHAPE = RoundedCornerShape(SpicyRadii.Lg)
private val ITEM_SHAPE = RoundedCornerShape(SpicyRadii.Md)
private val PANEL_FILL = Color(0xF2141418)
// A heavier, flat tint than the page beside it: structure, not a surface to read.
private val SIDEBAR_FILL = Color.Black.copy(alpha = 0.22f)

/** The panel's glass: a strong blur under the theme's deep colour (or plain dark), most of the way opaque. */
private fun panelGlass(deep: Color?) = HazeStyle(
    backgroundColor = Color.Black,
    tints = listOf(HazeTint((deep ?: Color(0xFF16161A)).copy(alpha = 0.78f))),
    blurRadius = 40.dp,
    noiseFactor = 0f,
    fallbackTint = HazeTint(PANEL_FILL),
)
