package com.ai.assistance.operit.data.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM regression tests for the shared [ObservationCoordinator] (Step 1, Area 2).
 *
 * These prove that an observation produced through the shared store reaches preflight in the same
 * scope, and cannot cross memory space, task/chat scope, or a rotated session generation. Pure JVM:
 * no Android, no ObjectBox, no Robolectric.
 */
class ObservationCoordinatorTest {

    private fun record(
        memorySpace: String,
        taskScope: String,
        sessionId: String,
        eventId: String = "e1"
    ) = ObservationRecord(
        eventId = eventId,
        sourceId = "call-1",
        memorySpace = memorySpace,
        projectScope = "",
        taskScope = taskScope,
        executorId = "",
        environment = "",
        sessionId = sessionId,
        predicate = "tool.execution.status",
        subject = "tool",
        value = TypedValue.BooleanValue(true),
        observedAtMs = 1000L,
        validUntilMs = 0L,
        source = ObservationSource.TOOL_EXECUTION,
        trust = TrustLevel.OBSERVED
    )

    private fun coordinator() = ObservationCoordinator(
        store = TaskContextSnapshotStore(clock = { 1000L })
    )

    @Test
    fun producedObservation_reachesPreflightInSameScope() {
        val coordinator = coordinator()
        val session = coordinator.currentGeneration().toString()
        coordinator.store.record(record(memorySpace = "ms1", taskScope = "chat1", sessionId = session))

        val snapshot = coordinator.store.snapshotFor(
            memorySpace = "ms1",
            projectScope = "",
            taskScope = "chat1",
            executorId = "",
            environment = "",
            sessionId = session
        )

        assertEquals(1, snapshot.observations.size)
        assertEquals("e1", snapshot.observations.first().eventId)
    }

    @Test
    fun observation_cannotCrossMemorySpace() {
        val coordinator = coordinator()
        val session = coordinator.currentGeneration().toString()
        coordinator.store.record(record(memorySpace = "ms1", taskScope = "chat1", sessionId = session))

        val snapshot = coordinator.store.snapshotFor(
            memorySpace = "ms2",
            projectScope = "",
            taskScope = "chat1",
            executorId = "",
            environment = "",
            sessionId = session
        )

        assertTrue(snapshot.observations.isEmpty())
    }

    @Test
    fun observation_cannotCrossTaskScope() {
        val coordinator = coordinator()
        val session = coordinator.currentGeneration().toString()
        coordinator.store.record(record(memorySpace = "ms1", taskScope = "chat1", sessionId = session))

        val snapshot = coordinator.store.snapshotFor(
            memorySpace = "ms1",
            projectScope = "",
            taskScope = "chat2",
            executorId = "",
            environment = "",
            sessionId = session
        )

        assertTrue(snapshot.observations.isEmpty())
    }

    @Test
    fun observation_cannotCrossSessionGeneration() {
        val coordinator = coordinator()
        val oldSession = coordinator.currentGeneration().toString()
        coordinator.store.record(record(memorySpace = "ms1", taskScope = "chat1", sessionId = oldSession))

        coordinator.rotateGeneration()
        val newSession = coordinator.currentGeneration().toString()

        val snapshot = coordinator.store.snapshotFor(
            memorySpace = "ms1",
            projectScope = "",
            taskScope = "chat1",
            executorId = "",
            environment = "",
            sessionId = newSession
        )

        assertTrue(snapshot.observations.isEmpty())
    }

    @Test
    fun rotateGeneration_clearsStore() {
        val coordinator = coordinator()
        val session = coordinator.currentGeneration().toString()
        coordinator.store.record(record(memorySpace = "ms1", taskScope = "chat1", sessionId = session))

        coordinator.rotateGeneration()

        val snapshot = coordinator.store.snapshotFor(
            memorySpace = "ms1",
            projectScope = "",
            taskScope = "chat1",
            executorId = "",
            environment = "",
            sessionId = session
        )
        assertTrue(snapshot.observations.isEmpty())
    }

    @Test
    fun isCurrent_rejectsOldGeneration() {
        val coordinator = coordinator()
        val oldGeneration = coordinator.currentGeneration()

        coordinator.rotateGeneration()

        assertFalse(coordinator.isCurrent(oldGeneration))
        assertTrue(coordinator.isCurrent(coordinator.currentGeneration()))
    }

    @Test
    fun unknownScope_doesNotMatchRealScope() {
        val coordinator = coordinator()
        val session = coordinator.currentGeneration().toString()
        coordinator.store.record(record(memorySpace = "", taskScope = "", sessionId = session))

        val snapshot = coordinator.store.snapshotFor(
            memorySpace = "real",
            projectScope = "",
            taskScope = "chat1",
            executorId = "",
            environment = "",
            sessionId = session
        )

        assertTrue(snapshot.observations.isEmpty())
    }

    @Test
    fun inFlightOldSessionCompletion_isRejected() {
        val coordinator = coordinator()
        val writeGeneration = coordinator.currentGeneration()

        coordinator.rotateGeneration()

        assertFalse(coordinator.isCurrent(writeGeneration))
    }

    @Test
    fun resolveMemorySpace_overrideWins() {
        assertEquals("override", ObservationScopeResolver.resolveMemorySpace("override", "active"))
    }

    @Test
    fun resolveMemorySpace_fallsBackToActive() {
        assertEquals("active", ObservationScopeResolver.resolveMemorySpace(null, "active"))
        assertEquals("active", ObservationScopeResolver.resolveMemorySpace("   ", "active"))
    }
}
