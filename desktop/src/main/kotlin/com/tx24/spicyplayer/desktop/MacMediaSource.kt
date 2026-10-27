package com.tx24.spicyplayer.desktop

/**
 * macOS has no supported way yet, so this reports nothing.
 *
 * TODO: the likely routes, either behind a small helper that writes the same JSON lines as gsmtc:
 * - MediaRemote, the private framework behind the system's Now Playing widget. It sees every
 *   player with position and timestamp, but since macOS 15.4 only Apple-signed processes may
 *   read it, so it needs a workaround that runs the calls inside a system binary.
 * - AppleScript (`osascript`) against Spotify and Music, which both expose the track, player
 *   state and position. Only those two players, and only by polling.
 */
fun macMediaSource(): MediaSource = UnsupportedMediaSource("macos", "Following players on macOS isn't supported yet")
