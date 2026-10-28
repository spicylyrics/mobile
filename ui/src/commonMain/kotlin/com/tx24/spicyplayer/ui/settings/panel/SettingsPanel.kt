package com.tx24.spicyplayer.ui.settings.panel

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.tx24.spicyplayer.ui.components.ChromeTheme
import com.tx24.spicyplayer.ui.components.drawBrandMark
import com.tx24.spicyplayer.ui.components.drawBrandRamp
import com.tx24.spicyplayer.ui.components.rememberBrandGlyph
import com.tx24.spicyplayer.ui.settings.LocalPlatformActions
import com.tx24.spicyplayer.ui.theme.LocalSpicyPalette
import com.tx24.spicyplayer.ui.theme.SpicyColors
import com.tx24.spicyplayer.ui.theme.SpicyRadii
import com.tx24.spicyplayer.ui.theme.SpicySpacing
import com.tx24.spicyplayer.ui.theme.SpicyType

/**
 * The big-screen settings panel: a sidebar of tabs with search beside a pane of grouped cards, or,
 * narrower than [SINGLE_PANE_BELOW], the section list and then one section at a time with a back
 * button ([panelLayoutFor]). [theme] dresses it, in the brand's ramp or the plain plate. Links open
 * through `LocalPlatformActions`. Escape clears the search, then goes back, then closes
 * ([onClose]); Ctrl+F jumps to the search.
 */
@Composable
fun SettingsPanel(
    sections: List<PanelSection>,
    theme: ChromeTheme,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    actions: PanelActions = PanelActions(),
) {
    val palette = theme.palette
    CompositionLocalProvider(LocalSpicyPalette provides palette) {
        // Null is the section list, which only a single pane shows; two panes always have a tab.
        var tab by remember { mutableStateOf<PanelSectionId?>(null) }
        var query by remember { mutableStateOf("") }
        val searchFocus = remember { FocusRequester() }
        val searching = searchTerms(query).isNotEmpty()
        val hits = if (searching) searchPanel(query, sections) else null
        val brand = palette.brand
        val glyph = rememberBrandGlyph()
        val select: (PanelSectionId) -> Unit = { tab = it; query = "" }

        LaunchedEffect(Unit) { runCatching { searchFocus.requestFocus() } }

        BoxWithConstraints(
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
            val layout = panelLayoutFor(maxWidth)
            val back: (() -> Unit)? = when {
                layout != PanelLayout.SinglePane -> null
                query.isNotEmpty() -> ({ query = "" })
                tab != null -> ({ tab = null })
                else -> null
            }
            Box(
                Modifier.fillMaxSize().onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        event.key == Key.Escape -> {
                            when {
                                query.isNotEmpty() -> query = ""
                                back != null -> back()
                                else -> onClose()
                            }
                            true
                        }
                        event.key == Key.F && (event.isCtrlPressed || event.isMetaPressed) -> {
                            runCatching { searchFocus.requestFocus() }
                            true
                        }
                        else -> false
                    }
                },
            ) {
                val section = sections.firstOrNull { it.id == tab } ?: sections.first()
                when {
                    layout != PanelLayout.SinglePane -> Row(Modifier.fillMaxSize()) {
                        Sidebar(
                            sections, section.id, hits, query, { query = it }, searchFocus, select,
                            SidebarStyle.Rail(layout.railWidth, brand?.fieldDeep ?: Color.Black),
                        )
                        Pane(section, hits, query, actions, select, onClose, back = null, gutter = layout.gutter)
                    }
                    // One pane: the section list, with the search, until a tab or a search is open.
                    tab == null && hits == null ->
                        Sidebar(sections, null, null, query, { query = it }, searchFocus, select, SidebarStyle.Page, onClose)
                    else -> Pane(section, hits, query, actions, select, onClose, back = back, gutter = layout.gutter)
                }
            }
        }
    }
}

private val ROW_HEIGHT = 34.dp
private val SmallShape = RoundedCornerShape(SpicyRadii.Sm)
private val CardShape = RoundedCornerShape(SpicyRadii.Md)

internal val titleStyle @Composable get() = SpicyType.Body.copy(
    fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 1.15.em, letterSpacing = (-0.025).em,
)

/** How the section list sits: a tinted rail beside the pane, or the whole page on its own. */
private sealed interface SidebarStyle {
    class Rail(val width: Dp, val deep: Color) : SidebarStyle
    data object Page : SidebarStyle
}

