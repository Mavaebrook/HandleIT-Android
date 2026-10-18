package com.ai.assistance.operit.api.chat.library

import com.ai.assistance.operit.data.model.PendingReviewEntity
import com.ai.assistance.operit.data.repository.PendingReviewBatchPolicy
import com.ai.assistance.operit.data.repository.PendingReviewBatchPolicy.ReviewOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B01 regression tests: the review scheduler must delete only the reviews that succeeded, cap
 * retries with a terminal dead-letter status, and recover stale PROCESSING rows.
 *
 * [ReviewScheduler] and [com.ai.assistance.operit.data.repository.PendingReviewRepository] depend
 * on Android `Context` and a real ObjectBox store, so they cannot be instantiated in a plain JVM
 * test (there is no Robolectric in this project). These tests therefore cover the pure decision
 * core [PendingReviewBatchPolicy], which is the logic the repository and scheduler delegate to.
 */
class ReviewSchedulerBatchTest {

    // --- B01: a partial-failure batch deletes only the reviews that succeeded ---

    @Test
    fun batchOfThreeWithOneFailure_deletesExactlyTwo() {
        // B01 acceptance: a batch of three reviews where two produce episodes and one does not
        // must delete exactly the two that succeeded; the failed one stays retryable.
        val dispositions =
            listOf(
                PendingReviewBatchPolicy.dispositionFor(
                    reviewId = 1L,
                    producedEpisode = true,
                    currentAttemptCount = 0
                ),
                PendingReviewBatchPolicy.dispositionFor(
                    reviewId = 2L,
                    producedEpisode = true,
                    currentAttemptCount = 0
                ),
                PendingReviewBatchPolicy.dispositionFor(
                    reviewId = 3L,
                    producedEpisode = false,
                    currentAttemptCount = 0
                )
            )

        val deleted = dispositions.filter { it.deleteAfterProcessing }
        val kept = dispositions.filterNot { it.deleteAfterProcessing }

        assertEquals(2, deleted.size)
        assertEquals(listOf(1L, 2L), deleted.map { it.reviewId })
        assertEquals(1, kept.size)
        assertEquals(3L, kept.single().reviewId)
        assertEquals(PendingReviewEntity.STATUS_FAILED, kept.single().nextStatus)
        // Still retryable: one attempt consumed, cap not reached.
        assertEquals(1, kept.single().nextAttemptCount)
    }

    // --- B01: retries are capped by a terminal dead-letter status ---

    @Test
    fun reviewFailingPastMaxAttempts_isDeadLettered() {
        // B01 acceptance: the review that reaches MAX_ATTEMPTS is dead-lettered, not retried.
        val disposition =
            PendingReviewBatchPolicy.dispositionFor(
                reviewId = 42L,
                producedEpisode = false,
                currentAttemptCount = PendingReviewBatchPolicy.MAX_ATTEMPTS - 1
            )

        assertEquals(PendingReviewEntity.STATUS_DEAD_LETTER, disposition.nextStatus)
        assertEquals(ReviewOutcome.FAILED, disposition.outcome)
        assertFalse(disposition.deleteAfterProcessing)
        assertEquals(PendingReviewBatchPolicy.MAX_ATTEMPTS, disposition.nextAttemptCount)
    }

    @Test
    fun deadLetteredReview_isNotRetried() {
        // B01 acceptance: a dead-lettered review is neither retried nor deleted, and its status
        // string is distinct from the statuses getPendingAndFailed() queries, so the scheduler
        // cannot pick it up again.
        val disposition =
            PendingReviewBatchPolicy.dispositionFor(
                reviewId = 7L,
                producedEpisode = false,
                currentAttemptCount = PendingReviewBatchPolicy.MAX_ATTEMPTS - 1
            )

        assertEquals(PendingReviewEntity.STATUS_DEAD_LETTER, disposition.nextStatus)
        assertFalse(disposition.deleteAfterProcessing)
        assertNotEquals(PendingReviewEntity.STATUS_PENDING, PendingReviewEntity.STATUS_DEAD_LETTER)
        assertNotEquals(PendingReviewEntity.STATUS_FAILED, PendingReviewEntity.STATUS_DEAD_LETTER)
    }

    @Test
    fun reviewBelowMaxAttempts_staysRetryable() {
        // B01 acceptance: a first failure is retryable, not terminal.
        val disposition =
            PendingReviewBatchPolicy.dispositionFor(
                reviewId = 9L,
                producedEpisode = false,
                currentAttemptCount = 0
            )

        assertEquals(PendingReviewEntity.STATUS_FAILED, disposition.nextStatus)
        assertEquals(1, disposition.nextAttemptCount)
        assertFalse(disposition.deleteAfterProcessing)
    }

    // --- B01: stale PROCESSING rows are recovered, fresh ones are left alone ---

    @Test
    fun staleProcessingRow_isRecovered() {
        // B01 acceptance: a PROCESSING row abandoned by a crashed run is stale and recovered.
        val now = 1_000_000_000L
        val updatedAtMs = now - PendingReviewBatchPolicy.PROCESSING_STALE_AFTER_MS - 1

        assertTrue(
            PendingReviewBatchPolicy.isStaleProcessing(
                status = PendingReviewEntity.STATUS_PROCESSING,
                updatedAtMs = updatedAtMs,
                nowMs = now
            )
        )
    }

    @Test
    fun freshProcessingRow_isLeftAlone() {
        // B01 acceptance: a PROCESSING row that may genuinely be in flight is not touched.
        val now = 1_000_000_000L

        assertFalse(
            PendingReviewBatchPolicy.isStaleProcessing(
                status = PendingReviewEntity.STATUS_PROCESSING,
                updatedAtMs = now - 1000L,
                nowMs = now
            )
        )
    }

    @Test
    fun processingRowExactlyAtStalenessBoundary_isNotStale() {
        // B01 acceptance: staleness is strictly greater than the threshold, so the boundary is
        // still considered in flight.
        val now = 1_000_000_000L

        assertFalse(
            PendingReviewBatchPolicy.isStaleProcessing(
                status = PendingReviewEntity.STATUS_PROCESSING,
                updatedAtMs = now - PendingReviewBatchPolicy.PROCESSING_STALE_AFTER_MS,
                nowMs = now
            )
        )
    }

    @Test
    fun nonProcessingStatus_isNeverStale() {
        // B01 acceptance: only PROCESSING rows can be abandoned; a PENDING row is never swept.
        val now = 1_000_000_000L

        assertFalse(
            PendingReviewBatchPolicy.isStaleProcessing(
                status = PendingReviewEntity.STATUS_PENDING,
                updatedAtMs = now - 10L * PendingReviewBatchPolicy.PROCESSING_STALE_AFTER_MS,
                nowMs = now
            )
        )
    }

    // --- B01: a succeeded review is deleted ---

    @Test
    fun succeededReview_isDeleted() {
        // B01 acceptance: a review that produced an episode is deleted after processing.
        val disposition =
            PendingReviewBatchPolicy.dispositionFor(
                reviewId = 11L,
                producedEpisode = true,
                currentAttemptCount = 0
            )

        assertEquals(ReviewOutcome.SUCCEEDED, disposition.outcome)
        assertTrue(disposition.deleteAfterProcessing)
    }
}
