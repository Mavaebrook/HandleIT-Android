package com.ai.assistance.operit.data.learning

import com.ai.assistance.operit.data.model.TaskEpisode

/**
 * Pure formatter for the bounded similar-task preflight block injected into the system prompt
 * for development/workspace tasks. No model call is made here; this is deterministic retrieval
 * output only, bounded in size.
 */
object TaskPreflightFormatter {

    private const val MAX_EPISODES_IN_BLOCK = 5
    private const val MAX_CHARS_PER_FIELD = 240

    /**
     * Build a compact preflight block from retrieved episodes and the current task context.
     * Returns an empty string when there is nothing useful to show.
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
            if (applicablePrereqs.isNotEmpty()) {
                lines.add("    prerequisites: " + applicablePrereqs.joinToString("; ") { truncate(it) })
            }
        }
        if (lines.isEmpty()) return ""
        val header = if (useEnglish) {
            "[Prior similar-task lessons — bounded retrieval, verify applicability]"
        } else {
            "[相似历史任务经验 — 有界检索，请自行核对适用性]"
        }
        return header + "\n" + lines.joinToString("\n")
    }

    private fun truncate(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.length <= MAX_CHARS_PER_FIELD) trimmed else trimmed.take(MAX_CHARS_PER_FIELD) + "…"
    }
}