@Composable
private fun Sidebar(
    sections: List<PanelSection>,
    selected: PanelSectionId?,
    hits: List<SearchHit>?,
    query: String,
    onQuery: (String) -> Unit,
    searchFocus: FocusRequester,
    onSelect: (PanelSectionId) -> Unit,
    style: SidebarStyle,
    onClose: (() -> Unit)? = null,
) {
    val hairline = SpicyColors.Hairline
    val frame = when (style) {
        is SidebarStyle.Rail -> Modifier
            .width(style.width)
            .fillMaxHeight()
            .background(style.deep.copy(alpha = 0.42f))
            .drawBehind { drawRect(hairline, topLeft = Offset(size.width - 1.dp.toPx(), 0f), size = Size(1.dp.toPx(), size.height)) }
        SidebarStyle.Page -> Modifier.fillMaxSize()
    }
    Column(
        frame
            .verticalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 12.dp, top = 22.dp, bottom = SpicySpacing.S4),
        verticalArrangement = Arrangement.spacedBy(SpicySpacing.S4),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                BrandLine()
                Spacer(Modifier.height(10.dp))
                BasicText("Settings", style = titleStyle)
            }
            onClose?.let { RoundButton(PanelIcons.Close, 12.dp, it) }
        }
        SearchBar(query, onQuery, searchFocus)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            sections.filter { !it.secondary }.forEach { Tab(it, selected, hits, onSelect) }
            Box(Modifier.padding(horizontal = 10.dp, vertical = SpicySpacing.S2).fillMaxWidth().height(1.dp).background(SpicyColors.Hairline))
            sections.filter { it.secondary }.forEach { Tab(it, selected, hits, onSelect) }
        }
    }
}

@Composable
private fun BrandLine() {
    val glyph = rememberBrandGlyph()
    val color = SpicyColors.TextSecondary
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Image(glyph, null, Modifier.size(16.dp), colorFilter = ColorFilter.tint(color))
        BasicText("Spicy Lyrics", style = SpicyType.Caption.copy(color = color, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp))
    }
}

@Composable
internal fun SearchBar(query: String, onQuery: (String) -> Unit, focus: FocusRequester, placeholder: String = "Search settings") {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    var focused by remember { mutableStateOf(false) }
    val tint = if (hovered || focused) SpicyColors.TintBgPressed else SpicyColors.TintBg
    val ring = if (focused) SpicyColors.HairlineStrong else SpicyColors.Hairline
    Row(
        Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .clip(SmallShape)
            .background(tint)
            .border(1.dp, ring, SmallShape)
            .hoverable(hover)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Glyph(PanelIcons.Search, SpicyColors.TextTertiary, 14.dp)
        val style = SpicyType.Body.copy(color = SpicyColors.TextPrimary)
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(SpicyColors.TextPrimary),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focus)
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) BasicText(placeholder, style = style.copy(color = SpicyColors.TextSecondary), maxLines = 1)
                    field()
                }
            },
        )
        if (query.isNotEmpty()) {
            Box(Modifier.clickable { onQuery("") }.pointerHoverIcon(PointerIcon.Hand)) {
                Glyph(PanelIcons.Clear, SpicyColors.TextTertiary, 10.dp)
            }
        }
    }
}

