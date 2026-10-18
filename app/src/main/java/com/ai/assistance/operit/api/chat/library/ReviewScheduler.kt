package com.ai.assistance.operit.api.chat.library

import android.content.Context
import com.ai.assistance.operit.data.preferences.preferencesManager
import com.ai.assistance.operit.data.repository.PendingReviewBatchPolicy
import com.ai.assistance.operit.data.repository.PendingReviewRepository
import com.ai.assistance.operit.data.repository.TaskEpisodeRepository
import com.ai.assistance.operit.util.AppLogger
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Resumable background scheduler draining the pending-review queue and invoking the logical
 * Reflector/Curator roles (docs/TODO/reliable-learning-architect-20261016 §4).
 *
 * Bounded: processes a small batch per tick, marks processing, deletes on success, marks failed
 * on error. No every-turn call; only queued reviews are processed.
 *
 * A03: drains every memory space that has pending reviews, not just the hardcoded "default" space.
 * Each resulting episode is saved into the SAME space the review was enqueued in.
 *
 * B01: a batch is processed per review. Only the reviews that actually produced an episode are
 * deleted; the rest are marked failed (and eventually dead-lettered) so a partial failure never
 * discards work that still needs another attempt. The per-review decision is delegated to
 * [PendingReviewBatchPolicy] so it is unit-testable.
 */
class ReviewScheduler(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "ReviewScheduler"
        private const val LOOP_TICK_MS = 60_000L
        private const val MAX_REVIEWS_PER_RUN = 5

        @Volatile
        private var instance: ReviewScheduler? = null

        fun getInstance(): ReviewScheduler? = instance

        /**
         * A03: the memory spaces that must be drained. Every space in the user's memory space list
         * is a candidate, because a review may have been enqueued into any of them.
         */
        internal fun memorySpacesToDrain(profileIds: List<String>): List<String> =
            profileIds.filter { it.isNotBlank() }.distinct()
    }

    private val isRunning = AtomicBoolean(false)
    private var loopJob: Job? = null

    fun start() {
        instance = this
        if (loopJob?.isActive == true) return
        loopJob = scope.launch(Dispatchers.IO) {
            // B01: recover rows abandoned in PROCESSING by a crashed run before the first drain,
            // so a crash mid-batch cannot strand a review forever.
            recoverStaleProcessing()
            while (isActive) {
                delay(LOOP_TICK_MS)
                runOnce()
            }
        }
    }

    suspend fun runOnce() {
        if (!isRunning.compareAndSet(false, true)) return
        try {
            drainPendingReviews()
        } catch (error: Exception) {
            AppLogger.e(TAG, "Review scheduler run failed", error)
        } finally {
            isRunning.set(false)
        }
    }

    /**
     * B01 startup sweep: returns rows abandoned in PROCESSING by a crashed run to PENDING so they
     * are picked up again. Each memory space is an independent ObjectBox store, so a failure in one
     * store is logged with its space id and the remaining spaces are still swept.
     */
    suspend fun recoverStaleProcessing() {
        val profileIds = memorySpacesToDrain(preferencesManager.memorySpaceListFlow.first())
        for (profileId in profileIds) {
            try {
                val recovered = PendingReviewRepository(context, profileId).recoverStaleProcessing()
                if (recovered > 0) {
                    AppLogger.i(TAG, "恢复滞留复盘: profileId=$profileId, recovered=$recovered")
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                AppLogger.e(TAG, "滞留复盘恢复失败: profileId=$profileId", error)
            }
        }
    }

    private suspend fun drainPendingReviews() {
        val profileIds = memorySpacesToDrain(preferencesManager.memorySpaceListFlow.first())
        for (profileId in profileIds) {
            // Each memory space is an independent ObjectBox store. A failure in one store must
            // not prevent the remaining spaces from being drained, so the failure is logged with
            // its space id and the loop continues. The exception is never discarded silently.
            try {
                drainPendingReviewsForSpace(profileId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                AppLogger.e(TAG, "待复盘队列处理失败: profileId=$profileId", error)
            }
        }
    }

    private suspend fun drainPendingReviewsForSpace(profileId: String) {
        val pendingRepository = PendingReviewRepository(context, profileId)
        val pending = pendingRepository.getPendingAndFailed().take(MAX_REVIEWS_PER_RUN)
        if (pending.isEmpty()) return
        AppLogger.d(TAG, "开始处理待复盘队列: profileId=$profileId, pending=${pending.size}")
        pendingRepository.markProcessing(pending.map { it.id })
        val episodeRepository = TaskEpisodeRepository(context, profileId)
        val succeededIds = mutableListOf<Long>()
        pending.forEach { review ->
            var failure: Throwable? = null
            val episode = runCatching {
                ReflectorCurator.reflect(
                    context = context,
                    taskDescription = review.taskSummary + "\n" + review.contextSummary,
                    useEnglish = true
                )
            }.onFailure {
                failure = it
                AppLogger.e(TAG, "复盘失败: reviewId=${review.id}, profileId=$profileId", it)
            }.getOrNull()
            val disposition =
                PendingReviewBatchPolicy.dispositionFor(
                    reviewId = review.id,
                    producedEpisode = episode != null,
                    currentAttemptCount = review.attemptCount
                )
            if (disposition.deleteAfterProcessing && episode != null) {
                episodeRepository.save(episode)
                succeededIds.add(review.id)
            } else {
                val thrown = failure
                val errorMessage =
                    if (thrown != null) {
                        "reflector threw: ${thrown.message ?: thrown.javaClass.simpleName}"
                    } else {
                        "reflector produced no episode"
                    }
                pendingRepository.markFailedSingle(review.id, errorMessage)
            }
        }
        // Delete ONLY the reviews that actually produced an episode. A partial failure must not
        // discard the reviews that still need another attempt.
        pendingRepository.delete(succeededIds)
    }
}
