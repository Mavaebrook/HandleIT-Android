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
 *
 * FAIL-CLOSED ON UNRESOLVED STORAGE (Step 1, Area C): a revision read from a row whose list fields
 * could not be decoded carries `emptyList()` for those fields. An empty list is indistinguishable
 * from a genuinely empty one at the domain level, so a candidate with an unresolved field could
 * otherwise appear to "preserve" constraints it never had. Every entry point here therefore refuses
 * to evaluate a revision with any unresolved list field.
 */
object SkillRevisionLifecycle {

    const val MIN_INDEPENDENT_EVIDENCE = 1

    /** True when [revision] has any unresolved list field. */
    fun hasUnresolvedFields(revision: SkillRevision): Boolean = revision.listIntegrity.hasUnresolved

    /**
     * True when [candidate] preserves every user constraint present in [authoritativeConstraints].
     * A constraint is preserved when it still appears (normalized) in the candidate's
     * userConstraints. Missing constraints mean the candidate weakened/overrode them.
     *
     * FAIL CLOSED: a candidate with ANY unresolved list field returns `false`. Its userConstraints
     * list may be empty only because the stored bytes could not be decoded, so constraint
     * preservation cannot be demonstrated. Healthy candidates keep the exact behaviour above.
     */
    fun preservesUserConstraints(
        authoritativeConstraints: List<String>,
        candidate: SkillRevision
    ): Boolean {
        if (candidate.listIntegrity.hasUnresolved) return false
        if (authoritativeConstraints.isEmpty()) return true
        val candidateKeys = candidate.userConstraints.map { normalize(it) }.toHashSet()
        return authoritativeConstraints.all { normalize(it) in candidateKeys }
    }

    /**
     * Evaluate a candidate revision against observed outcomes. A candidate passes only when it
     * preserves user constraints, has at least MIN_INDEPENDENT_EVIDENCE independent evidence
     * sources, and has no harmful outcomes.
     *
     * FAIL CLOSED: a candidate with any unresolved list field is rejected before any
     * constraint/evidence logic runs, with an explicit reason naming the unresolved fields.
     */
    fun evaluate(
        candidate: SkillRevision,
        authoritativeConstraints: List<String>,
        independentEvidenceCount: Int,
        helpfulCount: Int,
        harmfulCount: Int
    ): SkillRevisionEvaluation {
        if (candidate.listIntegrity.hasUnresolved) {
            val fields = candidate.listIntegrity.unresolvedFields.joinToString(", ") { it.fieldName }
            return SkillRevisionEvaluation(
                revisionId = candidate.id,
                independentEvidenceCount = independentEvidenceCount,
                helpfulCount = helpfulCount,
                harmfulCount = harmfulCount,
                preservesUserConstraints = false,
                passed = false,
                reason = "rejected: revision has unresolved list field(s) [$fields]; integrity must be resolved before evaluation"
            )
        }
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
     * Evaluate [candidate] against the constraints of [authoritativeRevision] (the baseline).
     *
     * This overload exists because the plain-list form above cannot tell whether the BASELINE was
     * itself read with unresolved list fields: an unresolved baseline yields an empty
     * userConstraints list, which would make every candidate look constraint-preserving. When the
     * authoritative revision has any unresolved list field the evaluation fails closed with an
     * explicit reason instead of silently comparing against an empty constraint set.
     */
    fun evaluate(
        candidate: SkillRevision,
        authoritativeRevision: SkillRevision,
        independentEvidenceCount: Int,
        helpfulCount: Int,
        harmfulCount: Int
    ): SkillRevisionEvaluation {
        if (authoritativeRevision.listIntegrity.hasUnresolved) {
            val fields = authoritativeRevision.listIntegrity.unresolvedFields.joinToString(", ") { it.fieldName }
            return SkillRevisionEvaluation(
                revisionId = candidate.id,
                independentEvidenceCount = independentEvidenceCount,
                helpfulCount = helpfulCount,
                harmfulCount = harmfulCount,
                preservesUserConstraints = false,
                passed = false,
                reason = "rejected: authoritative revision has unresolved list field(s) [$fields]; cannot verify constraint preservation"
            )
        }
        return evaluate(candidate, authoritativeRevision.userConstraints, independentEvidenceCount, helpfulCount, harmfulCount)
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
