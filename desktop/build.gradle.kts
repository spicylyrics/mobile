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

version = "0.1.0"

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

dependencies {
    implementation(project(":core"))
    implementation(project(":renderer"))
    implementation(project(":ui"))
    implementation(compose.desktop.currentOs)
    // :core compiles against the XmlPull API that Android ships.
    runtimeOnly("net.sf.kxml:kxml2:2.3.0")
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
}

val helperResources = tasks.register<Sync>("helperResources") {
    from(gsmtcHelper)
    into(layout.buildDirectory.dir("appResources/windows"))
}

compose.desktop {
    application {
        mainClass = "com.tx24.spicyplayer.desktop.MainKt"
        args += desktopArgs

        nativeDistributions {
            // MSI over EXE: jpackage's EXE is the same MSI behind a bootstrapper, and the MSI on
            // its own upgrades in place (upgradeUuid), repairs and suits winget.
            targetFormats(TargetFormat.Msi)
            packageName = "Spicy Lyrics Desktop"
            packageVersion = project.version.toString()
            description = "Word-synced lyrics for whatever Windows is playing"
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
