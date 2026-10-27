// Stand-ins for the Android types the renderer sources name. The desktop app doesn't load avatars yet, so
// these only have to compile; the one that matters at run time is View.display.refreshRate.
@file:Suppress("unused", "PackageDirectoryMismatch")

package android.content

open class Context
