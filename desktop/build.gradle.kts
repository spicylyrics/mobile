// Spicy Lyrics Desktop: Windows' media sessions (through desktop/gsmtc) into :core's sources and
// lyric clock, drawn by the shared lyrics renderer (:renderer) on Compose Desktop.
//
//   ./gradlew :desktop:run                                  the lyrics window
//   ./gradlew :desktop:run -PdesktopArgs="--demo --seconds 30 --no-vsync"   renderer on a made-up song
//   ./gradlew :desktop:console -PdesktopArgs="--log run.jsonl"              console measurement
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
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
    implementation(project(":renderer"))
    implementation(compose.desktop.currentOs)
    // :core compiles against the XmlPull API that Android ships.
    runtimeOnly("net.sf.kxml:kxml2:2.3.0")
}

val desktopArgs = (findProperty("desktopArgs") as String?)?.split(" ")?.filter { it.isNotBlank() }.orEmpty()

compose.desktop {
    application {
        mainClass = "com.tx24.spicyplayer.desktop.MainKt"
        args += desktopArgs
    }
}

tasks.register<JavaExec>("console") {
    description = "Prints the current line of Windows' media session, for measuring each player."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.tx24.spicyplayer.desktop.ConsoleKt"
    args = desktopArgs
    workingDir = rootDir
    standardInput = System.`in`
}