@Composable
private fun Tab(section: PanelSection, selected: PanelSectionId?, hits: List<SearchHit>?, onSelect: (PanelSectionId) -> Unit) {
    val count = hits?.let { h -> h.firstOrNull { it.section.id == section.id }?.rows?.size ?: 0 }
    val active = hits == null && section.id == selected
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val background = when {
        active -> SpicyColors.TintBgPressed
        hovered -> SpicyColors.TintBg
        else -> Color.Transparent
    }
    val ink = if (active || hovered) SpicyColors.TextPrimary else SpicyColors.TextSecondary
    Row(
        Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .alpha(if (count == 0) 0.45f else 1f)
            .clip(SmallShape)
            .background(background)
            .hoverable(hover)
            .clickable(interactionSource = hover, indication = null) { onSelect(section.id) }
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Glyph(section.icon, ink, 16.dp)
        BasicText(
            section.title,
            style = SpicyType.Body.copy(color = ink, fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (count != null && count > 0) {
            BasicText(
                count.toString(),
                style = SpicyType.Footnote.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(SpicyColors.TintBgPressed)
                    .widthIn(min = 20.dp)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun Pane(
    section: PanelSection,
    hits: List<SearchHit>?,
    query: String,
    actions: PanelActions,
    onSelect: (PanelSectionId) -> Unit,
    onClose: () -> Unit,
    back: (() -> Unit)?,
    gutter: Dp,
) {
    val scroll = rememberScrollState()
    LaunchedEffect(section.id, hits == null) { scroll.scrollTo(0) }
    Column(Modifier.fillMaxSize()) {
        val hairline = SpicyColors.Hairline
        val scrolled = scroll.value > 0
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = ROW_HEIGHT)
                .drawBehind { if (scrolled) drawRect(hairline, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = Size(size.width, 1.dp.toPx())) }
                .padding(start = if (back != null) 12.dp else gutter, end = 18.dp, top = 22.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S3),
        ) {
            back?.let { RoundButton(PanelIcons.Chevron, 10.dp, it, mirrored = true) }
            BasicText(
                if (hits != null) "Results" else section.title,
                style = titleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (hits != null) {
                val n = hits.sumOf { it.rows.size }
                BasicText(if (n == 1) "1 setting" else "$n settings", style = SpicyType.Caption.copy(color = SpicyColors.TextSecondary))
            }
            Spacer(Modifier.weight(1f))
            RoundButton(PanelIcons.Close, 12.dp, onClose)
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(start = gutter, end = gutter, top = SpicySpacing.S2, bottom = 32.dp),
        ) {
            androidx.compose.runtime.key(if (hits != null) "search" else section.id.name) {
                val shown = remember { Animatable(0f) }
                LaunchedEffect(Unit) { shown.animateTo(1f, tween(140)) }
                Column(Modifier.graphicsLayer { alpha = shown.value }) {
                    when {
                        hits != null -> SearchResults(hits, query, onSelect)
                        section.id == PanelSectionId.About -> About(actions)
                        else -> SectionBody(section)
                    }
                }
            }
        }
    }
}

/** A 30dp round tinted button: close, and back on a single pane ([mirrored] turns the chevron). */
@Composable
internal fun RoundButton(icon: ImageVector, iconSize: Dp, onClick: () -> Unit, mirrored: Boolean = false) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(if (hovered) SpicyColors.TintBgPressed else SpicyColors.TintBg)
            .hoverable(hover)
            .clickable(interactionSource = hover, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.graphicsLayer { if (mirrored) rotationZ = 180f }) {
            Glyph(icon, if (hovered) SpicyColors.TextPrimary else SpicyColors.TextSecondary, iconSize)
        }
    }
}

@Composable
private fun SectionBody(section: PanelSection) {
    section.intro?.let {
        BasicText(it, style = SpicyType.Caption.copy(color = SpicyColors.TextSecondary, lineHeight = 1.45.em), modifier = Modifier.widthIn(max = 520.dp))
        Spacer(Modifier.height(20.dp))
    }
    val groups = section.groups.filter { it.rows.isNotEmpty() }
    if (groups.isEmpty()) {
        Empty(section.emptyText, null)
        return
    }
    groups.forEachIndexed { i, group ->
        if (i > 0) Spacer(Modifier.height(24.dp))
        group.title?.let { GroupTitle(it) }
        Card(group.rows, "")
    }
}

@Composable
private fun SearchResults(hits: List<SearchHit>, query: String, onSelect: (PanelSectionId) -> Unit) {
    if (hits.isEmpty()) {
        Empty("No results for “${query.trim()}”", "Check the spelling, or try a shorter word.")
        return
    }
    hits.forEachIndexed { i, hit ->
        if (i > 0) Spacer(Modifier.height(24.dp))
        val hover = remember { MutableInteractionSource() }
        val hovered by hover.collectIsHoveredAsState()
        Row(
            Modifier
                .hoverable(hover)
                .clickable(interactionSource = hover, indication = null) { onSelect(hit.section.id) }
                .pointerHoverIcon(PointerIcon.Hand),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val ink = if (hovered) SpicyColors.TextPrimary else SpicyColors.TextSecondary
            GroupTitle(hit.section.title, ink)
            Box(Modifier.padding(bottom = 8.dp).graphicsLayer { translationX = if (hovered) 2.dp.toPx() else 0f }) {
                Glyph(PanelIcons.Chevron, ink, 10.dp)
            }
        }
        Card(hit.rows, query)
    }
}

@Composable
private fun GroupTitle(text: String, color: Color = SpicyColors.TextSecondary) {
    BasicText(
        text,
        style = SpicyType.Caption.copy(color = color, fontWeight = FontWeight.SemiBold, letterSpacing = 0.01.em),
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun Empty(title: String, hint: String?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 56.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(title, style = SpicyType.Body.copy(fontWeight = FontWeight.SemiBold))
        hint?.let {
            Spacer(Modifier.height(4.dp))
            BasicText(it, style = SpicyType.Caption.copy(color = SpicyColors.TextSecondary))
        }
    }
}

@Composable
private fun Card(rows: List<PanelRow>, query: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(SpicyColors.TintBg)
            .border(1.dp, SpicyColors.Hairline, CardShape),
    ) {
        rows.forEachIndexed { i, row -> SettingRow(row, query, divider = i > 0) }
    }
}

@Composable
private fun SettingRow(row: PanelRow, query: String, divider: Boolean) {
    val reason = row.disabled()
    val note = row.note()
    val disabled = reason != null
    val dim = if (disabled) 0.45f else 1f
    val hairline = SpicyColors.Hairline
    val rowModifier = Modifier
        .fillMaxWidth()
        .drawBehind { if (divider) drawRect(hairline, topLeft = Offset(16.dp.toPx(), 0f), size = Size(size.width - 16.dp.toPx(), 1.dp.toPx())) }
        .padding(horizontal = 16.dp, vertical = 13.dp)
    val labels: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            BasicText(
                highlighted(row.label, query),
                style = SpicyType.Body.copy(fontWeight = FontWeight.Medium, lineHeight = 1.3.em),
                modifier = Modifier.alpha(dim),
            )
            row.description?.let {
                BasicText(
                    it,
                    style = SpicyType.Caption.copy(color = SpicyColors.TextSecondary, lineHeight = 1.4.em),
                    modifier = Modifier.widthIn(max = 520.dp).alpha(dim),
                )
            }
            listOfNotNull(reason, note).forEach {
                BasicText(
                    it,
                    style = SpicyType.Footnote.copy(fontWeight = FontWeight.Medium, lineHeight = 1.35.em),
                    modifier = Modifier.alpha(0.8f),
                )
            }
        }
    }
    val control: @Composable () -> Unit = {
        Box(Modifier.alpha(dim)) {
            when (row) {
                is PanelRow.Toggle -> ToggleControl(row.checked(), !disabled, row.onChange)
                is PanelRow.Select -> SelectControl(row, !disabled)
                is PanelRow.Slider -> SliderControl(row.spec, row.value(), !disabled, row.onChange)
                is PanelRow.Button -> ButtonControl(row.text, row.onClick)
            }
        }
    }
    if (row.stacked) {
        Column(rowModifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            labels()
            control()
        }
    } else {
        Row(rowModifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.weight(1f)) { labels() }
            control()
        }
    }
}

