package com.tx24.spicyplayer.ui.components

import com.tx24.spicyplayer.ui.theme.SpicyPalette

/** How a part of the chrome is dressed: the lavender brand, or the plain monochrome glass. */
enum class ChromeTheme(val label: String, val palette: SpicyPalette) {
    Purple("Purple", SpicyPalette.Purple),
    Classic("Classic", SpicyPalette.Neutral),
}
