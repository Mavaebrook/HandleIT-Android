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
    var lastError: String = ""
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_PROCESSING = "processing"
        const val STATUS_FAILED = "failed"
    }
}
