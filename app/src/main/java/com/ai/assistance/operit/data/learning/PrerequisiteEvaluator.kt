package com.ai.assistance.operit.data.learning

/**
 * Three-valued evaluation of a supported typed predicate against a [TaskContextSnapshot]
 * (Step 1, Area 2).
 *
 * This applies ONLY to supported structured claims. Existing free-text prerequisites are NOT
 * automatically converted into typed predicates; this evaluator does not pretend they have been.
 * The result is advisory and is NOT an execution authorization gate.
 */
enum class EvaluationResult {
    SATISFIED,
    CONTRADICTED,
    UNKNOWN
}

object PrerequisiteEvaluator {

    /**
     * Evaluate [predicate] about [subject] against [snapshot] at [nowMs].
     *
     * Rules:
     *  - no matching, applicable, non-expired observation -> [EvaluationResult.UNKNOWN];
     *  - all matching observations agree on BooleanValue(true) -> [EvaluationResult.SATISFIED];
     *  - all matching observations agree on BooleanValue(false) -> [EvaluationResult.CONTRADICTED];
     *  - contradictory equally-applicable evidence (both true and false present) -> UNKNOWN (a
     *    conflict is not resolved by newest-wins);
     *  - expired observations (nowMs >= validUntilMs, or a future observedAtMs) are ignored; a
     *    stored record never carries validUntilMs == 0 because the store rewrites it to a bounded
     *    value;
     *  - non-boolean or [TypedValue.Unknown] values -> UNKNOWN.
     *
     * Only [TrustLevel.OBSERVED] records count: a REPORTED or REQUESTED claim cannot satisfy an
     * observed predicate.
     *
     * Only records whose scope/executor/environment/session match the snapshot's coordinates are
     * considered: a record from a different scope is not applicable and yields UNKNOWN.
     */
    fun evaluate(
        predicate: String,
        subject: String,
        snapshot: TaskContextSnapshot,
        nowMs: Long
    ): EvaluationResult {
        val matching = snapshot.observations.filter { record ->
            record.predicate == predicate &&
                record.subject == subject &&
                record.trust == TrustLevel.OBSERVED &&
                record.memorySpace == snapshot.memorySpace &&
                record.projectScope == snapshot.projectScope &&
                record.taskScope == snapshot.taskScope &&
                record.executorId == snapshot.executorId &&
                record.environment == snapshot.environment &&
                record.sessionId == snapshot.sessionId &&
                !isExpired(record, nowMs)
        }
        if (matching.isEmpty()) return EvaluationResult.UNKNOWN

        var sawTrue = false
        var sawFalse = false
        for (record in matching) {
            when (val value = record.value) {
                is TypedValue.BooleanValue -> {
                    if (value.value) sawTrue = true else sawFalse = true
                }
                else -> return EvaluationResult.UNKNOWN
            }
        }
        return when {
            sawTrue && sawFalse -> EvaluationResult.UNKNOWN
            sawTrue -> EvaluationResult.SATISFIED
            sawFalse -> EvaluationResult.CONTRADICTED
            else -> EvaluationResult.UNKNOWN
        }
    }

    private fun isExpired(record: ObservationRecord, nowMs: Long): Boolean {
        // Match TaskContextSnapshotStore's boundary exactly so the evaluator cannot reauthorize a
        // record the store already treats as stale: expired when now >= validUntilMs, and fail
        // closed for a future observation time (now < observedAt). A stored record never carries
        // validUntilMs == 0 (the store rewrites it to a bounded value), so 0 is treated as expired
        // here rather than as "never expires".
        if (record.observedAtMs > nowMs) return true
        return nowMs >= record.validUntilMs
    }
}
