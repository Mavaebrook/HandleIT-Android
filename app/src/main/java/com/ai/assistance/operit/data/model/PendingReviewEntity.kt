package com.ai.assistance.operit.data.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import java.util.Date

/**
 * ObjectBox persistence for a pending post-task review (resumable across restarts).
 * This is a NEW entity; it does not modify any existing entity or ObjectBox id.
 */
@Entity
data class PendingReviewEntity(
    @Id var id: Long = 0,
    @Index var chatId: String = "",
    var triggerReason: String = "",
    var taskSummary: String = "",
    var contextSummary: String = "",
    var outcome: String = TaskEpisode.OUTCOME_UNKNOWN,
    var createdAt: Date = Date(),
    var updatedAt: Date = Date(),
    var status: String = STATUS_PENDING,
    var attemptCount: Int = 0,
    var lastError: String = "",
    /**
     * [com.ai.assistance.operit.data.learning.ReviewEvidence] captured at enqueue time, JSON
     * encoded. Blank only on rows queued by a build without evidence capture
     * (docs/TODO/review-evidence-capture-20261030/README.md). Added as a new property; no existing
     * property id changes.
     */
    var evidenceJson: String = ""
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_PROCESSING = "processing"
        const val STATUS_FAILED = "failed"

        /**
         * Terminal status for a review that has failed [PendingReviewBatchPolicy.MAX_ATTEMPTS]
         * times. A dead-lettered review is never retried and never deleted, so it stays in the
         * store and remains inspectable (see `PendingReviewRepository.getDeadLettered()`).
         */
        const val STATUS_DEAD_LETTER = "dead_letter"
    }
}
