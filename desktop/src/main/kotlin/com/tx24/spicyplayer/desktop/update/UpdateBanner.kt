package com.tx24.spicyplayer.desktop.update

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.awt.Desktop
import java.net.URI
import kotlinx.coroutines.flow.StateFlow

/**
 * "Spicy Lyrics Desktop X.Y.Z is out" while [update] has a release: a click opens its page in the
 * browser, the × hides it until the next launch.
 */
@Composable
fun UpdateBanner(update: StateFlow<UpdateInfo?>, modifier: Modifier = Modifier) {
    val info by update.collectAsState()
    var dismissed by remember { mutableStateOf<String?>(null) }
    val shown = info?.takeIf { it.version != dismissed } ?: return
    Row(
        modifier.padding(20.dp).widthIn(max = 480.dp)
            .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
            .clickable { openInBrowser(shown.url) }
            .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText("Spicy Lyrics Desktop ${shown.version} is out",
            style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
        BasicText("Download", modifier = Modifier.padding(start = 12.dp),
            style = TextStyle(color = Color.White.copy(alpha = 0.65f), fontSize = 14.sp))
        BasicText("×", modifier = Modifier.padding(start = 12.dp).clickable { dismissed = shown.version }.padding(horizontal = 8.dp),
            style = TextStyle(color = Color.White.copy(alpha = 0.65f), fontSize = 18.sp))
    }
}

private fun openInBrowser(url: String) {
    runCatching {
        if (url.startsWith("https://") && Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(url))
    }
}
