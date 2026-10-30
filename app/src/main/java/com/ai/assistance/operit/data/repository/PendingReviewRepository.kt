package com.ai.assistance.operit.data.repository

import android.content.Context
import com.ai.assistance.operit.data.db.ObjectBoxManager
import com.ai.assistance.operit.data.model.PendingReviewEntity
import com.ai.assistance.operit.data.model.PendingReviewEntity_
import com.ai.assistance.operit.util.AppLogger
import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import java.util.Date

/**
 * Persistence for pending post-task reviews. Mirrors MemoryAutoSaveCandidateRepository so reviews
 * are resumable across process restarts.
 *
 * Attempt accounting and the stale-PROCESSING decision are delegated to
 * [PendingReviewBatchPolicy] so the scheduler and this repository share one source of truth.
 */
class PendingReviewRepository(
    context: Context,
    profileId: String
) {
    companion object {
        private const val TAG = "PendingReviewRepository"

        /** A review that has failed this many times is dead-lettered and never retried. */
        const val MAX_ATTEMPTS = PendingReviewBatchPolicy.MAX_ATTEMPTS

        /** A PROCESSING row older than this is considered abandoned by a crashed run. */
        const val PROCESSING_STALE_AFTER_MS = PendingReviewBatchPolicy.PROCESSING_STALE_AFTER_MS

        /**
         * Audit-only read against an already-open store. Never creates or opens a store, so it
         * can be used by diagnostics even while another thread may close/delete the profile: the
         * caller obtains the store via ObjectBoxManager.getExisting, which never builds one.
         * This is the existing-store-only access path; the instance method [getAll] delegates here
         * so there is one implementation.
         */
        fun getAllFrom(store: BoxStore): List<PendingReviewEntity> {
            val box: Box<PendingReviewEntity> = store.boxFor()
            return box.all.sortedBy { it.createdAt.time }
        }
    }

    private val store = ObjectBoxManager.get(context, profileId)
    private val reviewBox: Box<PendingReviewEntity> = store.boxFor()

    fun enqueue(
        chatId: String,
        triggerReason: String,
        taskSummary: String,
        contextSummary: String,
        outcome: String,
        evidenceJson: String
    ): Long {
        val now = Date()
        val entity = PendingReviewEntity(
            chatId = chatId,
            triggerReason = triggerReason,
            taskSummary = taskSummary,
            contextSummary = contextSummary,
            outcome = outcome,
            evidenceJson = evidenceJson,
            createdAt = now,
            updatedAt = now,
            status = PendingReviewEntity.STATUS_PENDING
        )
        return reviewBox.put(entity)
    }

    /**
     * Reviews that are eligible for another attempt.
     *
     * Dead-lettered rows are deliberately NOT returned: `STATUS_DEAD_LETTER` is a distinct string
     * from `STATUS_PENDING` and `STATUS_FAILED`, so this query cannot match them. They are never
     * retried, and they are never deleted either — they remain in the store for inspection via
     * [getDeadLettered].
     */
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

    /** Dead-lettered reviews, kept for inspection. Never retried, never deleted. */
    fun getDeadLettered(): List<PendingReviewEntity> {
        return reviewBox
            .query(PendingReviewEntity_.status.equal(PendingReviewEntity.STATUS_DEAD_LETTER))
            .build()
            .find()
            .sortedBy { it.createdAt.time }
    }

    /**
     * Audit-only single-query read: every persisted review regardless of status, returned in one
     * consistent snapshot. Read-only; never mutates.
     *
     * Used by ReviewPipelineAuditReporter instead of combining [getPendingAndFailed] and
     * [getDeadLettered]: those two separate queries can disagree when a row changes status
     * between them, and they omit PROCESSING rows entirely.
     */
    fun getAll(): List<PendingReviewEntity> = getAllFrom(store)

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
        val entities = ids.mapNotNull { reviewBox.get(it) }
        applyFailure(entities, errorMessage)
    }

    /** Per-row attempt accounting for exactly one review. */
    fun markFailedSingle(id: Long, errorMessage: String) {
        val entity = reviewBox.get(id) ?: return
        applyFailure(listOf(entity), errorMessage)
    }

    /**
     * Returns abandoned PROCESSING rows to PENDING.
     *
     * A row is abandoned when it has been PROCESSING for longer than
     * [PROCESSING_STALE_AFTER_MS], which means the run that claimed it crashed. Rows that are
     * PROCESSING but not yet stale are left untouched because they may genuinely be in flight.
     *
     * The staleness decision is a pure function of the row's `updatedAt` versus [nowMs]
     * ([PendingReviewBatchPolicy.isStaleProcessing]) so it is unit-testable.
     *
     * @return the number of rows recovered.
     */
    fun recoverStaleProcessing(nowMs: Long = System.currentTimeMillis()): Int {
        val now = Date(nowMs)
        val stale =
            reviewBox
                .query(PendingReviewEntity_.status.equal(PendingReviewEntity.STATUS_PROCESSING))
                .build()
                .find()
                .filter {
                    PendingReviewBatchPolicy.isStaleProcessing(
                        status = it.status,
                        updatedAtMs = it.updatedAt.time,
                        nowMs = nowMs
                    )
                }
        if (stale.isEmpty()) return 0
        stale.forEach {
            it.status = PendingReviewEntity.STATUS_PENDING
            it.updatedAt = now
            it.lastError = appendNote(it.lastError, "recovered from stale processing")
        }
        reviewBox.put(stale)
        AppLogger.i(TAG, "Recovered ${stale.size} stale PROCESSING review(s) to PENDING")
        return stale.size
    }

    /**
     * Shared per-row failure accounting used by both [markFailed] and [markFailedSingle] so the
     * two entry points cannot drift. Increments the attempt count and transitions to
     * [PendingReviewEntity.STATUS_DEAD_LETTER] once [MAX_ATTEMPTS] is reached.
     */
    private fun applyFailure(entities: List<PendingReviewEntity>, errorMessage: String) {
        if (entities.isEmpty()) return
        val now = Date()
        val normalizedError = errorMessage.take(500)
        entities.forEach {
            it.attemptCount += 1
            it.lastError = normalizedError
            it.updatedAt = now
            it.status =
                if (it.attemptCount >= MAX_ATTEMPTS) {
                    PendingReviewEntity.STATUS_DEAD_LETTER
                } else {
                    PendingReviewEntity.STATUS_FAILED
                }
            if (it.status == PendingReviewEntity.STATUS_DEAD_LETTER) {
                AppLogger.w(
                    TAG,
                    "Review dead-lettered: id=${it.id}, attemptCount=${it.attemptCount}"
                )
            }
        }
        reviewBox.put(entities)
    }

    private fun appendNote(existing: String, note: String): String {
        val combined = if (existing.isBlank()) note else "$existing; $note"
        return combined.take(500)
    }
}
