package com.tx24.spicyplayer.haptics

/** What the music haptics follow. */
enum class MusicHapticsStyle(val label: String) { Drums("The drums"), Beat("The beat") }

/**
 * What a music haptic marks. The first three follow the drums ([MusicHapticsStyle.Drums]), [Note]
 * the notes where there are no drums; [Beat] and [Downbeat] are the steady beat
 * ([MusicHapticsStyle.Beat]); [Accent] and [Drop] are the big moments in either.
 */
enum class MusicPulse { Kick, Snare, Note, Beat, Downbeat, Accent, Drop }

/** Plays single music haptics, for the settings to try them out. */
interface MusicHapticsPlayer {
    /** There is a motor that can play them (and a system new enough to shape them). */
    val canPlayMusic: Boolean

    /** One [pulse] at [strength], 0..1. */
    fun play(pulse: MusicPulse, strength: Float)
}

/** No motor: the desktop, and tests. */
object NoMusicHaptics : MusicHapticsPlayer {
    override val canPlayMusic = false
    override fun play(pulse: MusicPulse, strength: Float) = Unit
}