@Composable
private fun highlighted(text: String, query: String): AnnotatedString {
    val ranges = highlightRanges(text, query)
    if (ranges.isEmpty()) return AnnotatedString(text)
    val mark = SpanStyle(background = SpicyColors.TextPrimary.copy(alpha = 0.26f))
    return buildAnnotatedString {
        var at = 0
        ranges.forEach { r ->
            append(text.substring(at, r.first))
            withStyle(mark) { append(text.substring(r.first, r.last + 1)) }
            at = r.last + 1
        }
        append(text.substring(at))
    }
}

/**
 * A switch: a dark groove with a cream knob when off, a lit track with a white, hairline-ringed
 * knob when on, so the state reads by brightness alone.
 */
@Composable
private fun ToggleControl(checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val spec = tween<Float>(220, easing = PanelEase)
    val knob by animateFloatAsState(if (checked) 1f else 0f, spec)
    val track by animateColorAsState(
        when {
            checked && hovered -> Color.White.copy(alpha = 0.52f)
            checked -> Color.White.copy(alpha = 0.42f)
            hovered -> Color.Black.copy(alpha = 0.5f)
            else -> Color.Black.copy(alpha = 0.42f)
        },
        tween(220, easing = PanelEase),
    )
    val knobColor by animateColorAsState(
        when {
            checked -> Color.White
            hovered -> Color.White.copy(alpha = 0.9f)
            else -> Color(245, 245, 245).copy(alpha = 0.78f)
        },
        tween(220, easing = PanelEase),
    )
    Box(
        Modifier
            .size(42.dp, 24.dp)
            .hoverable(hover, enabled)
            .clickable(interactionSource = hover, indication = null, enabled = enabled) { onChange(!checked) }
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .drawBehind {
                val r = CornerRadius(12.dp.toPx())
                drawRoundRect(track, cornerRadius = r)
                val ring = if (checked) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.06f)
                val px = 1.dp.toPx()
                drawRoundRect(ring, topLeft = Offset(px / 2, px / 2), size = Size(size.width - px, size.height - px), cornerRadius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(px))
                val radius = 10.dp.toPx()
                val center = Offset(2.dp.toPx() + radius + knob * 18.dp.toPx(), size.height / 2)
                drawCircle(Color.Black.copy(alpha = 0.3f), radius + 0.5.dp.toPx(), center + Offset(0f, 2.dp.toPx()))
                drawCircle(knobColor, radius, center)
                if (checked) drawCircle(Color.Black.copy(alpha = 0.18f), radius, center, style = androidx.compose.ui.graphics.drawscope.Stroke(px))
            },
    )
}

