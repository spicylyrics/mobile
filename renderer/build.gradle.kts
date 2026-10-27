// The word-synced lyrics renderer and the Kawarp background engine, shared by the Android app
// and the desktop app (docs/renderer-module.md). commonMain stays free of java.* and android.*.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

val composeVersion = "1.12.2"

kotlin {
    compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")
    android {
        namespace = "com.tx24.spicyplayer.renderer"
        compileSdk = 37
        minSdk = 23
        androidResources { enable = true }
        compilerOptions.jvmTarget = JvmTarget.JVM_11
    }
    jvm("desktop") {
        compilerOptions.jvmTarget = JvmTarget.JVM_17
    }

    sourceSets {
        commonMain.dependencies {
            // The lyric models it draws. :core is JVM-only, the one thing between this module and iOS.
            api(project(":core"))
            api("org.jetbrains.compose.runtime:runtime:$composeVersion")
            api("org.jetbrains.compose.foundation:foundation:$composeVersion")
            api("org.jetbrains.compose.ui:ui:$composeVersion")
            implementation("org.jetbrains.compose.animation:animation:$composeVersion")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
        }
        getByName("desktopTest").dependencies {
            implementation("junit:junit:4.13.2")
            implementation("net.sf.kxml:kxml2:2.3.0")
            implementation(compose.desktop.currentOs)
        }
        // The lyric fonts live once, as Android font resources; the desktop reads the same files
        // from its classpath.
        getByName("desktopMain").resources.srcDir("src/androidMain/res")
    }
}
