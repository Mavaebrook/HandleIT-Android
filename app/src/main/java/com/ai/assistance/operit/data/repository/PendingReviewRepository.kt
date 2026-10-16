package com.ai.assistance.operit.data.repository

import android.content.Context
import com.ai.assistance.operit.data.db.ObjectBoxManager
import com.ai.assistance.operit.data.model.PendingReviewEntity
import com.ai.assistance.operit.data.model.PendingReviewEntity_
import io.objectbox.Box
import io.objectbox.kotlin.boxFor
import java.util.Date

/**
 * Persistence for pending post-task reviews. Mirrors MemoryAutoSaveCandidateRepository so reviews
 * are resumable across process restarts.
 */
class PendingReviewRepository(
    context: Context,
    profileId: String
) {
    private val store = ObjectBoxManager.get(context, profileId)
    private val reviewBox: Box<PendingReviewEntity> = store.boxFor()

    fun enqueue(
        chatId: String,
        triggerReason: String,
        taskSummary: String,
        contextSummary: String,
        outcome: String
    ): Long {
        val now = Date()
        val entity = PendingReviewEntity(
            chatId = chatId,
            triggerReason = triggerReason,
            taskSummary = taskSummary,
            contextSummary = contextSummary,
            outcome = outcome,
            createdAt = now,
            updatedAt = now,
            status = PendingReviewEntity.STATUS_PENDING
        )
        return reviewBox.put(entity)
    }

    fun getPendingAndFailed(): List<PendingReviewEntity> {
        return reviewBox
            .query(
                PendingReviewEntity_.status
                    .equal(PendingReviewEntity.STATUS_PENDING)
                    .or(PendingReviewEntity_.status.equal(PendingReviewEntity.STATUS_FAILED))
            )
            .build()
            .find()
            .sortedBy { it.createdAt.time }
    }

    fun markProcessing(ids: List<Long>) {
        if (ids.isEmpty()) return
        val now = Date()
        val entities = ids.mapNotNull { reviewBox.get(it) }
        entities.forEach { it.status = PendingReviewEntity.STATUS_PROCESSING; it.updatedAt = now; it.lastError = "" }
        reviewBox.put(entities)
    }

    fun delete(ids: List<Long>) {
        if (ids.isEmpty()) return
        ids.forEach { reviewBox.remove(it) }
    }

    fun markFailed(ids: List<Long>, errorMessage: String) {
        if (ids.isEmpty()) return
        val now = Date()
        val normalizedError = errorMessage.take(500)
        val entities = ids.mapNotNull { reviewBox.get(it) }
        entities.forEach {
            it.status = PendingReviewEntity.STATUS_FAILED
            it.attemptCount += 1
            it.lastError = normalizedError
            it.updatedAt = now
        }
        reviewBox.put(entities)
    }
}
