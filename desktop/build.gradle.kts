// Throwaway console test: Windows' media sessions (through desktop/gsmtc) into :core's sources and
// LyricClock, printing the current line. A measurement, not the desktop app.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions.jvmTarget = JvmTarget.JVM_17
}

dependencies {
    implementation(project(":core"))
    // :core compiles against the XmlPull API that Android ships.
    runtimeOnly("net.sf.kxml:kxml2:2.3.0")
}

application {
    mainClass = "com.tx24.spicyplayer.desktop.NowPlayingKt"
}
