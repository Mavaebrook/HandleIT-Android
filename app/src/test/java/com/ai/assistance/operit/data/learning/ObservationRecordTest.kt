package com.ai.assistance.operit.data.learning

import com.ai.assistance.operit.core.tools.TerminalCommandResultData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.ToolResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for Step 1 Area 2: typed observations, the three-valued evaluator, the bounded scoped
 * store, and the real producer-to-snapshot wiring. Pure JVM, no Android or ObjectBox.
 */
class ObservationRecordTest {

    private val scope = ObservationScope(
        memorySpace = "space-a",
        projectScope = "proj-1",
        taskScope = "task-1",
        executorId = "exec-1",
        environment = "env-1",
        sessionId = "sess-1"
    )

    private fun obs(
        eventId: String = "e1",
        predicate: String = "p",
        subject: String = "s",
        value: TypedValue = TypedValue.BooleanValue(true),
        trust: TrustLevel = TrustLevel.OBSERVED,
        observedAtMs: Long = 1000L,
        validUntilMs: Long = 0L,
        memorySpace: String = "space-a",
        executorId: String = "exec-1",
        sessionId: String = "sess-1"
    ): ObservationRecord = ObservationRecord(
        eventId = eventId,
        sourceId = "src",
        memorySpace = memorySpace,
        projectScope = "proj-1",
        taskScope = "task-1",
        executorId = executorId,
        environment = "env-1",
        sessionId = sessionId,
        predicate = predicate,
        subject = subject,
        value = value,
        observedAtMs = observedAtMs,
        validUntilMs = validUntilMs,
        source = ObservationSource.TOOL_EXECUTION,
        trust = trust
    )

    private fun snapshot(vararg records: ObservationRecord): TaskContextSnapshot =
        TaskContextSnapshot(
            memorySpace = "space-a",
            projectScope = "proj-1",
            taskScope = "task-1",
            executorId = "exec-1",
            environment = "env-1",
            sessionId = "sess-1",
            observations = records.toList(),
            capturedAtMs = 1000L
        )

    // --- evaluator: negative value ---

    @Test
    fun negativeValue_returnsContradicted() {
        val result = PrerequisiteEvaluator.evaluate(
            "p", "s", snapshot(obs(value = TypedValue.BooleanValue(false))), nowMs = 1000L
        )
        assertEquals(EvaluationResult.CONTRADICTED, result)
    }

    // --- evaluator: unknown ---

    @Test
    fun noObservation_returnsUnknown() {
        val result = PrerequisiteEvaluator.evaluate("p", "s", snapshot(), nowMs = 1000L)
        assertEquals(EvaluationResult.UNKNOWN, result)
    }

    // --- evaluator: expiry with fake clock ---

    @Test
    fun expiredObservation_isIgnored_returnsUnknown() {
        val record = obs(value = TypedValue.BooleanValue(true), validUntilMs = 2000L)
        val result = PrerequisiteEvaluator.evaluate("p", "s", snapshot(record), nowMs = 3000L)
        assertEquals(EvaluationResult.UNKNOWN, result)
    }

    @Test
    fun beforeExpiry_returnsSatisfied() {
        val record = obs(value = TypedValue.BooleanValue(true), validUntilMs = 2000L)
        val result = PrerequisiteEvaluator.evaluate("p", "s", snapshot(record), nowMs = 1500L)
        assertEquals(EvaluationResult.SATISFIED, result)
    }

    @Test
    fun expiryBoundary_nowEqualsValidUntil_isIgnored_returnsUnknown() {
        val record = obs(value = TypedValue.BooleanValue(true), validUntilMs = 2000L)
        val result = PrerequisiteEvaluator.evaluate("p", "s", snapshot(record), nowMs = 2000L)
        assertEquals(EvaluationResult.UNKNOWN, result)
    }

    @Test
    fun futureObservationTime_isIgnored_returnsUnknown() {
        val record = obs(
            value = TypedValue.BooleanValue(true),
            observedAtMs = 3000L,
            validUntilMs = 4000L
        )
        val result = PrerequisiteEvaluator.evaluate("p", "s", snapshot(record), nowMs = 2000L)
        assertEquals(EvaluationResult.UNKNOWN, result)
    }

    @Test
    fun zeroValidUntil_isTreatedAsExpired_returnsUnknown() {
        val record = obs(value = TypedValue.BooleanValue(true), validUntilMs = 0L)
        val result = PrerequisiteEvaluator.evaluate("p", "s", snapshot(record), nowMs = 1000L)
        assertEquals(EvaluationResult.UNKNOWN, result)
    }

    // --- evaluator: scope isolation ---

    @Test
    fun differentMemorySpace_returnsUnknown() {
        val record = obs(value = TypedValue.BooleanValue(true), memorySpace = "space-b")
        val result = PrerequisiteEvaluator.evaluate("p", "s", snapshot(record), nowMs = 1000L)
        assertEquals(EvaluationResult.UNKNOWN, result)
    }

    // --- evaluator: executor isolation ---

    @Test
    fun differentExecutor_returnsUnknown() {
        val record = obs(value = TypedValue.BooleanValue(true), executorId = "exec-2")
        val result = PrerequisiteEvaluator.evaluate("p", "s", snapshot(record), nowMs = 1000L)
        assertEquals(EvaluationResult.UNKNOWN, result)
    }

    // --- evaluator: conflicting evidence is not newest-wins ---