@Composable
private fun SelectControl(row: PanelRow.Select, enabled: Boolean) {
    var open by remember { mutableStateOf(false) }
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val value = row.value()
    val shown = row.labels.getOrNull(row.options.indexOf(value)) ?: value
    Box {
        Row(
            Modifier
                .clip(SmallShape)
                .background(if (hovered || open) SpicyColors.TintBgPressed else SpicyColors.TintBg)
                .border(1.dp, if (hovered || open) SpicyColors.HairlineStrong else SpicyColors.Hairline, SmallShape)
                .hoverable(hover, enabled)
                .clickable(interactionSource = hover, indication = null, enabled = enabled) { open = true }
                .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicText(shown, style = SpicyType.Caption.copy(fontWeight = FontWeight.Medium), maxLines = 1)
            Box(Modifier.graphicsLayer { rotationZ = 90f }) { Glyph(PanelIcons.Chevron, SpicyColors.TextSecondary, 10.dp) }
        }
        if (open) {
            val plate = SpicyColors.brand?.fieldDeep ?: SpicyColors.BgElevated.copy(alpha = 1f)
            Popup(
                alignment = Alignment.TopEnd,
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    Modifier
                        .widthIn(min = 160.dp)
                        .clip(SmallShape)
                        .background(plate)
                        .border(1.dp, SpicyColors.HairlineStrong, SmallShape)
                        .padding(4.dp),
                ) {
                    row.options.forEachIndexed { i, option ->
                        val itemHover = remember { MutableInteractionSource() }
                        val itemHovered by itemHover.collectIsHoveredAsState()
                        val picked = option == value
                        BasicText(
                            row.labels[i],
                            style = SpicyType.Caption.copy(fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Medium),
                            maxLines = 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (itemHovered || picked) SpicyColors.TintBgPressed else Color.Transparent)
                                .hoverable(itemHover)
                                .clickable(interactionSource = itemHover, indication = null) {
                                    open = false
                                    if (option != value) row.onChange(option)
                                }
                                .pointerHoverIcon(PointerIcon.Hand)
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Kept in step with the thumb drawn below: the fill and centre tick line up with it at both ends. */
private val THUMB = 16.dp

/**
 * A snapping slider: a light groove, a fill from the left (or out of the centre tick for a range
 * across zero), the value underneath and a Reset once it leaves the default.
 */
@Composable
private fun SliderControl(spec: SliderSpec, value: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    val v = spec.clamp(value)
    val accent = SpicyColors.Accent
    val centreTick = SpicyColors.HairlineStrong
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(20.dp)
                .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
                .pointerInput(spec, enabled) {
                    if (!enabled) return@pointerInput
                    val thumb = THUMB.toPx()
                    fun at(x: Float) = spec.valueAt((x - thumb / 2) / (size.width - thumb).coerceAtLeast(1f))
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        at(down.position.x).let { if (it != current) change(it) }
                        drag(down.id) { moved ->
                            moved.consume()
                            at(moved.position.x).let { if (it != current) change(it) }
                        }
                    }
                }
                .drawBehind {
                    val thumb = THUMB.toPx()
                    val travel = size.width - thumb
                    fun xAt(f: Float) = thumb / 2 + f * travel
                    val h = 4.dp.toPx()
                    val y = size.height / 2
                    val pill = CornerRadius(h / 2)
                    drawRoundRect(Color.White.copy(alpha = 0.18f), Offset(0f, y - h / 2), Size(size.width, h), pill)
                    val f = spec.fraction(v)
                    if (spec.bipolar) {
                        val from = minOf(spec.zeroFraction, f)
                        val to = maxOf(spec.zeroFraction, f)
                        drawRoundRect(accent, Offset(xAt(from), y - h / 2), Size(xAt(to) - xAt(from), h), pill)
                        val tick = Size(2.dp.toPx(), 12.dp.toPx())
                        drawRoundRect(centreTick, Offset(xAt(spec.zeroFraction) - tick.width / 2, y - tick.height / 2), tick, CornerRadius(1.dp.toPx()))
                    } else {
                        drawRoundRect(accent, Offset(0f, y - h / 2), Size(xAt(f), h), pill)
                    }
                    val c = Offset(xAt(f), y)
                    drawCircle(Color.Black.copy(alpha = 0.3f), thumb / 2 + 0.5.dp.toPx(), c + Offset(0f, 2.dp.toPx()))
                    drawCircle(Color.White, thumb / 2, c)
                    drawCircle(Color.Black.copy(alpha = 0.18f), thumb / 2, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                },
        )
        Row(Modifier.fillMaxWidth().heightIn(min = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(spec.format(v), style = SpicyType.Caption.copy(fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.weight(1f))
            val default = spec.default
            if (default != null && v != default && enabled) {
                val hover = remember { MutableInteractionSource() }
                val hovered by hover.collectIsHoveredAsState()
                BasicText(
                    "Reset",
                    style = SpicyType.Caption.copy(
                        color = if (hovered) SpicyColors.TextPrimary else SpicyColors.TextSecondary,
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = Modifier
                        .hoverable(hover)
                        .clickable(interactionSource = hover, indication = null) { change(default) }
                        .pointerHoverIcon(PointerIcon.Hand),
                )
            }
        }
    }
}

@Composable
private fun ButtonControl(text: String, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    BasicText(
        text,
        style = SpicyType.Caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.01.em),
        modifier = Modifier
            .clip(SmallShape)
            .background(if (hovered) SpicyColors.TintBgPressed else SpicyColors.TintBg)
            .border(1.dp, if (hovered) SpicyColors.HairlineStrong else SpicyColors.Hairline, SmallShape)
            .hoverable(hover)
            .clickable(interactionSource = hover, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

/** The app's name and version first, then the ways out. */
@Composable
private fun About(actions: PanelActions) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(SpicyColors.TintBg)
                .border(1.dp, SpicyColors.Hairline, CardShape)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicText(
                "Spicy Lyrics",
                style = SpicyType.Body.copy(fontSize = 36.sp, fontWeight = FontWeight.Bold, lineHeight = 1.05.em, letterSpacing = (-0.03).em),
            )
            actions.version?.let {
                BasicText("Version $it", style = SpicyType.Body.copy(fontWeight = FontWeight.Medium))
            }
        }
        val platform = LocalPlatformActions.current
        if (actions.links.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
                actions.links.forEach { (label, url) -> LinkButton(label) { platform.openUrl(url) } }
            }
        }
    }
}

@Composable
private fun LinkButton(label: String, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Row(
        Modifier
            .height(36.dp)
            .clip(SmallShape)
            .background(if (hovered) SpicyColors.TintBgPressed else SpicyColors.TintBg)
            .border(1.dp, if (hovered) SpicyColors.HairlineStrong else SpicyColors.Hairline, SmallShape)
            .hoverable(hover)
            .clickable(interactionSource = hover, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicText(label, style = SpicyType.Body.copy(fontWeight = FontWeight.SemiBold))
        Box(Modifier.alpha(0.7f)) { Glyph(PanelIcons.ExternalArrow, SpicyColors.TextPrimary, 10.dp) }
    }
}

@Composable
internal fun Glyph(icon: ImageVector, color: Color, size: Dp) {
    Image(icon, null, Modifier.size(size), colorFilter = ColorFilter.tint(color))
}

private val PanelEase = androidx.compose.animation.core.CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
