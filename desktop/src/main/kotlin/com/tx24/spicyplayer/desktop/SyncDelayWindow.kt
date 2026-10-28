package com.tx24.spicyplayer.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.network.data.spotify.AudioAnalysis
import java.awt.Dimension
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.SwingUtilities
import javax.swing.WindowConstants
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/**
 * The sync-delay window: the followed player's saved offset, nudged in steps with the current line
 * in view, or found by tapping along to the beat ([TapSync]). Changes save per player and move the
 * lyrics at once. [attach] it from the lyrics window, then [openSyncDelay] (Ctrl+D there) shows it.
 */
object SyncDelay {
    private class Parts(
        val session: MediaSession,
        val delays: SyncDelays,
        val analysis: StateFlow<AudioAnalysis?>,
        val lyrics: () -> LyricsState?,
    )

    @Volatile private var parts: Parts? = null
    private var window: ComposeWindow? = null

    fun attach(session: MediaSession, delays: SyncDelays, analysis: StateFlow<AudioAnalysis?>, lyrics: () -> LyricsState?) {
        parts = Parts(session, delays, analysis, lyrics)
    }

    /** For the lyrics window's key handler: Ctrl+D opens the window. */
    fun onKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed || event.key != Key.D) return false
        openSyncDelay()
        return true
    }

    /** Shows the window, or brings it to the front when it is open. Does nothing before [attach]. */
    fun openSyncDelay() = SwingUtilities.invokeLater {
        val p = parts ?: return@invokeLater
        window?.let { it.toFront(); return@invokeLater }
        window = ComposeWindow().apply {
            title = "Sync delay"
            size = Dimension(460, 520)
            defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
            addWindowListener(object : WindowAdapter() {
                override fun windowClosed(e: WindowEvent) {
                    window = null
                }
            })
            setContent { SyncDelayPanel(p.session, p.delays, p.analysis, p.lyrics) }
            setLocationRelativeTo(null)
            isVisible = true
        }
    }
}

/** Opens the sync-delay window; for a menu. */
fun openSyncDelay() = SyncDelay.openSyncDelay()

private val white = Color.White
private val dim = Color.White.copy(alpha = 0.6f)

@Composable
private fun SyncDelayPanel(session: MediaSession, delays: SyncDelays, analysis: StateFlow<AudioAnalysis?>, lyrics: () -> LyricsState?) {
    // What the window shows, read from the session a few times a second.
    val now by produceState(DelayView(), session) {
        while (true) {
            val position = session.clock.positionAt(monoMs())
            val lines = (lyrics() as? LyricsState.Ready)?.lines.orEmpty()
            value = DelayView(session.track?.app, session.offsetMs, session.clock.isPlaying, lineAt(lines, position)?.text())
            delay(50)
        }
    }
    val app = now.app
    fun setOffset(offsetMs: Long) {
        app ?: return
        delays.set(app, offsetMs)
        session.offsetChanged()
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFF121212)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BasicText(app?.let { "Delay for $it" } ?: "Play something first",
            style = TextStyle(color = white, fontSize = 18.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        BasicText("${signed(now.offsetMs)} ms", style = TextStyle(color = white, fontSize = 34.sp, fontWeight = FontWeight.Bold))
        BasicText(
            if (delays.flagged) "--offset is set, so it wins over what you save here until you start without it."
            else "Positive shows the lyrics earlier. Saved for this player only.",
            style = TextStyle(color = dim, fontSize = 13.sp),
        )
        BasicText(now.line ?: "…", modifier = Modifier.fillMaxWidth().height(48.dp),
            style = TextStyle(color = white, fontSize = 16.sp, textAlign = TextAlign.Center), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (step in NUDGES) Chip((if (step > 0) "+" else "") + step) { setOffset(now.offsetMs + step) }
            Chip("Reset") { setOffset(0) }
        }
        TapPad(session, analysis, now, onUse = ::setOffset)
    }
}

/** How far each nudge button moves the offset. */
private val NUDGES = listOf(-50L, -10L, 10L, 50L)

private data class DelayView(val app: String? = null, val offsetMs: Long = 0, val playing: Boolean = false, val line: String? = null)

@Composable
private fun TapPad(session: MediaSession, analysis: StateFlow<AudioAnalysis?>, now: DelayView, onUse: (Long) -> Unit) {
    val taps = remember { mutableStateListOf<Long>() }
    // A new player or a changed offset makes the taps so far count against the wrong clock.
    LaunchedEffect(now.app, now.offsetMs, session.track?.key) { taps.clear() }
    val beats by produceState<LongArray?>(null, analysis) { analysis.collect { value = TapSync.beatsMs(it) } }
    val estimate = beats?.let { TapSync.estimate(taps, it, now.offsetMs) }
    val ready = now.playing && beats != null
    val (headline, detail) = when {
        !now.playing -> "Play a song" to "Then tap along to its beat here."
        beats == null -> "No beats yet" to "They come from the song's Spotify analysis; some songs have none."
        estimate == null -> "Tap along to the beat" to "${(TapSync.WARMUP_TAPS + TapSync.MIN_TAPS - taps.size).coerceAtLeast(1)} more taps"
        !estimate.steady -> "${signed(estimate.offsetMs)} ms?" to "Your taps are uneven. Keep going."
        else -> "${signed(estimate.offsetMs)} ms" to "From ${estimate.taps} taps, give or take ${estimate.spreadMs} ms."
    }
    Box(
        Modifier.fillMaxWidth().height(140.dp)
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .pointerInput(ready) {
                if (!ready) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown()
                    taps.add(session.clock.positionAt(monoMs()))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(headline, style = TextStyle(color = white, fontSize = 20.sp, fontWeight = FontWeight.SemiBold))
            BasicText(detail, modifier = Modifier.padding(top = 4.dp), style = TextStyle(color = dim, fontSize = 13.sp, textAlign = TextAlign.Center))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("Start over") { taps.clear() }
        if (estimate?.steady == true) Chip("Use ${signed(estimate.offsetMs)} ms") { onUse(estimate.offsetMs) }
    }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    BasicText(
        label,
        modifier = Modifier.background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(8.dp)).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        style = TextStyle(color = white, fontSize = 14.sp),
    )
}

private fun signed(ms: Long) = if (ms > 0) "+$ms" else ms.toString()