    @Test
    fun conflictingEvidence_returnsUnknown() {
        val t = obs(eventId = "e1", value = TypedValue.BooleanValue(true), observedAtMs = 1000L)
        val f = obs(eventId = "e2", value = TypedValue.BooleanValue(false), observedAtMs = 2000L)
        val result = PrerequisiteEvaluator.evaluate("p", "s", snapshot(t, f), nowMs = 3000L)
        assertEquals(EvaluationResult.UNKNOWN, result)
    }

    // --- evaluator: source separation ---

    @Test
    fun reportedClaim_doesNotSatisfyObservedPredicate() {
        val record = obs(value = TypedValue.BooleanValue(true), trust = TrustLevel.REPORTED)
        val result = PrerequisiteEvaluator.evaluate("p", "s", snapshot(record), nowMs = 1000L)
        assertEquals(EvaluationResult.UNKNOWN, result)
    }

    // --- store: invalidation ---

    @Test
    fun invalidateSession_dropsPriorObservations() {
        val store = TaskContextSnapshotStore(clock = { 1000L })
        store.record(obs(value = TypedValue.BooleanValue(true)))
        store.invalidateSession("sess-1")
        val snap = store.snapshotFor("space-a", "proj-1", "task-1", "exec-1", "env-1", "sess-1")
        assertEquals(0, snap.observations.size)
    }

    // --- store: bounded eviction ---

    @Test
    fun store_evictsOldestBeyondMaxRecords() {
        val store = TaskContextSnapshotStore(clock = { 1000L }, maxRecords = 2)
        store.record(obs(eventId = "e1"))
        store.record(obs(eventId = "e2"))
        store.record(obs(eventId = "e3"))
        val snap = store.snapshotFor("space-a", "proj-1", "task-1", "exec-1", "env-1", "sess-1")
        assertEquals(2, snap.observations.size)
        assertFalse(snap.observations.any { it.eventId == "e1" })
    }

    // --- store: payload cap ---

    @Test
    fun store_truncatesLongStringValues() {
        val store = TaskContextSnapshotStore(clock = { 1000L })
        val long = "x".repeat(TaskContextSnapshotStore.MAX_STRING_VALUE_CHARS + 100)
        store.record(obs(value = TypedValue.StringValue(long)))
        val snap = store.snapshotFor("space-a", "proj-1", "task-1", "exec-1", "env-1", "sess-1")
        val stored = snap.observations.single().value as TypedValue.StringValue
        assertEquals(TaskContextSnapshotStore.MAX_STRING_VALUE_CHARS, stored.value.length)
    }

    // --- producer-to-snapshot wiring via fakes ---

    @Test
    fun producer_successfulToolResult_recordsSatisfiedStatus() {
        val store = TaskContextSnapshotStore(clock = { 1000L })
        val result = ToolResult(
            toolName = "shell",
            success = true,
            result = StringResultData("ok")
        )
        val record = ToolObservationProducer.fromToolResult("shell", "call-1", result, scope, 1000L)
        store.record(record)
        val snap = store.snapshotFor("space-a", "proj-1", "task-1", "exec-1", "env-1", "sess-1")
        val eval = PrerequisiteEvaluator.evaluate(
            ToolObservationProducer.PREDICATE_TOOL_EXECUTION_STATUS, "shell", snap, 1000L
        )
        assertEquals(EvaluationResult.SATISFIED, eval)
    }

    @Test
    fun producer_failedToolResult_recordsContradictedStatus() {
        val store = TaskContextSnapshotStore(clock = { 1000L })
        val result = ToolResult(
            toolName = "shell",
            success = false,
            result = StringResultData("boom")
        )
        val record = ToolObservationProducer.fromToolResult("shell", "call-2", result, scope, 1000L)
        store.record(record)
        val snap = store.snapshotFor("space-a", "proj-1", "task-1", "exec-1", "env-1", "sess-1")
        val eval = PrerequisiteEvaluator.evaluate(
            ToolObservationProducer.PREDICATE_TOOL_EXECUTION_STATUS, "shell", snap, 1000L
        )
        assertEquals(EvaluationResult.CONTRADICTED, eval)
    }

    @Test
    fun producer_successfulTransport_doesNotEmitTaskSucceeded() {
        // Tool transport success must NOT imply the desired task succeeded: no such predicate exists.
        val result = ToolResult(toolName = "shell", success = true, result = StringResultData("ok"))
        val record = ToolObservationProducer.fromToolResult("shell", "call-3", result, scope, 1000L)
        assertFalse(record.predicate == "task.succeeded")
        assertFalse(record.predicate == "shell.succeeded")
        assertEquals(ToolObservationProducer.PREDICATE_TOOL_EXECUTION_STATUS, record.predicate)
    }

    @Test
    fun producer_structuredExitCode_emitsExitCodeObservation() {
        val result = ToolResult(
            toolName = "terminal",
            success = true,
            result = TerminalCommandResultData(
                command = "ls",
                output = "",
                exitCode = 0,
                sessionId = "s",
                timedOut = false
            )
        )
        val exitObs = ToolObservationProducer.shellExitCodeObservation("terminal", "call-4", result, scope, 1000L)
        assertTrue(exitObs != null)
        assertEquals(ToolObservationProducer.PREDICATE_SHELL_EXIT_CODE, exitObs!!.predicate)
        assertEquals(TypedValue.IntValue(0), exitObs.value)
    }

    @Test
    fun producer_noStructuredExitCode_returnsNull() {
        val result = ToolResult(toolName = "shell", success = true, result = StringResultData("ok"))
        val exitObs = ToolObservationProducer.shellExitCodeObservation("shell", "call-5", result, scope, 1000L)
        assertNull(exitObs)
    }
}
