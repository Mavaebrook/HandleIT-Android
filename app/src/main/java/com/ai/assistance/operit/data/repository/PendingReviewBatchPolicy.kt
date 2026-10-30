package com.ai.assistance.operit.data.repository

import com.ai.assistance.operit.data.model.PendingReviewEntity

/**
 * Pure, Android-free decision logic for draining the pending-review queue.
 *
 * This object exists so the batch policy can be unit-tested on the JVM: the repositories need a
 * real ObjectBox store and cannot be instantiated in `app/src/test` (there is no Robolectric in
 * this project). [PendingReviewRepository] delegates its attempt accounting and its stale-row
 * decision to this object, so the tested logic is the logic that actually runs.
 */
object PendingReviewBatchPolicy {

    /** A review that has failed this many times is dead-lettered and never retried. */
    const val MAX_ATTEMPTS = 3

    /** A PROCESSING row older than this is considered abandoned by a crashed run. */
    const val PROCESSING_STALE_AFTER_MS = 10 * 60 * 1000L

    /** Outcome of processing one review in a batch. */
    enum class ReviewOutcome { SUCCEEDED, FAILED }

    /** What the scheduler must do with one review after attempting it. */
    data class ReviewDisposition(
        val reviewId: Long,
        val outcome: ReviewOutcome,
        val nextStatus: String,
        val nextAttemptCount: Int,
        val deleteAfterProcessing: Boolean
    )

    /**
     * Decide the disposition of one review. A review that completed (it produced an episode, or the
     * Reflector answered "no lesson") is deleted.
     * A review that did not is marked failed and stays retryable, until it reaches
     * [MAX_ATTEMPTS], at which point it is dead-lettered and never retried or deleted.
     */
    fun dispositionFor(
        reviewId: Long,
        producedEpisode: Boolean,
        currentAttemptCount: Int
    ): ReviewDisposition {
        if (producedEpisode) {
            // The row is deleted, so nextStatus is unused; it is reported as PENDING so the value
            // is never mistaken for a terminal state.
            return ReviewDisposition(
                reviewId = reviewId,
                outcome = ReviewOutcome.SUCCEEDED,
                nextStatus = PendingReviewEntity.STATUS_PENDING,
                nextAttemptCount = currentAttemptCount,
                deleteAfterProcessing = true
            )
        }
        val nextAttemptCount = currentAttemptCount + 1
        val nextStatus =
            if (nextAttemptCount >= MAX_ATTEMPTS) {
                PendingReviewEntity.STATUS_DEAD_LETTER
            } else {
                PendingReviewEntity.STATUS_FAILED
            }
        return ReviewDisposition(
            reviewId = reviewId,
            outcome = ReviewOutcome.FAILED,
            nextStatus = nextStatus,
            nextAttemptCount = nextAttemptCount,
            deleteAfterProcessing = false
        )
    }

    /** True when a PROCESSING row is old enough to be considered abandoned. */
    fun isStaleProcessing(status: String, updatedAtMs: Long, nowMs: Long): Boolean {
        if (status != PendingReviewEntity.STATUS_PROCESSING) return false
        return (nowMs - updatedAtMs) > PROCESSING_STALE_AFTER_MS
    }
}
