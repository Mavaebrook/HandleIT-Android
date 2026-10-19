package com.ai.assistance.operit.data.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the deterministic TTL enforced by [TaskContextSnapshotStore] (Step 1, Area 2).
 *
 * Pure JVM, JUnit4, no coroutines. Uses a mutable fake clock so expiry is fully deterministic.
 *
 * Expiry boundary rule under test: a record is expired when `now >= validUntilMs` (valid strictly
 * before its effective expiry).
 */
class TaskContextSnapshotStoreTtlTest {

    private var now = 1000L
    private val store = TaskContextSnapshotStore(clock = { now }, ttlMs = 100L)

    private fun obs(
        eventId: String = "e1",
        memorySpace: String = "a",
        observedAtMs: Long = 1000L,
        validUntilMs: Long = 0L
    ): ObservationRecord = ObservationRecord(
        eventId = eventId,
        sourceId = "src",
        memorySpace = memorySpace,
        projectScope = "proj",
        taskScope = "task",
        executorId = "exec",
        environment = "env",
        sessionId = "sess",
        predicate = "p",
        subject = "s",
        value = TypedValue.BooleanValue(true),
        observedAtMs = observedAtMs,
        validUntilMs = validUntilMs,
        source = ObservationSource.TOOL_EXECUTION,
        trust = TrustLevel.OBSERVED
    )

    private fun snapshot(memorySpace: String = "a"): TaskContextSnapshot =
        store.snapshotFor(memorySpace, "proj", "task", "exec", "env", "sess")

    @Test
    fun freshRecord_withinTtl_isIncluded() {
        store.record(obs(observedAtMs = 1000L))
        now = 1050L
        assertEquals(1, snapshot().observations.size)
    }

    @Test
    fun staleRecord_pastTtl_isExcluded() {
        store.record(obs(observedAtMs = 1000L))
        now = 1200L // > 1000 + 100
        assertEquals(0, snapshot().observations.size)
    }

    @Test
    fun defaultZeroValidUntil_getsBoundedTtl() {
        store.record(obs(observedAtMs = 1000L, validUntilMs = 0L))
        now = 1050L
        assertEquals(1, snapshot().observations.size)
        now = 1200L
        assertEquals(0, snapshot().observations.size)
    }

    @Test
    fun explicitEarlierExpiry_isHonored() {
        store.record(obs(observedAtMs = 1000L, validUntilMs = 1020L))
        now = 1030L // past explicit 1020, before default 1100
        assertEquals(0, snapshot().observations.size)
    }

    @Test
    fun explicitLaterExpiry_cannotExtendBeyondDefault() {
        store.record(obs(observedAtMs = 1000L, validUntilMs = 999999L))
        now = 1200L // past default bound 1100, well before explicit 999999
        assertEquals(0, snapshot().observations.size)
    }

    @Test
    fun expiryBoundary_isExplicit() {
        store.record(obs(observedAtMs = 1000L, validUntilMs = 1100L))
        now = 1099L
        assertEquals(1, snapshot().observations.size)
        now = 1100L // now == validUntilMs -> expired
        assertEquals(0, snapshot().observations.size)
    }

    @Test
    fun futureObservationTime_failsClosed() {
        store.record(obs(observedAtMs = 2000L))
        now = 1000L // now < observedAt
        assertEquals(0, snapshot().observations.size)
    }

    @Test
    fun clockRollback_failsClosed() {
        store.record(obs(observedAtMs = 1000L))
        now = 500L // clock rolled back
        assertEquals(0, snapshot().observations.size)
    }

    @Test
    fun overflow_observedAtPlusTtl_clampsToMax() {
        val observedAt = Long.MAX_VALUE - 10L
        store.record(obs(observedAtMs = observedAt, validUntilMs = 0L))
        now = Long.MAX_VALUE
        // Must not throw and must not wrap negative. With the clamp, effective expiry == Long.MAX_VALUE
        // and now == Long.MAX_VALUE, so the record is expired (now >= validUntilMs).
        val snap = snapshot()
        assertEquals(0, snap.observations.size)
        assertFalse(snap.observations.any { it.eventId == "e1" })
    }

    @Test
    fun snapshotFor_scopeMismatch_excluded() {
        store.record(obs(memorySpace = "a"))
        now = 1050L
        assertEquals(0, snapshot(memorySpace = "b").observations.size)
        assertEquals(1, snapshot(memorySpace = "a").observations.size)
    }
}
