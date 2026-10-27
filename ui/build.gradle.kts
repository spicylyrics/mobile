// Shared Compose UI: the design tokens, glass, skeletons, glyphs and now-playing layout metrics,
// used by the Android app and the desktop app (docs/renderer-module.md). commonMain stays free of
// java.* and android.*.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

val composeVersion = "1.12.2"

kotlin {
    android {
        namespace = "com.tx24.spicyplayer.ui"
        compileSdk = 37
        minSdk = 23
        compilerOptions.jvmTarget = JvmTarget.JVM_11
    }
    jvm("desktop") {
        compilerOptions.jvmTarget = JvmTarget.JVM_17
    }

    sourceSets {
        commonMain.dependencies {
            // SpicyTokens reads the lyric font family from the renderer.
            api(project(":renderer"))
            api("org.jetbrains.compose.runtime:runtime:$composeVersion")
            api("org.jetbrains.compose.foundation:foundation:$composeVersion")
            api("org.jetbrains.compose.ui:ui:$composeVersion")
            // HazeStyle and HazeState are part of the tokens' and Backdrop's public API.
            api("dev.chrisbanes.haze:haze:1.7.3")
            implementation("org.jetbrains.compose.animation:animation:$composeVersion")
            implementation("org.jetbrains.compose.material3:material3:1.9.0")
            // The settings rows' icons (Icons.Rounded), the same set the app already ships.
            implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
        }
        getByName("desktopTest").dependencies {
            implementation("junit:junit:4.13.2")
        }
    }
}
