package com.ai.assistance.operit.data.repository

/**
 * Pure scope/active-state filter for memory graph expansion and final retrieval.
 *
 * A memory may only participate in normal active retrieval when it is BOTH:
 *  - in the allowed scope (same folder/time window as the query), and
 *  - active (not superseded/inactive).
 *
 * Obsolete or out-of-scope memories are retained in the database for deliberate
 * historical inspection, but must never be resurrected into normal results through
 * graph links (outgoing or backlink).
 */
object MemoryGraphScopeFilter {

    /**
     * @param allowedScopedIds ids of memories that are in scope AND active for this query.
     * @param candidateId the id of a graph-expansion target or a final-result candidate.
     * @return true when the candidate may be included in normal active retrieval.
     */
    fun isAllowed(allowedScopedIds: Set<Long>, candidateId: Long): Boolean {
        return allowedScopedIds.contains(candidateId)
    }

    /**
     * Decide whether a graph-expansion target should receive propagated score.
     * Both outgoing-link targets and backlink sources must pass this check.
     */
    fun shouldPropagateTo(allowedScopedIds: Set<Long>, targetId: Long): Boolean {
        return isAllowed(allowedScopedIds, targetId)
    }

    /**
     * Filter a ranked list of memory ids down to those allowed in normal active retrieval,
     * preserving the original order. Used on the final result path.
     */
    fun filterAllowedIds(allowedScopedIds: Set<Long>, rankedIds: List<Long>): List<Long> {
        return rankedIds.filter { isAllowed(allowedScopedIds, it) }
    }
}
