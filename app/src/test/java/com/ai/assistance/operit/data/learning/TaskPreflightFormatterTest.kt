package com.ai.assistance.operit.data.learning

import com.ai.assistance.operit.data.model.TaskEpisode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM regression tests for C01: production use of `missingPrerequisites` in the bounded
 * similar-task preflight block. Pure formatter logic only — no Android or ObjectBox dependencies.
 */
class TaskPreflightFormatterTest {

    // --- C01: an applicable prerequisite NOT evident in the request is rendered as a caution ---

    // C02 tightened applicability to require at least two DISTINCT matched meaningful tokens, so a
    // prerequisite of "signing continuity" is only applicable when the context contains BOTH
    // "signing" and "continuity". The context below contains both tokens but NOT the contiguous
    // phrase "signing continuity", so the prerequisite is applicable yet not satisfied -> caution.
    @Test
    fun buildPreflightBlock_applicablePrerequisiteNotEvident_rendersVerifyCaution() {
        val episode = episode(
            id = 1L,
            taskSummary = "in-place apk update failed",
            requiredPrerequisites = listOf("signing continuity")
        )

        val block = TaskPreflightFormatter.buildPreflightBlock(
            episodes = listOf(episode),
            currentTaskContext = "perform an in-place apk update; verify signing and continuity of the release key",
            useEnglish = true
        )

        assertTrue(block.contains("verify before relying on this:"))
        assertTrue(block.contains("signing continuity"))
    }

    // --- C01: an applicable prerequisite already evident in the request is NOT a caution ---

    @Test
    fun buildPreflightBlock_applicablePrerequisiteEvident_isNotRenderedAsCaution() {
        val episode = episode(
            id = 1L,
            taskSummary = "in-place apk update failed",
            requiredPrerequisites = listOf("signing continuity")
        )

        val block = TaskPreflightFormatter.buildPreflightBlock(
            episodes = listOf(episode),
            currentTaskContext = "perform an in-place apk update; signing continuity is already configured",
            useEnglish = true
        )

        assertFalse(block.contains("verify before relying on this:"))
        assertFalse(block.contains("signing continuity"))
    }

    // --- C01: a non-applicable prerequisite is not rendered at all ---

    @Test
    fun buildPreflightBlock_nonApplicablePrerequisite_isNotRendered() {
        val episode = episode(
            id = 1L,
            taskSummary = "in-place apk update failed",
            requiredPrerequisites = listOf("signing continuity")
        )

        val block = TaskPreflightFormatter.buildPreflightBlock(
            episodes = listOf(episode),
            currentTaskContext = "perform a fresh installation on a clean device",
            useEnglish = true
        )

        assertFalse(block.contains("signing continuity"))
        assertFalse(block.contains("verify before relying on this:"))
    }

    // --- C01: the block stays bounded in episodes, prerequisite lines and total characters ---

    @Test
    fun buildPreflightBlock_manyEpisodesAndPrerequisites_staysBounded() {
        val episodes = (1L..20L).map { id ->
            episode(
                id = id,
                taskSummary = "in-place apk update failed " + "x".repeat(400),
                resolutionSummary = "y".repeat(400),
                causeSummary = "z".repeat(400),
                requiredPrerequisites = (1..10).map { "signing continuity variant $it" }
            )
        }

        val block = TaskPreflightFormatter.buildPreflightBlock(
            episodes = episodes,
            currentTaskContext = "perform an in-place apk update; verify signing and continuity of the release key",
            useEnglish = true
        )

        assertTrue(block.length <= 2000)
        val episodeLines = block.lines().count { it.startsWith("- [") }
        assertTrue(episodeLines <= 5)
        val cautionLines = block.lines().count { it.contains("verify before relying on this:") }
        assertTrue(cautionLines <= 5)
        // At most MAX_PREREQUISITES_PER_EPISODE (3) prerequisites per caution line.
        block.lines()
            .filter { it.contains("verify before relying on this:") }
            .forEach { line ->
                val rendered = line.substringAfter("verify before relying on this: ")
                assertTrue(rendered.split("; ").size <= 3)
            }
    }

    // --- C01: framing is a caution, never an imperative user mandate ---

    @Test
    fun buildPreflightBlock_neverUsesImperativeMandatePhrasing() {
        val episode = episode(
            id = 1L,
            taskSummary = "in-place apk update failed",
            requiredPrerequisites = listOf("signing continuity")
        )

        val english = TaskPreflightFormatter.buildPreflightBlock(
            episodes = listOf(episode),
            currentTaskContext = "perform an in-place apk update",
            useEnglish = true
        )
        val chinese = TaskPreflightFormatter.buildPreflightBlock(
            episodes = listOf(episode),
            currentTaskContext = "perform an in-place apk update",
            useEnglish = false
        )

        assertFalse(english.lowercase().contains("you must"))
        assertFalse(chinese.contains("必须"))
        assertTrue(english.contains("verify applicability"))
        assertTrue(chinese.contains("请自行核对适用性"))
    }

    // --- C01: the Chinese block uses the Chinese caution label, never an imperative mandate ---

    @Test
    fun buildPreflightBlock_chineseFraming_rendersChineseCautionLabel() {
        // C01: the Chinese block uses the Chinese caution label and never an imperative mandate.
        val episode = episode(
            id = 1L,
            taskSummary = "in-place apk update failed",
            requiredPrerequisites = listOf("signing continuity")
        )
        val block = TaskPreflightFormatter.buildPreflightBlock(
            episodes = listOf(episode),
            currentTaskContext = "perform an in-place apk update; verify signing and continuity of the release key",
            useEnglish = false
        )
        assertTrue(block.contains("依赖前请先核实:"))
        assertFalse(block.contains("必须"))
    }

    private fun episode(
        id: Long = 0L,
        taskSummary: String = "",
        resolutionSummary: String = "",
        causeSummary: String = "",
        requiredPrerequisites: List<String> = emptyList()
    ): TaskEpisode = TaskEpisode(
        id = id,
        taskSummary = taskSummary,
        outcome = TaskEpisode.OUTCOME_FAILURE,
        causeSummary = causeSummary,
        resolutionSummary = resolutionSummary,
        requiredPrerequisites = requiredPrerequisites
    )
}
