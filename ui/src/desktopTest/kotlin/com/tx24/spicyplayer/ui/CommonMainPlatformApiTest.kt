package com.tx24.spicyplayer.ui

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * commonMain must build for any Kotlin target, iOS included. Both of today's targets are JVMs,
 * so the compiler lets JVM and Android APIs through there; this catches them. Platform calls
 * belong behind an expect.
 */
class CommonMainPlatformApiTest {
    private val platformApi = Regex(
        """\bjava\.|\bjavax\.|\bandroid\.|\bSystem\.|\bCharacter\.|\bMath\.|\bThread\b|::class\.java\b|@Jvm|@Synchronized|\bsynchronized\(|\.codePointAt\(|\.codePointBefore\(|String\.format\(""",
    )

    @Test
    fun `commonMain uses no JVM or Android API`() {
        val common = File("src/commonMain/kotlin")
        assumeTrue("run from the ui module", common.isDirectory)
        val hits = common.walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
            file.readLines().mapIndexedNotNull { i, line ->
                val code = line.trim()
                if (code.startsWith("//") || code.startsWith("*") || code.startsWith("/*")) return@mapIndexedNotNull null
                platformApi.find(code.substringBefore(" // "))?.let { "${file.path}:${i + 1}: ${it.value}" }
            }
        }.toList()
        assertTrue("JVM/Android API in commonMain:\n" + hits.joinToString("\n"), hits.isEmpty())
    }
}
