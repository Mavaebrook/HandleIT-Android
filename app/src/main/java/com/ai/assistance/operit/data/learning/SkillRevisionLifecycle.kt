package com.ai.assistance.operit.data.learning

/**
 * Pure promotion lifecycle and constraint-preservation guard for skill revisions.
 *
 * Rules (docs/TODO/reliable-learning-architect-20261016/01-design.md §4):
 *  - explicit user constraints are authoritative and may never be weakened or overridden by a
 *    reviewer or teacher inference;
 *  - teacher-generated inferences remain candidates until evaluated and promoted;
 *  - promotion requires independent evidence and no harmful outcomes;
 *  - prior versions are retained and rollback is always possible.
 */
object SkillRevisionLifecycle {

    const val MIN_INDEPENDENT_EVIDENCE = 1

    /**
     * True when [candidate] preserves every user constraint present in [authoritativeConstraints].
     * A constraint is preserved when it still appears (normalized) in the candidate's
     * userConstraints. Missing constraints mean the candidate weakened/overrode them.
     */
    fun preservesUserConstraints(
        authoritativeConstraints: List<String>,
        candidate: SkillRevision
    ): Boolean {
        if (authoritativeConstraints.isEmpty()) return true
        val candidateKeys = candidate.userConstraints.map { normalize(it) }.toHashSet()
        return authoritativeConstraints.all { normalize(it) in candidateKeys }
    }

    /**
     * Evaluate a candidate revision against observed outcomes. A candidate passes only when it
     * preserves user constraints, has at least MIN_INDEPENDENT_EVIDENCE independent evidence
     * sources, and has no harmful outcomes.
     */
    fun evaluate(
        candidate: SkillRevision,
        authoritativeConstraints: List<String>,
        independentEvidenceCount: Int,
        helpfulCount: Int,
        harmfulCount: Int
    ): SkillRevisionEvaluation {
        val preserves = preservesUserConstraints(authoritativeConstraints, candidate)
        val hasEvidence = independentEvidenceCount >= MIN_INDEPENDENT_EVIDENCE
        val noHarm = harmfulCount <= 0
        val passed = preserves && hasEvidence && noHarm
        val reason = when {
            !preserves -> "rejected: candidate weakens or overrides an explicit user constraint"
            !hasEvidence -> "rejected: no independent evidence"
            !noHarm -> "rejected: harmful outcomes recorded ($harmfulCount)"
            else -> "passed: constraints preserved, evidence present, no harmful outcomes"
        }
        return SkillRevisionEvaluation(
            revisionId = candidate.id,
            independentEvidenceCount = independentEvidenceCount,
            helpfulCount = helpfulCount,
            harmfulCount = harmfulCount,
            preservesUserConstraints = preserves,
            passed = passed,
            reason = reason
        )
    }

    /**
     * Apply an evaluation to a candidate, returning the candidate with its new status.
     * A passing evaluation promotes; a failing evaluation rejects. The prior version is never
     * mutated here (rollback is achieved by keeping the parent revision).
     */
    fun applyEvaluation(candidate: SkillRevision, evaluation: SkillRevisionEvaluation): SkillRevision {
        val newStatus = if (evaluation.passed) SkillRevision.STATUS_PROMOTED else SkillRevision.STATUS_REJECTED
        return candidate.copy(
            status = newStatus,
            helpfulCount = evaluation.helpfulCount,
            harmfulCount = evaluation.harmfulCount
        )
    }

    /**
     * Rollback target: the parent version of a promoted revision, or null when there is none.
     * Rollback is always possible because prior versions are retained.
     */
    fun rollbackTarget(candidate: SkillRevision, allRevisions: List<SkillRevision>): SkillRevision? {
        if (candidate.parentVersion <= 0) return null
        return allRevisions.firstOrNull {
            it.skillId == candidate.skillId && it.version == candidate.parentVersion
        }
    }

    private fun normalize(value: String): String = value.lowercase().trim().replace(Regex("\\s+"), " ")
}
