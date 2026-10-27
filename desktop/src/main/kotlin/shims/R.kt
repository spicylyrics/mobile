@file:Suppress("PackageDirectoryMismatch")

package com.tx24.spicyplayer

object R {
    object font {
        val names = listOf(
            "lyrics_regular", "lyrics_medium", "lyrics_semibold", "lyrics_bold",
            "vazirmatn_variable", "noto_sans_georgian_variable",
        ).withIndex().associate { (i, name) -> i to name }
        const val lyrics_regular = 0
        const val lyrics_medium = 1
        const val lyrics_semibold = 2
        const val lyrics_bold = 3
        const val vazirmatn_variable = 4
        const val noto_sans_georgian_variable = 5
    }
}
