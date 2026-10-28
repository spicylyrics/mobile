package com.tx24.spicyplayer.ui.nowplaying

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import com.tx24.spicyplayer.ui.settings.DISCORD_URL
import com.tx24.spicyplayer.ui.settings.openUrl

/** The notice's buttons on the phone: its clipboard, the Discord link in the browser, Material's icons. */
internal fun androidNoticeActions(
    context: Context,
    onRetry: () -> Unit,
    report: () -> String,
    onSearchSpotify: () -> Unit,
) = NoticeActions(
    onRetry = onRetry,
    report = report,
    onSearchSpotify = onSearchSpotify,
    copyText = { text ->
        runCatching {
            val clipboard = context.getSystemService(ClipboardManager::class.java) ?: error("no clipboard")
            clipboard.setPrimaryClip(ClipData.newPlainText("Spicy Lyrics Mobile debug info", text))
        }.isSuccess
    },
    onOpenHelp = { openUrl(context, DISCORD_URL) },
    icons = NoticeIcons(
        retry = Icons.Rounded.Refresh,
        copy = Icons.Rounded.ContentCopy,
        copied = Icons.Rounded.Check,
        copyFailed = Icons.Rounded.ErrorOutline,
        help = Icons.Rounded.Forum,
        search = Icons.Rounded.Search,
    ),
)
