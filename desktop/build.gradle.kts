// Spicy Lyrics Desktop: Windows' media sessions (through desktop/gsmtc) into :core's sources and
// lyric clock, drawn by the app's lyrics renderer on Compose Desktop. The renderer sources are
// copied in from :app at build time; src/main/kotlin/shims stands in for the few Android APIs
// they touch.
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

val appMain = rootProject.file("app/src/main")

val rendererSources = tasks.register<Sync>("rendererSources") {
    from(appMain.resolve("java")) {
        include("com/tx24/spicyplayer/lyrics/spicy/RenderConfig.kt")
        include("com/tx24/spicyplayer/lyrics/spicy/parser/LetterSynthesizer.kt")
        include("com/tx24/spicyplayer/lyrics/spicy/animation/**")
        include("com/tx24/spicyplayer/lyrics/spicy/canvas/*.kt")
        include("com/tx24/spicyplayer/lyrics/spicy/canvas/kawarp/KawarpBlurCore.kt")
        include("com/tx24/spicyplayer/lyrics/spicy/canvas/kawarp/KawarpEngine.kt")
        // The old bitmap background; Kawarp is a separate port.
        exclude("**/DynamicBackground*.kt", "**/StackBlur.kt")
    }
    into(layout.buildDirectory.dir("generated/renderer"))
}

// Kawarp's shader, lifted out of KawarpBackground.kt (whose Kotlin side is Android's RuntimeShader).
val kawarpShader = tasks.register("kawarpShader") {
    val source = appMain.resolve("java/com/tx24/spicyplayer/lyrics/spicy/canvas/kawarp/KawarpBackground.kt")
    val out = layout.buildDirectory.file("generated/kawarp/com/tx24/spicyplayer/desktop/KawarpShader.kt")
    inputs.file(source)
    outputs.file(out)
    doLast {
        val q = "\"\"\""
        val agsl = Regex("KAWARP_FUSED_AGSL = $q(.*?)$q", RegexOption.DOT_MATCHES_ALL).find(source.readText())!!.groupValues[1]
        out.get().asFile.apply { parentFile.mkdirs() }
            .writeText("package com.tx24.spicyplayer.desktop\n\ninternal const val KAWARP_FUSED_AGSL = $q$agsl$q\n")
    }
}

val rendererFonts = tasks.register<Sync>("rendererFonts") {
    from(appMain.resolve("res/font"))
    into(layout.buildDirectory.dir("generated/fonts/fonts"))
}

sourceSets.main {
    kotlin.srcDir(rendererSources)
    kotlin.srcDir(kawarpShader.map { layout.buildDirectory.dir("generated/kawarp").get() })
    resources.srcDir(rendererFonts.map { it.destinationDir.parentFile })
}

dependencies {
    implementation(project(":core"))
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
