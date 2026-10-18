package com.ai.assistance.operit.data.learning

/**
 * Bounded, in-memory, scoped store of [ObservationRecord]s (Step 1, Area 2).
 *
 *  - The clock is injected so expiry is testable with a fake clock.
 *  - Records are capped at [maxRecords]; the oldest are evicted first.
 *  - String payloads are truncated to [MAX_STRING_VALUE_CHARS] so no unbounded text is retained.
 *  - Persistence is deferred: this store is in-memory only, so a process restart means every fact is
 *    unknown again.
 *  - Independent observations on different resources are separate records keyed by eventId; they do
 *    not overwrite each other.
 */
class TaskContextSnapshotStore(
    private val clock: () -> Long,
    private val maxRecords: Int = 200,
    private val ttlMs: Long = 30L * 60L * 1000L
) {
    private val records = ArrayDeque<ObservationRecord>()

    /** Append an observation, applying the payload cap and evicting the oldest beyond [maxRecords]. */
    fun record(observation: ObservationRecord) {
        records.addLast(capPayload(observation))
        while (records.size > maxRecords) {
            records.removeFirst()
        }
    }

    /**
     * A snapshot of the applicable, non-expired observations for the given scope coordinates.
     * Observations whose scope/executor/environment/session do not match are excluded.
     */
    fun snapshotFor(
        memorySpace: String,
        projectScope: String,
        taskScope: String,
        executorId: String,
        environment: String,
        sessionId: String
    ): TaskContextSnapshot {
        val now = clock()
        val applicable = records.filter { record ->
            record.memorySpace == memorySpace &&
                record.projectScope == projectScope &&
                record.taskScope == taskScope &&
                record.executorId == executorId &&
                record.environment == environment &&
                record.sessionId == sessionId &&
                !isExpired(record, now)
        }
        return TaskContextSnapshot(
            memorySpace = memorySpace,
            projectScope = projectScope,
            taskScope = taskScope,
            executorId = executorId,
            environment = environment,
            sessionId = sessionId,
            observations = applicable,
            capturedAtMs = now
        )
    }

    /** Drop every record for [sessionId]; used when the session changes. */
    fun invalidateSession(sessionId: String) {
        records.removeAll { it.sessionId == sessionId }
    }

    /** Drop every record for [environment]; used when the environment/workspace changes. */
    fun invalidateEnvironment(environment: String) {
        records.removeAll { it.environment == environment }
    }

    /** Drop every record. */
    fun clear() {
        records.clear()
    }

    private fun isExpired(record: ObservationRecord, nowMs: Long): Boolean =
        record.validUntilMs != 0L && nowMs > record.validUntilMs

    private fun capPayload(observation: ObservationRecord): ObservationRecord {
        val value = observation.value
        if (value is TypedValue.StringValue && value.value.length > MAX_STRING_VALUE_CHARS) {
            return observation.copy(value = TypedValue.StringValue(value.value.take(MAX_STRING_VALUE_CHARS)))
        }
        return observation
    }

    companion object {
        /** Maximum retained characters of a string-typed value. */
        const val MAX_STRING_VALUE_CHARS = 512
    }
}
