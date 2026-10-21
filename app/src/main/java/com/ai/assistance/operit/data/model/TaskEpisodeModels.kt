package com.ai.assistance.operit.data.model

import com.ai.assistance.operit.data.learning.RowListIntegrity

/**
 * Compact, structured record of a past task episode.
 *
 * Design goals (docs/TODO/reliable-learning-architect-20261016/01-design.md §3):
 *  - capture task/context, outcome, failure, confirmed cause OR explicitly-marked hypothesis,
 *    resolution, required prerequisites, applicable constraints, source evidence, related skill version;
 *  - avoid secrets, private payload dumps, and full transcript retention.
 *
 * @property listIntegrity Non-persisted integrity metadata for the list fields of the row this
 *   episode was read from. It is NOT written to ObjectBox and does NOT change the entity schema; it
 *   exists only so a consumer can distinguish a genuine empty list from a field whose stored bytes
 *   could not be resolved (corrupt, unsupported version, or ambiguous provenance). It defaults to
 *   [RowListIntegrity.HEALTHY] for episodes that were not read from storage.
 */
data class TaskEpisode(
    val id: Long = 0L,
    val taskSummary: String = "",
    val contextSummary: String = "",
    val outcome: String = OUTCOME_UNKNOWN,
    val failureSummary: String = "",
    val causeSummary: String = "",
    val causeIsHypothesis: Boolean = false,
    val resolutionSummary: String = "",
    val requiredPrerequisites: List<String> = emptyList(),
    val applicableConstraints: List<String> = emptyList(),
    val evidenceKeys: List<String> = emptyList(),
    val relatedSkillVersion: String = "",
    val createdAtMs: Long = 0L,
    val listIntegrity: RowListIntegrity = RowListIntegrity.HEALTHY
) {
    companion object {
        const val OUTCOME_SUCCESS = "success"
        const val OUTCOME_FAILURE = "failure"
        const val OUTCOME_PARTIAL = "partial"
        const val OUTCOME_UNKNOWN = "unknown"
    }
}
