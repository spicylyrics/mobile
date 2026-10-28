package com.tx24.spicyplayer.desktop

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tx24.spicyplayer.ui.bigscreen.TooltipHost

/** The view controls' tooltips: a small dark label under the button after a short hover. */
object DesktopTooltips : TooltipHost {
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    override fun Tooltip(text: String, content: @Composable () -> Unit) {
        TooltipArea(
            tooltip = {
                BasicText(
                    text,
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(28, 28, 32).copy(alpha = 0.88f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    style = TextStyle(color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp, fontWeight = FontWeight.Medium),
                )
            },
            delayMillis = 400,
            tooltipPlacement = TooltipPlacement.ComponentRect(Alignment.BottomCenter, Alignment.BottomCenter, DpOffset(0.dp, 6.dp)),
            content = content,
        )
    }
}
