package com.tx24.spicyplayer.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tx24.spicyplayer.playback.TapCalibration
import com.tx24.spicyplayer.ui.components.SettingRow
import com.tx24.spicyplayer.ui.components.SpicyButton
import com.tx24.spicyplayer.ui.theme.SpicyColors
import com.tx24.spicyplayer.ui.theme.SpicyRadii
import com.tx24.spicyplayer.ui.theme.SpicySpacing
import com.tx24.spicyplayer.ui.theme.SpicyType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long to wait for a song's beats before saying it has none. */
private const val BEATS_WAIT_MS = 8_000L

/**
 * What finding the delay by tapping needs from the platform. [song] changes when the song does,
 * which starts the taps over. [beatsMs]: the playing song's beats, null until it has them.
 * [tapPositionMs]: the player's own position (no delay taken off) when a press stamped with
 * [androidx.compose.ui.input.pointer.PointerInputChange.uptimeMillis] landed. [onCalibrating] is
 * told while the pad is up, so the platform can fetch the beats.
 */
class TapCalibrationPlatform(
    val playing: Boolean,
    val song: Any?,
    val beatsMs: () -> LongArray?,
    val tapPositionMs: (uptimeMillis: Long) -> Long,
    val onCalibrating: (Boolean) -> Unit,
)

/**
 * Finding the output delay by tapping along to the song playing ([TapCalibration]). The beats come
 * from the song's Spotify analysis, so it works with any player once the song is matched there.
 */
@Composable
fun DelayCalibrationRow(outputLabel: String, lyricDelayMs: Int, platform: TapCalibrationPlatform, setDelay: (Int) -> Unit) {
    var active by remember { mutableStateOf(false) }
    SettingRow(
        label = "Tap to the beat",
        description = "Finds the delay for $outputLabel: tap along to the song playing and it works out how late the sound arrives.",
        icon = Icons.Rounded.TouchApp,
        stacked = active,
    ) {
        if (active) {
            TapPad(lyricDelayMs, platform, setDelay, onDone = { active = false })
        } else {
            SpicyButton("Start", { active = true })
        }
    }
}

@Composable
private fun TapPad(lyricDelayMs: Int, platform: TapCalibrationPlatform, setDelay: (Int) -> Unit, onDone: () -> Unit) {
    val onCalibrating by rememberUpdatedState(platform.onCalibrating)
    DisposableEffect(Unit) {
        onCalibrating(true)
        onDispose { onCalibrating(false) }
    }
    // The player's position at each tap; a new song starts over.
    val taps = remember { mutableStateListOf<Long>() }
    LaunchedEffect(platform.song) { taps.clear() }
    // The beats arrive a moment after the song is looked up on Spotify.
    val beatsMs by rememberUpdatedState(platform.beatsMs)
    val beats by produceState<LongArray?>(null, platform.song) {
        value = null
        while (true) {
            value = beatsMs()
            delay(if (value == null) 250L else 2_000L)
        }
    }
    val beatsLate by produceState(false, platform.song) {
        value = false
        delay(BEATS_WAIT_MS)
        value = true
    }
    val estimate = beats?.let { TapCalibration.estimate(taps, it, lyricDelayMs) }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val flash = remember { Animatable(0f) }
    val ready = platform.playing && beats != null

    val (headline, detail) = when {
        !platform.playing -> "Play a song" to "Then tap along to its beat here."
        beats == null && beatsLate -> "No beats for this song" to "It needs the song on Spotify. Try another one."
        beats == null -> "Getting the beats…" to "Looking the song up on Spotify."
        estimate == null -> "Tap along to the beat" to
            "${(TapCalibration.WARMUP_TAPS + TapCalibration.MIN_TAPS - taps.size).coerceAtLeast(1)} more taps"
        !estimate.steady -> "${estimate.delayMs.signed()} ms?" to "Your taps are uneven. Keep going."
        else -> "${estimate.delayMs.signed()} ms" to "From ${estimate.taps} taps, give or take ${estimate.spreadMs} ms. Keep going to firm it up."
    }

    Column(verticalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
        val shape = RoundedCornerShape(SpicyRadii.Lg)
        Box(
            Modifier
                .fillMaxWidth()
                .height(148.dp)
                .clip(shape)
                .background(lerp(SpicyColors.TintBg, SpicyColors.TintBgPressed, flash.value))
                .border(1.dp, SpicyColors.Hairline, shape)
                .pointerInput(ready) {
                    if (!ready) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        taps.add(platform.tapPositionMs(down.uptimeMillis))
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        scope.launch {
                            flash.snapTo(1f)
                            flash.animateTo(0f, tween(220))
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(SpicySpacing.S1)) {
                Text(headline, style = SpicyType.Title, color = SpicyColors.TextPrimary, textAlign = TextAlign.Center)
                Text(detail, style = SpicyType.Footnote, color = SpicyColors.TextSecondary, textAlign = TextAlign.Center)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
            SpicyButton("Cancel", onDone, Modifier.weight(1f))
            SpicyButton("Start over", { taps.clear() }, Modifier.weight(1f), enabled = taps.isNotEmpty())
            SpicyButton(
                "Use it",
                {
                    estimate?.let { setDelay(it.delayMs) }
                    onDone()
                },
                Modifier.weight(1f),
                enabled = estimate?.steady == true,
            )
        }
    }
}
