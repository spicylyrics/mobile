// Spicy Lyrics Desktop: Windows' media sessions (through desktop/gsmtc) into :core's sources and
// lyric clock, drawn by the shared lyrics renderer (:renderer) on Compose Desktop.
//
//   ./gradlew :desktop:run                                  the lyrics window
//   ./gradlew :desktop:run -PdesktopArgs="--demo --seconds 30 --no-vsync"   renderer on a made-up song
//   ./gradlew :desktop:console -PdesktopArgs="--log run.jsonl"              console measurement
//   ./gradlew :desktop:packageMsi                           the installer (build/compose/binaries/main/msi)
//   ./gradlew :desktop:createDistributable                  the portable app (build/compose/binaries/main/app)
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

// CI passes -PdesktopVersion from a desktop-vX.Y.Z tag. Windows installers take only
// MAJOR.MINOR.BUILD, with MAJOR and MINOR up to 255 and BUILD up to 65535.
version = (findProperty("desktopVersion") as String?)?.trim()?.takeIf(String::isNotEmpty) ?: "0.1.0"
version.toString().split('.').let { parts ->
    val limits = listOf(255, 255, 65535)
    require(parts.size == 3 && parts.zip(limits).all { (part, max) ->
        part.matches(Regex("[0-9]+")) && (part.toIntOrNull() ?: Int.MAX_VALUE) <= max
    }) { "Desktop version must be MAJOR.MINOR.BUILD (MAJOR, MINOR <= 255, BUILD <= 65535), got '$version'" }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions.jvmTarget = JvmTarget.JVM_17
}

// The publishable Spicy Lyrics key from the root `.env` (or the environment), built in so the
// installed app has it without a checkout. Blank leaves the Spicy Lyrics source out.
val spicyLyricsKey = tasks.register("spicyLyricsKey") {
    val dotEnv = rootProject.file(".env")
    val out = layout.buildDirectory.file("generated/key/spicy-lyrics-key.txt")
    val key = providers.provider {
        dotEnv.takeIf(File::isFile)?.readLines()?.map(String::trim)
            ?.firstOrNull { it.startsWith("SPICY_LYRICS_CLIENT_KEY=") }?.substringAfter('=')?.trim()?.trim('"', '\'')
            ?: System.getenv("SPICY_LYRICS_CLIENT_KEY").orEmpty()
    }
    inputs.property("key", key)
    outputs.file(out)
    doLast {
        require(key.get().isBlank() || Regex("sl_pk_[A-Za-z0-9_-]+").matches(key.get())) {
            "SPICY_LYRICS_CLIENT_KEY must be a publishable sl_pk_ key (and nothing else on its line in .env)"
        }
        out.get().asFile.apply { parentFile.mkdirs() }.writeText(key.get())
    }
}

sourceSets.main {
    resources.srcDir(spicyLyricsKey.map { layout.buildDirectory.dir("generated/key").get() })
}

// The desktop app runs on Compose Multiplatform 1.13 (and its skiko) while the shared modules and
// the phone stay on 1.12: 1.13 draws the window synchronously while it's resized on Direct3D
// (skiko.rendering.windows.direct3DSynchronousLiveResize), where 1.12's async frames stalled for
// seconds. Its Android artifacts need minSdk 24 and are a beta, so the phone doesn't take it.
// Compose keeps binary compatibility across minor versions, so :ui and :renderer, compiled
// against 1.12, run on it. The material icons are published apart, at 1.7.3.
val desktopComposeVersion = "1.13.0-beta01"
val desktopSkikoVersion = "0.156.0"
// 1.13 brings skiko's natives for every OS in one jar (skiko-awt-runtime-all), which the 1.12
// Gradle plugin used here doesn't unpack into the app, so the installed app found no native
// library and never opened its window. Each installer builds on its own OS, so it takes that OS's
// runtime instead, as 1.12 did; tests run on it too.
val skikoRuntime = run {
    val os = System.getProperty("os.name").lowercase()
    val arm = System.getProperty("os.arch").lowercase().let { it == "aarch64" || it == "arm64" }
    when {
        os.startsWith("windows") -> if (arm) "windows-arm64" else "windows-x64"
        os.startsWith("mac") -> if (arm) "macos-arm64" else "macos-x64"
        else -> if (arm) "linux-arm64" else "linux-x64"
    }
}
configurations.all {
    resolutionStrategy.eachDependency {
        val group = requested.group
        when {
            group == "org.jetbrains.skiko" -> useVersion(desktopSkikoVersion)
            group.startsWith("org.jetbrains.compose.") && !requested.name.startsWith("material-icons") ->
                useVersion(desktopComposeVersion)
        }
    }
    resolutionStrategy.dependencySubstitution {
        substitute(module("org.jetbrains.skiko:skiko-awt-runtime-all"))
            .using(module("org.jetbrains.skiko:skiko-awt-runtime-$skikoRuntime:$desktopSkikoVersion"))
            .because("the 1.12 Gradle plugin unpacks only a per-OS skiko runtime into the app")
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":renderer"))
    implementation(project(":ui"))
    // The shared lyrics screen's Scaffold takes its colours from the dark Material theme, as on the phone.
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
    implementation(compose.desktop.currentOs)
    // Dispatchers.Main, the UI thread the lyrics state lives on.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
    // :core compiles against the XmlPull API that Android ships.
    runtimeOnly("net.sf.kxml:kxml2:2.3.0")
    testImplementation("junit:junit:4.13.2")
}

