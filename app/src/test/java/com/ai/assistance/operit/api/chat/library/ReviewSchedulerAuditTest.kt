package com.ai.assistance.operit.api.chat.library

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM-only tests for the scheduler's audit-facing pure helpers: the canonical terminal-event
 * message and the completion-outcome mapping. The message is what the documented tag-anchored
 * extraction keeps for every pass outcome, so every outcome is exercised here.
 */
class ReviewSchedulerAuditTest {

    private fun auditWith(outcome: String?, failedProfiles: Int?): ReviewPassAudit =
        ReviewPassAudit(
            passesStarted = 1L,
            passesCompleted = 1L,
            lastPassStartedAtEpochMillis = null,
            lastPassCompletedAtEpochMillis = null,
            lastPassOutcome = outcome,
            lastPassPendingFound = null,
            lastPassSucceeded = null,
            lastPassFailedMarked = null,
            lastPassFailedProfiles = failedProfiles,
            startupRecoveryCompletedAtEpochMillis = null,
            startupRecoveryRecovered = null,
            startupRecoveryFailedProfiles = null
        )

    @Test
    fun passCompletionMessage_coversEveryTerminalOutcomeWithConsistentPrefix() {
        val outcomes = listOf(
            ReviewPassAuditRecorder.OUTCOME_COMPLETED,
            ReviewPassAuditRecorder.OUTCOME_PARTIAL,
            ReviewPassAuditRecorder.OUTCOME_FAILED,
            ReviewPassAuditRecorder.OUTCOME_ERROR,
            ReviewPassAuditRecorder.OUTCOME_CANCELLED
        )
        outcomes.forEach { outcome ->
            val message =
                ReviewScheduler.passCompletionMessage(auditWith(outcome, failedProfiles = 1))
            assertTrue(
                "missing documented prefix for $outcome: $message",
                message.startsWith("复习调度器 pass完成: ")
            )
            assertTrue("missing outcome for $outcome: $message", message.contains("outcome=$outcome"))
            assertTrue("missing failedProfiles for $outcome: $message", message.contains("failedProfiles=1"))
        }
    }

    @Test
    fun passCompletionMessage_rendersNullCountsExplicitly() {
        val message =
            ReviewScheduler.passCompletionMessage(
                auditWith(ReviewPassAuditRecorder.OUTCOME_CANCELLED, null)
            )
        assertTrue(message.contains("outcome=cancelled"))
        assertTrue(message.contains("failedProfiles="))
    }
}
