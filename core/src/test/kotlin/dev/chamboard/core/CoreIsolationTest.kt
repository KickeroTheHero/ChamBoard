package dev.chamboard.core

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * INV-2: `core` stays pure Kotlin — no Android framework, no networking, no
 * logging or clipboard APIs. Runs over the source tree so a violation fails
 * with the offending file and line.
 */
class CoreIsolationTest {

    private val forbidden = listOf(
        Regex("""^\s*import\s+android\."""),
        Regex("""^\s*import\s+androidx\."""),
        Regex("""^\s*import\s+java\.net\."""),
        Regex("""^\s*import\s+javax\.net\."""),
        Regex("""\bURL\s*\("""),
        Regex("""\bSocket\s*\("""),
        Regex("""\bprintln\s*\("""),
    )

    @Test
    fun mainSourcesUseNoForbiddenApis() {
        val root = File("src/main")
        val violations = root.walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "java") }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { i, line ->
                    if (forbidden.any { it.containsMatchIn(line) }) "${file.path}:${i + 1}: ${line.trim()}" else null
                }
            }
            .toList()
        assertTrue("Forbidden API use in core (INV-2):\n" + violations.joinToString("\n"), violations.isEmpty())
    }
}
