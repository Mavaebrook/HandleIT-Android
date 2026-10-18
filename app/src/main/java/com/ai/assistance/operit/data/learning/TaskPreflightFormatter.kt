package com.ai.assistance.operit.data.learning

import com.ai.assistance.operit.data.model.TaskEpisode

/**
 * Pure formatter for the bounded similar-task preflight block injected into the system prompt
 * for development/workspace tasks. No model call is made here; this is deterministic retrieval
 * output only, bounded in size.
 *
 * R16: retrieval relevance is NOT lesson confidence and NOT promotion evidence. Retrieved
 * episodes are prior similar-task lessons whose applicability must be verified by the model;
 * they are never presented as verified facts, and the block never issues new user mandates.
 */
object TaskPreflightFormatter {

    /** Maximum number of retrieved episodes rendered in one block. */
    private const val MAX_EPISODES_IN_BLOCK = 5

    /** Maximum characters kept per rendered field (task / resolution / cause / prerequisite). */
    private const val MAX_CHARS_PER_FIELD = 240

    /**
     * Maximum number of "verify before relying on this" prerequisite lines rendered per episode.
     * Keeps the block bounded when an episode carries many required prerequisites.
     */
    private const val MAX_PREREQUISITES_PER_EPISODE = 3

    /**
     * Hard cap on the total assembled block length in characters. The block is truncated to this
     * length as a last step so the injected system prompt stays bounded regardless of input.
     */
    private const val MAX_BLOCK_CHARS = 2000

    /**
     * Build a compact preflight block from retrieved episodes and the current task context.
     * Returns an empty string when there is nothing useful to show.
     *
     * Prerequisite handling (deterministic, no model call):
     *  - a prerequisite that does NOT apply to the present task is not rendered;
     *  - a prerequisite that applies AND is evidently covered by the request text
     *    ([TaskEpisodeRetrieval.satisfiedPrerequisites]) is not rendered as a caution;
     *  - a prerequisite that applies and is NOT evident in the request is rendered as a
     *    "verify before relying on this" caution.
     *
     * @param episodes bounded, already-ranked relevant episodes (top-k).
     * @param currentTaskContext short description of the present task, used for applicability.
     * @param useEnglish language for the block.
     */
    fun buildPreflightBlock(
        episodes: List<TaskEpisode>,
        currentTaskContext: String,
        useEnglish: Boolean
    ): String {
        if (episodes.isEmpty()) return ""
        val bounded = episodes.take(MAX_EPISODES_IN_BLOCK)
        val lines = mutableListOf<String>()
        bounded.forEach { episode ->
            val task = truncate(episode.taskSummary)
            if (task.isEmpty()) return@forEach
            val outcome = episode.outcome
            val resolution = truncate(episode.resolutionSummary)
            val cause = truncate(episode.causeSummary)
            val causeLabel = if (episode.causeIsHypothesis) "hypothesis" else "cause"
            lines.add("- [$outcome] $task" + if (resolution.isNotEmpty()) " -> $resolution" else "")
            if (cause.isNotEmpty()) {
                lines.add("    $causeLabel: $cause")
            }
            val applicablePrereqs = episode.requiredPrerequisites.filter { prereq ->
                TaskEpisodeRetrieval.isPrerequisiteApplicable(prereq, currentTaskContext)
            }
            val satisfiedPrereqs =
                TaskEpisodeRetrieval.satisfiedPrerequisites(applicablePrereqs, currentTaskContext)
            val missingPrereqs =
                TaskEpisodeRetrieval.missingPrerequisites(applicablePrereqs, satisfiedPrereqs)
                    .take(MAX_PREREQUISITES_PER_EPISODE)
            if (missingPrereqs.isNotEmpty()) {
                val label = if (useEnglish) {
                    "verify before relying on this: "
                } else {
                    "依赖前请先核实: "
                }
                lines.add("    $label" + missingPrereqs.joinToString("; ") { truncate(it) })
            }
        }
        if (lines.isEmpty()) return ""
        val header = if (useEnglish) {
            "[Prior similar-task lessons — bounded retrieval, verify applicability]"
        } else {
            "[相似历史任务经验 — 有界检索，请自行核对适用性]"
        }
        val block = header + "\n" + lines.joinToString("\n")
        return if (block.length <= MAX_BLOCK_CHARS) block else block.take(MAX_BLOCK_CHARS)
    }

    private fun truncate(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.length <= MAX_CHARS_PER_FIELD) trimmed else trimmed.take(MAX_CHARS_PER_FIELD) + "…"
    }
}
