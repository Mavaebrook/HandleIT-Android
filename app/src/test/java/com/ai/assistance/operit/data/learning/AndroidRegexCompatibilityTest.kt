package com.ai.assistance.operit.data.learning

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards against regex features that compile on the desktop JVM but throw on Android.
 *
 * Android's `java.util.regex.Pattern` does not support the `UNICODE_CHARACTER_CLASS` or
 * `CANON_EQ` flags; `Pattern.compile` throws for them. JVM unit tests cannot observe this because
 * the desktop JVM supports both. `TaskPreflightFormatter` once compiled a regex with
 * `UNICODE_CHARACTER_CLASS` in its object initializer, so on devices every chat turn failed with
 * an `ExceptionInInitializerError` and an empty reply. This test keeps those flags out of the
 * app's main sources.
 */
class AndroidRegexCompatibilityTest {

    private val unsupportedOnAndroid = listOf("UNICODE_CHARACTER_CLASS", "CANON_EQ")

    @Test
    fun mainSources_doNotUseRegexFlagsUnsupportedOnAndroid() {
        val mainSources = File("src/main/java")
        assertTrue("run from the app module: ${mainSources.absolutePath}", mainSources.isDirectory)

        val offenders = mainSources.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    val code = line.substringBefore("//").trim()
                    if (code.startsWith("*")) return@mapIndexedNotNull null // KDoc/Javadoc text
                    unsupportedOnAndroid.firstOrNull { code.contains(it) }
                        ?.let { "${file.path}:${index + 1}: $it" }
                }
            }
            .toList()

        assertEquals("Regex flags that throw on Android:\n" + offenders.joinToString("\n"), emptyList<String>(), offenders)
    }

    @Test
    fun preflightBlock_collapsesUnicodeWhitespaceWithoutTheUnsupportedFlag() {
        val episode = com.ai.assistance.operit.data.model.TaskEpisode(
            id = 1L,
            taskSummary = "a  b　c d",
            outcome = com.ai.assistance.operit.data.model.TaskEpisode.OUTCOME_FAILURE
        )
        val block = TaskPreflightFormatter.buildPreflightBlock(listOf(episode), "a b c d", useEnglish = true)
        assertTrue(block, block.contains("task: a b c d"))
    }
}
