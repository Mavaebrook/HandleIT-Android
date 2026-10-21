package com.ai.assistance.operit.data.repository

/**
 * Pure, Android-free governance policy for retiring (deactivating) a memory.
 *
 * This object exists so the retirement decision can be unit-tested on the JVM: the repositories
 * need a real ObjectBox store and cannot be instantiated in `app/src/test` (there is no
 * Robolectric in this project). The production call site delegates its retirement decision to
 * this object, so the tested logic is the logic that actually runs.
 *
 * Core invariant: model output cannot grant permission. This policy only ever REDUCES the set of
 * retirements — it never expands it. Every branch either leaves the memory active or defers the
 * decision; the single path to [RetirementDecision.Retire] is [RetirementReason.ACCEPTED].
 * Insufficient evidence yields [RetirementDecision.PendingDecision], never a default retirement.
 *
 * Note on timestamps: a timestamp alone is not proof of semantic contradiction. A newer "target"
 * only tells us the ordering of writes, not that the source genuinely corrects it. That is why
 * step 8 requires explicit [RetirementCandidate.correctionApplicable] evidence and otherwise
 * returns [RetirementDecision.PendingDecision].
 */
object MemoryRetirementGovernance {

    /** Link types this policy is willing to consider as a retirement justification. */
    val SUPPORTED_LINK_TYPES = setOf("CORRECTS", "SUPERSEDES")

    /** Why a candidate was retired, left active, or deferred. */
    enum class RetirementReason {
        ACCEPTED,
        SELF_LINK,
        ALREADY_INACTIVE,
        AMBIGUOUS_TARGET,
        REVERSED_DIRECTION,
        TARGET_NEWER_THAN_SOURCE,
        NO_APPLICABLE_CORRECTION,
        PROTECTED_USER_CONSTRAINT,
        INSUFFICIENT_EVIDENCE
    }

    /** Outcome of evaluating one retirement candidate. */
    sealed class RetirementDecision {
        data class Retire(val reason: RetirementReason) : RetirementDecision()
        data class LeaveActive(val reason: RetirementReason) : RetirementDecision()
        data class PendingDecision(val reason: RetirementReason) : RetirementDecision()
    }

    /**
     * Plain facts about one candidate retirement. Deliberately free of Memory/entity types so the
     * decision can be exercised without an ObjectBox store.
     *
     * @param targetTitleMatchCount how many memories share the target title; anything other than
     * exactly 1 means the target cannot be identified unambiguously.
     * @param targetProtectedByActiveUserConstraint the target is pinned by a live user constraint.
     * @param correctionApplicable caller-observed evidence that the correction genuinely applies.
     */
    data class RetirementCandidate(
        val sourceId: Long,
        val sourceTitle: String,
        val sourceCreatedAtMs: Long,
        val targetId: Long,
        val targetTitle: String,
        val targetCreatedAtMs: Long,
        val targetStatus: String,
        val linkType: String,
        val targetTitleMatchCount: Int,
        val targetProtectedByActiveUserConstraint: Boolean,
        val correctionApplicable: Boolean
    )

    /**
     * Decide whether a candidate may be retired. Steps are evaluated in this exact precedence and
     * the first match wins, so a later permissive signal can never override an earlier refusal.
     *
     * 1. self-link (same id or same title) -> [RetirementDecision.LeaveActive]
     * 2. target already inactive -> [RetirementDecision.LeaveActive]
     * 3. target not uniquely identifiable -> [RetirementDecision.PendingDecision]
     * 4. target protected by an active user constraint -> [RetirementDecision.LeaveActive]
     * 5. target newer than source (direction reversed) -> [RetirementDecision.LeaveActive]
     * 6. equal timestamps (no ordering evidence) -> [RetirementDecision.LeaveActive]
     * 7. unsupported link type -> [RetirementDecision.LeaveActive]
     * 8. no explicit correction evidence -> [RetirementDecision.PendingDecision]
     * 9. otherwise -> [RetirementDecision.Retire] with [RetirementReason.ACCEPTED]
     */
    fun decide(candidate: RetirementCandidate): RetirementDecision {
        // 1. A memory can never retire itself, by id or by title.
        if (candidate.sourceId == candidate.targetId ||
            candidate.sourceTitle.trim() == candidate.targetTitle.trim()
        ) {
            return RetirementDecision.LeaveActive(RetirementReason.SELF_LINK)
        }

        // 2. An already-inactive memory must not be retired again: re-retiring would overwrite
        // the recorded prior status and corrupt the audit chain.
        if (candidate.targetStatus == "INACTIVE") {
            return RetirementDecision.LeaveActive(RetirementReason.ALREADY_INACTIVE)
        }

        // 3. 0 or >1 matches means we cannot identify the target unambiguously.
        if (candidate.targetTitleMatchCount != 1) {
            return RetirementDecision.PendingDecision(RetirementReason.AMBIGUOUS_TARGET)
        }

        // 4. A live user constraint outranks any model-proposed retirement.
        if (candidate.targetProtectedByActiveUserConstraint) {
            return RetirementDecision.LeaveActive(RetirementReason.PROTECTED_USER_CONSTRAINT)
        }

        // 5. The "target" is newer than the "correction": the direction is reversed.
        if (candidate.targetCreatedAtMs > candidate.sourceCreatedAtMs) {
            return RetirementDecision.LeaveActive(RetirementReason.TARGET_NEWER_THAN_SOURCE)
        }

        // 6. No ordering evidence; we cannot establish direction.
        if (candidate.targetCreatedAtMs == candidate.sourceCreatedAtMs) {
            return RetirementDecision.LeaveActive(RetirementReason.REVERSED_DIRECTION)
        }

        // 7. Only correction-style links can justify a retirement.
        if (!SUPPORTED_LINK_TYPES.contains(candidate.linkType)) {
            return RetirementDecision.LeaveActive(RetirementReason.NO_APPLICABLE_CORRECTION)
        }

        // 8. A timestamp is not proof of semantic contradiction; require explicit evidence.
        if (!candidate.correctionApplicable) {
            return RetirementDecision.PendingDecision(RetirementReason.INSUFFICIENT_EVIDENCE)
        }

        // 9. The only path to Retire, and it is always ACCEPTED.
        return RetirementDecision.Retire(RetirementReason.ACCEPTED)
    }

    /** True only when the decision actually retires the memory. */
    fun isRetirement(decision: RetirementDecision): Boolean = decision is RetirementDecision.Retire

    /** Stable string reason for storage, e.g. "ACCEPTED" or "AMBIGUOUS_TARGET". */
    fun reasonCode(decision: RetirementDecision): String {
        return when (decision) {
            is RetirementDecision.Retire -> decision.reason.name
            is RetirementDecision.LeaveActive -> decision.reason.name
            is RetirementDecision.PendingDecision -> decision.reason.name
        }
    }
}
