package com.tx24.spicyplayer.ui.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.tx24.spicyplayer.ui.bigscreen.LocalLyricsActions
import com.tx24.spicyplayer.ui.bigscreen.MoreMenuEntries
import com.tx24.spicyplayer.ui.theme.SpicyColors

/**
 * The lyrics choices ([LocalLyricsActions]: source, blends, Spotify match, translation and
 * romanization style) in a card over the middle of the screen, for a platform without the
 * phone's quick settings. A click outside, Esc or back closes it.
 */
@Composable
fun LyricsChoicesPopup(onDismiss: () -> Unit) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            Modifier
                .width(320.dp)
                .shadow(24.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(SpicyColors.BgElevated)
                .padding(vertical = 6.dp),
        ) {
            MoreMenuEntries(LocalLyricsActions.current)
        }
    }
}
