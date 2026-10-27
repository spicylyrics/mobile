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
        // Compose resources ride in the Android assets, which this plugin leaves off by default.
        androidResources { enable = true }
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
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            implementation("org.jetbrains.compose.components:components-resources:$composeVersion")
        }
        getByName("desktopTest").dependencies {
            implementation("junit:junit:4.13.2")
        }
    }
}

// Drawables in src/commonMain/composeResources, read as Res.drawable.*. The package is fixed so
// the generated class doesn't move with the module's group or name.
compose.resources {
    publicResClass = true
    packageOfResClass = "com.tx24.spicyplayer.ui.resources"
    generateResClass = always
}