val desktopArgs = (findProperty("desktopArgs") as String?)?.split(" ")?.filter { it.isNotBlank() }.orEmpty()

// The media session helper, built by cargo and shipped in the app's resources folder, where
// MediaSession.kt looks for it before the checkout's copy.
val gsmtcHelper = tasks.register<Exec>("gsmtcHelper") {
    description = "Builds the media session helper with cargo."
    workingDir = file("gsmtc")
    commandLine("cargo", "build", "--release")
    inputs.files("gsmtc/Cargo.toml", "gsmtc/Cargo.lock", fileTree("gsmtc/src"))
    outputs.file("gsmtc/target/release/gsmtc.exe")
    // Windows only: other platforms read their players without it (see MediaSource.kt).
    onlyIf { System.getProperty("os.name").startsWith("Windows") }
}

val helperResources = tasks.register<Sync>("helperResources") {
    from(gsmtcHelper)
    into(layout.buildDirectory.dir("appResources/windows"))
}

compose.desktop {
    application {
        mainClass = "com.tx24.spicyplayer.desktop.MainKt"
        args += desktopArgs
        // Direct3D draws each frame on the resize event itself while the window is dragged,
        // instead of on its render thread (Compose Multiplatform 1.13, see desktopComposeVersion).
        jvmArgs += "-Dskiko.rendering.windows.direct3DSynchronousLiveResize=true"

        nativeDistributions {
            // MSI over EXE: jpackage's EXE is the same MSI behind a bootstrapper, and the MSI on
            // its own upgrades in place (upgradeUuid), repairs and suits winget. Each format builds
            // only on its own OS.
            targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Dmg)
            packageName = "Spicy Lyrics Desktop"
            packageVersion = project.version.toString()
            description = "Word-synced lyrics for whatever your computer is playing"
            vendor = "TX24"
            appResourcesRootDir = layout.buildDirectory.dir("appResources")
            // suggestRuntimeModules, plus jdk.crypto.ec, which jdeps can't see but TLS needs.
            modules("java.instrument", "java.sql", "jdk.unsupported", "jdk.crypto.ec")
            windows {
                iconFile = file("packaging/icon.ico")
                menuGroup = "Spicy Lyrics"
                menu = true
                shortcut = true
                perUserInstall = true
                dirChooser = true
                // Never change: it is how a new MSI finds and replaces the installed one.
                upgradeUuid = "bb2eb161-512c-4044-9a54-796f963d1b13"
            }
            linux {
                packageName = "spicy-lyrics-desktop"
                iconFile = file("src/main/resources/icon.png")
                menuGroup = "Spicy Lyrics"
                appCategory = "AudioVideo"
                debMaintainer = "TX24"
            }
            macOS {
                bundleID = "com.tx24.spicylyrics.desktop"
                // A macOS app's version can't start with 0, so 0.y.z ships as 1.y.z there.
                packageVersion = macPackageVersion(project.version.toString())
            }
        }
    }
}

// The Compose plugin registers these after the script runs.
tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(helperResources) }
tasks.matching { it.name == "run" }.configureEach { dependsOn(gsmtcHelper) }

tasks.register<JavaExec>("console") {
    description = "Prints the current line of Windows' media session, for measuring each player."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.tx24.spicyplayer.desktop.ConsoleKt"
    args = desktopArgs
    workingDir = rootDir
    standardInput = System.`in`
}

/** [version] for macOS, which refuses a major version of 0: 0.y.z becomes 1.y.z, anything else stays. */
fun macPackageVersion(version: String): String = if (version.startsWith("0.")) "1." + version.removePrefix("0.") else version

// A failing test prints its whole cause chain, so CI's log says why (a native library, say).
tasks.withType<Test>().configureEach {
    testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showCauses = true
        showStackTraces = true
    }
}
