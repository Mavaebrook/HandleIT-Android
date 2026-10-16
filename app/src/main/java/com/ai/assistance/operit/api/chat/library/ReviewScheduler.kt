package com.ai.assistance.operit.api.chat.library

import android.content.Context
import com.ai.assistance.operit.data.repository.PendingReviewRepository
import com.ai.assistance.operit.data.repository.TaskEpisodeRepository
import com.ai.assistance.operit.util.AppLogger
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Resumable background scheduler draining the pending-review queue and invoking the logical
 * Reflector/Curator roles (docs/TODO/reliable-learning-architect-20261016 §4).
 *
 * Bounded: processes a small batch per tick, marks processing, deletes on success, marks failed
 * on error. No every-turn call; only queued reviews are processed.
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
    }

    private val isRunning = AtomicBoolean(false)
    private var loopJob: Job? = null

    fun start() {
        instance = this
        if (loopJob?.isActive == true) return
        loopJob = scope.launch(Dispatchers.IO) {
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

    private suspend fun drainPendingReviews() {
        val profileId = "default"
        val pendingRepository = PendingReviewRepository(context, profileId)
        val pending = pendingRepository.getPendingAndFailed().take(MAX_REVIEWS_PER_RUN)
        if (pending.isEmpty()) return
        val ids = pending.map { it.id }
        pendingRepository.markProcessing(ids)
        val episodeRepository = TaskEpisodeRepository(context, profileId)
        var processedAny = false
        pending.forEach { review ->
            val episode = runCatching {
                ReflectorCurator.reflect(
                    context = context,
                    taskDescription = review.taskSummary + "\n" + review.contextSummary,
                    useEnglish = true
                )
            }.getOrNull()
            if (episode != null) {
                episodeRepository.save(episode)
                processedAny = true
            }
        }
        if (processedAny) {
            pendingRepository.delete(ids)
        } else {
            pendingRepository.markFailed(ids, "reflector produced no episode")
        }
    }
}
