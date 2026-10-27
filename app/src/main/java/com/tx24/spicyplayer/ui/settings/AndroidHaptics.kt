package com.tx24.spicyplayer.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.tx24.spicyplayer.haptics.HapticPlayer
import com.tx24.spicyplayer.haptics.MusicHapticsPlayer
import com.tx24.spicyplayer.haptics.MusicPulse

/** Device → Haptics, played on the phone's vibration motor. */
@Composable
internal fun HapticsSection(settings: AppSettings) {
    val context = LocalContext.current
    val player = remember {
        val motor = HapticPlayer(context)
        object : MusicHapticsPlayer {
            override val canPlayMusic get() = motor.canPlayMusic
            override fun play(pulse: MusicPulse, strength: Float) = motor.play(pulse, strength)
        }
    }
    HapticsSection(settings, player)
}
