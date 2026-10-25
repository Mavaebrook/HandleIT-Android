package com.ai.assistance.operit.api.chat.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Concurrency and capture semantics for [ReviewPassAuditRecorder]: the recorder is the one
 * mutable object the audit reporter reads from another coroutine while the scheduler loop writes
 * to it, so its thread-safety contract is tested with real threads.
 */
class ReviewPassAuditRecorderTest {

    @Test
    fun concurrentRecordings_keepExactCounts() {
        val recorder = ReviewPassAuditRecorder()
        val threads = (0 until 8).map {
            Thread {
                repeat(500) {
                    recorder.recordPassStart(nowMillis = 1L)
                    recorder.recordPassCompleted(
                        ReviewPassAuditRecorder.OUTCOME_COMPLETED, 0, 0, 0,
                        nowMillis = 1L
                    )
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        val snap = recorder.snapshot()
        assertEquals(8L * 500L, snap.passesStarted)
        assertEquals(8L * 500L, snap.passesCompleted)
        assertEquals(ReviewPassAuditRecorder.OUTCOME_COMPLETED, snap.lastPassOutcome)
    }

    @Test
    fun recordPassCompleted_capturesOutcomeAndPerPassStats() {
        val recorder = ReviewPassAuditRecorder()
        recorder.recordPassStart(nowMillis = 100L)
        recorder.recordPassCompleted(
            outcome = ReviewPassAuditRecorder.OUTCOME_COMPLETED,
            pendingFound = 3,
            succeeded = 2,
            failedMarked = 1,
            nowMillis = 200L
        )
        val snap = recorder.snapshot()
        assertEquals(1L, snap.passesStarted)
        assertEquals(1L, snap.passesCompleted)
        assertEquals(100L, snap.lastPassStartedAtEpochMillis)
        assertEquals(200L, snap.lastPassCompletedAtEpochMillis)
        assertEquals(ReviewPassAuditRecorder.OUTCOME_COMPLETED, snap.lastPassOutcome)
        assertEquals(3, snap.lastPassPendingFound)
        assertEquals(2, snap.lastPassSucceeded)
        assertEquals(1, snap.lastPassFailedMarked)
    }

    @Test
    fun recordPassCompleted_errorOutcome_captured() {
        val recorder = ReviewPassAuditRecorder()
        recorder.recordPassStart()
        recorder.recordPassCompleted(ReviewPassAuditRecorder.OUTCOME_ERROR, null, null, null)
        val snap = recorder.snapshot()
        assertEquals(ReviewPassAuditRecorder.OUTCOME_ERROR, snap.lastPassOutcome)
        assertNull(snap.lastPassPendingFound)
    }

    @Test
    fun recordStartupRecovery_capturesValues() {
        val recorder = ReviewPassAuditRecorder()
        recorder.recordStartupRecovery(recovered = 3, nowMillis = 123L)
        val snap = recorder.snapshot()
        assertEquals(123L, snap.startupRecoveryCompletedAtEpochMillis)
        assertEquals(3, snap.startupRecoveryRecovered)
    }

    @Test
    fun snapshot_isImmutableCopy() {
        val recorder = ReviewPassAuditRecorder()
        recorder.recordPassStart(nowMillis = 5L)
        val before = recorder.snapshot()
        recorder.recordPassCompleted(ReviewPassAuditRecorder.OUTCOME_COMPLETED, 1, 1, 0, nowMillis = 7L)
        val after = recorder.snapshot()
        assertEquals(1L, before.passesStarted)
        assertEquals(0L, before.passesCompleted)
        assertEquals(5L, before.lastPassStartedAtEpochMillis)
        assertNull(before.lastPassCompletedAtEpochMillis)
        assertEquals(1L, after.passesCompleted)
        assertEquals(7L, after.lastPassCompletedAtEpochMillis)
    }
}