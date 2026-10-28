package com.tx24.spicyplayer.ui.bigscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tx24.spicyplayer.ui.theme.SpicyColors

/**
 * Asks before lyrics are sent somewhere new: [request]'s title and disclosure, then Not now and
 * Allow. Drawn in the menu or panel that asked, in its style.
 */
@Composable
fun ConsentPrompt(request: ConsentRequest, onAnswer: (allowed: Boolean) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(request.title, style = TextStyle(color = SpicyColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
        BasicText(request.description, style = TextStyle(color = SpicyColors.TextSecondary, fontSize = 12.sp, lineHeight = 17.sp))
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
            PromptButton("Not now", primary = false) { onAnswer(false) }
            PromptButton("Allow", primary = true) { onAnswer(true) }
        }
    }
}

@Composable
private fun PromptButton(label: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (primary) Color.White else Color.White.copy(alpha = 0.1f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        BasicText(label, style = TextStyle(color = if (primary) Color.Black else SpicyColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
    }
}
