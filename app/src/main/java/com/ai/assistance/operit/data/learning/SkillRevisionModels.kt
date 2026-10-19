package com.ai.assistance.operit.data.learning

/**
 * A versioned, candidate skill revision.
 *
 * Design goals (docs/TODO/reliable-learning-architect-20261016/01-design.md §4):
 *  - a revision records scope, prerequisites, steps, checks, pitfalls, evidence, parent version
 *    and promotion status;
 *  - explicit user instructions become applicable constraints immediately; teacher-generated
 *    inferences remain candidates;
 *  - no reviewer may weaken or override user constraints;
 *  - promotion lifecycle: draft -> evaluate -> promote or reject, with prior version retained
 *    and explicit rollback.
 *
 * @property listIntegrity Non-persisted integrity metadata for the list fields of the row this
 *   revision was read from. It is NOT written to ObjectBox and does NOT change the entity schema; it
 *   exists only so a consumer can distinguish a genuine empty list from a field whose stored bytes
 *   could not be resolved (corrupt, unsupported version, or ambiguous provenance). It defaults to
 *   [RowListIntegrity.HEALTHY] for revisions that were not read from storage.
 */
data class SkillRevision(
    val id: Long = 0L,
    val skillId: String = "",
    val version: Int = 1,
    val parentVersion: Int = 0,
    val scope: String = "",
    val prerequisites: List<String> = emptyList(),
    val steps: List<String> = emptyList(),
    val checks: List<String> = emptyList(),
    val pitfalls: List<String> = emptyList(),
    val evidenceKeys: List<String> = emptyList(),
    val userConstraints: List<String> = emptyList(),
    val status: String = STATUS_DRAFT,
    val helpfulCount: Int = 0,
    val harmfulCount: Int = 0,
    val createdAtMs: Long = 0L,
    val listIntegrity: RowListIntegrity = RowListIntegrity.HEALTHY
) {
    companion object {
        const val STATUS_DRAFT = "draft"
        const val STATUS_EVALUATED = "evaluated"
        const val STATUS_PROMOTED = "promoted"
        const val STATUS_REJECTED = "rejected"
    }
}

/** Outcome of an evaluation of a candidate revision against task outcomes. */
data class SkillRevisionEvaluation(
    val revisionId: Long,
    val independentEvidenceCount: Int,
    val helpfulCount: Int,
    val harmfulCount: Int,
    val preservesUserConstraints: Boolean,
    val passed: Boolean,
    val reason: String
)
