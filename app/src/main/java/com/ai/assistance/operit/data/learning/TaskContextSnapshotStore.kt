package com.ai.assistance.operit.data.learning

/**
 * Bounded, in-memory, scoped store of [ObservationRecord]s (Step 1, Area 2).
 *
 *  - The clock is injected so expiry is testable with a fake clock. It is the single time source.
 *  - Records are capped at [maxRecords]; the oldest are evicted first.
 *  - String payloads are truncated to [MAX_STRING_VALUE_CHARS] so no unbounded text is retained.
 *  - Persistence is deferred: this store is in-memory only, so a process restart means every fact is
 *    unknown again.
 *  - Independent observations on different resources are separate records keyed by eventId; they do
 *    not overwrite each other.
 *
 * ## Deterministic TTL
 *
 * Every record is bounded by [ttlMs] measured from its [ObservationRecord.observedAtMs]. The store is
 * the authority for this default bound; producers do not need to know it.
 *
 *  - When a record is recorded with `validUntilMs == 0` (no explicit expiry), the effective expiry is
 *    `observedAtMs + ttlMs`.
 *  - When a record carries an explicit `validUntilMs != 0`, the effective expiry is the EARLIER of the
 *    explicit value and `observedAtMs + ttlMs`. An explicit expiry can only shorten the window, never
 *    extend it beyond the default bound.
 *  - The effective expiry is stored back onto the record (via [ObservationRecord.copy]) so that
 *    [snapshotFor] observes it.
 *  - If `observedAtMs + ttlMs` overflows [Long], it is clamped to [Long.MAX_VALUE] (never wraps
 *    negative).
 *
 * ## Time domain and fail-closed rules
 *
 * [ObservationRecord.observedAtMs] and [ObservationRecord.validUntilMs] are wall-clock epoch
 * milliseconds (the provenance domain); monotonic time is deliberately NOT used here. Because the
 * clock is wall-clock, it can move backwards:
 *
 *  - If `observedAtMs > now` (i.e. `now < observedAt`, a clock rollback or a future-dated record), the
 *    record is treated as invalid and is excluded from [snapshotFor]. This fails closed.
 *
 * ## Expiry boundary
 *
 * A record is expired when `now >= validUntilMs`: a record that is valid until `T` is NOT valid AT
 * `T`. Equivalently, a record is valid strictly before its effective expiry.
 *
 * ## Thread safety
 *
 * The store is shared across services, so all public operations are `@Synchronized`.
 */
class TaskContextSnapshotStore(
    private val clock: () -> Long,
    private val maxRecords: Int = 200,
    private val ttlMs: Long = 30L * 60L * 1000L
) {
    private val records = ArrayDeque<ObservationRecord>()

    /**
     * Append an observation, applying the payload cap, the bounded default TTL, and evicting the
     * oldest beyond [maxRecords].
     *
     * The stored record's [ObservationRecord.validUntilMs] is rewritten to the effective expiry
     * (see the class KDoc): `observedAtMs + ttlMs` when no explicit expiry is given, otherwise the
     * earlier of the explicit expiry and `observedAtMs + ttlMs`.
     */
    @Synchronized
    fun record(observation: ObservationRecord) {
        records.addLast(capPayload(applyBoundedTtl(observation)))
        while (records.size > maxRecords) {
            records.removeFirst()
        }
    }

    /**
     * A snapshot of the applicable, non-expired observations for the given scope coordinates.
     * Observations whose scope/executor/environment/session do not match are excluded, as are
     * expired records and records whose observation time is in the future relative to the clock.
     */
    @Synchronized
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
    @Synchronized
    fun invalidateSession(sessionId: String) {
        records.removeAll { it.sessionId == sessionId }
    }

    /** Drop every record for [environment]; used when the environment/workspace changes. */
    @Synchronized
    fun invalidateEnvironment(environment: String) {
        records.removeAll { it.environment == environment }
    }

    /** Drop every record. */
    @Synchronized
    fun clear() {
        records.clear()
    }

    /**
     * True when [record] must be excluded from a snapshot at [nowMs].
     *
     * Fails closed for future observation times: if `observedAtMs > nowMs` (clock rollback or a
     * future-dated record) the record is treated as expired/invalid.
     *
     * Expiry boundary: expired when `nowMs >= validUntilMs` (valid strictly before the expiry).
     */
    private fun isExpired(record: ObservationRecord, nowMs: Long): Boolean {
        if (record.observedAtMs > nowMs) return true
        return nowMs >= record.validUntilMs
    }

    /**
     * Compute and store the effective expiry on [observation] (see the class KDoc). The default bound
     * is `observedAtMs + ttlMs`, clamped to [Long.MAX_VALUE] on overflow; an explicit expiry can only
     * shorten it.
     */
    private fun applyBoundedTtl(observation: ObservationRecord): ObservationRecord {
        val defaultExpiry = saturatedAdd(observation.observedAtMs, ttlMs)
        val explicit = observation.validUntilMs
        val effective = if (explicit == 0L) defaultExpiry else minOf(explicit, defaultExpiry)
        return if (effective == explicit) observation else observation.copy(validUntilMs = effective)
    }

    /** `a + b` clamped to [Long.MAX_VALUE] instead of wrapping negative. */
    private fun saturatedAdd(a: Long, b: Long): Long {
        val sum = a + b
        // Overflow iff both operands have the same sign and the sum's sign differs.
        return if (((a xor sum) and (b xor sum)) < 0L) Long.MAX_VALUE else sum
    }

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
