package com.ai.assistance.operit.api.chat.library

import android.content.Context
import com.ai.assistance.operit.BuildConfig
import com.ai.assistance.operit.data.learning.ReflectorInput
import com.ai.assistance.operit.data.learning.ReviewLessonLanguage
import com.ai.assistance.operit.data.model.TaskEpisode
import com.ai.assistance.operit.data.preferences.preferencesManager
import com.ai.assistance.operit.data.repository.EpisodeStore
import com.ai.assistance.operit.data.repository.PendingReviewBatchPolicy
import com.ai.assistance.operit.data.repository.PendingReviewRepository
import com.ai.assistance.operit.data.repository.TaskEpisodeRepository
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.LocaleUtils
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
 *
 * B02: lessons are generated in the user's locale language, not always English. The
 * `useEnglish` flag passed to [ReflectorCurator.reflect] is derived from the same signal
 * ConversationService uses (`!LocaleUtils.usesChineseContent(context)`), via the pure
 * [ReviewLessonLanguage] so the decision is unit-testable.
 *
 * @param episodeStoreFactory the episode write path for a memory space. This is a JVM-testability
 * seam only: production always uses the default, which builds the real [TaskEpisodeRepository] for
 * the space being drained.
 */
class ReviewScheduler(
    private val context: Context,
    private val scope: CoroutineScope,
    private val episodeStoreFactory: (String) -> EpisodeStore = { profileId ->
        TaskEpisodeRepository(context, profileId)
    }
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

        /**
         * Canonical DEBUG terminal event emitted for EVERY pass outcome (completed, partial,
         * failed, error, cancelled). Always starts with the same "复习调度器 pass完成:" prefix so
         * the documented tag-anchored extraction keeps terminal events for all outcomes alike,
         * and always carries the failed-profile count so a failed/partial pass can never be
         * mistaken for an empty completed one. Pure: directly unit-testable.
         */
        internal fun passCompletionMessage(audit: ReviewPassAudit): String =
            "复习调度器 pass完成: outcome=${audit.lastPassOutcome}, pending=${audit.lastPassPendingFound}, " +
                "succeeded=${audit.lastPassSucceeded}, failedMarked=${audit.lastPassFailedMarked}, " +
                "failedProfiles=${audit.lastPassFailedProfiles}"

        /**
         * Save [episode] and then add it to the episode vector index.
         *
         * EMBEDDING-FAILURE RULE: indexing is secondary to saving the episode. The episode is saved
         * first, so it is durable before any embedding request is made. If embedding or index
         * writing then fails, the failure is reported through [onIndexFailure] with the episode id
         * and the review is still treated as succeeded: an embedding failure alone must never mark
         * the review failed or cause it to be retried, because the lesson itself was captured. This
         * is an explicit rule, not a silent catch: the only exception that is not handled here is
         * cancellation, which is rethrown so structured concurrency is preserved.
         *
         * Extracted into the companion object so the rule is directly unit-testable without an
         * ObjectBox store or an Android Context.
         *
         * @return the stored episode id.
         */
        internal suspend fun saveEpisodeAndIndex(
            episodeStore: EpisodeStore,
            episode: TaskEpisode,
            reviewId: Long,
            onIndexFailure: (Long, Exception) -> Unit
        ): Long {
            val savedId = episodeStore.save(episode)
            try {
                episodeStore.indexSavedEpisode(savedId, episode)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                onIndexFailure(savedId, error)
            }
            return savedId
        }
    }

    private val isRunning = AtomicBoolean(false)
    private var loopJob: Job? = null

    /**
     * In-memory audit of pass events; read by ReviewPipelineAuditReporter. Always recorded
     * (cheap), regardless of build type.
     */
    val passAudit = ReviewPassAuditRecorder()

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

    /**
     * Runs one drain pass. Concurrent invocations are collapsed: only one pass runs at a time.
     *
     * Every pass — including a pass over an empty queue — records a start and a completion with
     * an outcome into [passAudit], so pass execution is observable even when nothing was pending.
     * The outcome distinguishes complete observations from partial ones (some profiles failed)
     * and failed ones (every profile failed). DEBUG builds log one line per pass start and one
     * canonical terminal line per outcome under TAG — including error and cancellation outcomes,
     * so every terminal event survives the documented extraction filter.
     */
    suspend fun runOnce() {
        if (!isRunning.compareAndSet(false, true)) return
        passAudit.recordPassStart()
        if (BuildConfig.DEBUG) {
            val audit = passAudit.snapshot()
            AppLogger.d(TAG, "复习调度器 pass开始: passCount=${audit.passesStarted}")
        }
        try {
            val stats = drainPendingReviews()
            passAudit.recordPassCompleted(
                outcome = ReviewPassAuditRecorder.completionOutcome(
                    failedProfiles = stats.failedProfiles,
                    profilesAttempted = stats.profilesAttempted
                ),
                pendingFound = stats.pendingFound,
                succeeded = stats.succeeded,
                failedMarked = stats.failedMarked,
                failedProfiles = stats.failedProfiles
            )
            logPassCompletion()
        } catch (cancellation: CancellationException) {
            passAudit.recordPassCompleted(ReviewPassAuditRecorder.OUTCOME_CANCELLED, null, null, null, null)
            logPassCompletion()
            throw cancellation
        } catch (error: Exception) {
            passAudit.recordPassCompleted(ReviewPassAuditRecorder.OUTCOME_ERROR, null, null, null, null)
            logPassCompletion()
            AppLogger.e(TAG, "Review scheduler run failed", error)
        } finally {
            isRunning.set(false)
        }
    }

    /**
     * Emits the single canonical terminal DEBUG line for the just-recorded pass outcome, so
     * completed, partial, failed, error and cancellation evidence all survive the documented
     * tag-anchored extraction filter. Cancellation propagation is unaffected: this only logs.
     */
    private fun logPassCompletion() {
        if (!BuildConfig.DEBUG) return
        AppLogger.d(TAG, passCompletionMessage(passAudit.snapshot()))
    }

    /**
     * B01 startup sweep: returns rows abandoned in PROCESSING by a crashed run to PENDING so they
     * are picked up again. Each memory space is an independent ObjectBox store, so a failure in one
     * store is logged with its space id and the remaining spaces are still swept.
     */
    suspend fun recoverStaleProcessing() {
        val profileIds = memorySpacesToDrain(preferencesManager.memorySpaceListFlow.first())
        var totalRecovered = 0
        var failedProfiles = 0
        for (profileId in profileIds) {
            try {
                val recovered = PendingReviewRepository(context, profileId).recoverStaleProcessing()
                totalRecovered += recovered
                if (recovered > 0) {
                    AppLogger.i(TAG, "恢复滞留复盘: profileId=$profileId, recovered=$recovered")
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                failedProfiles++
                AppLogger.e(TAG, "滞留复盘恢复失败: profileId=$profileId", error)
            }
        }
        passAudit.recordStartupRecovery(recovered = totalRecovered, failedProfiles = failedProfiles)
        if (BuildConfig.DEBUG) {
            AppLogger.d(TAG, "复习调度器启动恢复完成: recovered=$totalRecovered, failedProfiles=$failedProfiles")
        }
    }

    /**
     * Per-pass drain statistics, aggregated across memory spaces for [runOnce]'s audit record.
     * [failedProfiles] must be recorded so an all-failed pass is never reported as an empty
     * completed one: [ReviewPassAuditRecorder.completionOutcome] maps it to a partial/failed
     * outcome.
     */
    private data class PassStats(
        val pendingFound: Int = 0,
        val succeeded: Int = 0,
        val failedMarked: Int = 0,
        val profilesAttempted: Int = 0,
        val failedProfiles: Int = 0
    )

    private suspend fun drainPendingReviews(): PassStats {
        val profileIds = memorySpacesToDrain(preferencesManager.memorySpaceListFlow.first())
        var total = PassStats()
        for (profileId in profileIds) {
            // Each memory space is an independent ObjectBox store. A failure in one store must
            // not prevent the remaining spaces from being drained, so the failure is logged with
            // its space id and the loop continues. The exception is never discarded silently.
            try {
                val spaceStats = drainPendingReviewsForSpace(profileId)
                total = total.copy(
                    pendingFound = total.pendingFound + spaceStats.pendingFound,
                    succeeded = total.succeeded + spaceStats.succeeded,
                    failedMarked = total.failedMarked + spaceStats.failedMarked,
                    profilesAttempted = total.profilesAttempted + 1
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                AppLogger.e(TAG, "待复盘队列处理失败: profileId=$profileId", error)
                total = total.copy(
                    profilesAttempted = total.profilesAttempted + 1,
                    failedProfiles = total.failedProfiles + 1
                )
            }
        }
        return total
    }

    /**
     * B02: the language lessons are generated in, derived from the user's locale exactly as
     * ConversationService does (`!LocaleUtils.usesChineseContent(context)`). The decision itself is
     * delegated to the pure [ReviewLessonLanguage] so it is unit-testable.
     */
    private fun useEnglishForLessons(): Boolean =
        ReviewLessonLanguage.useEnglishFor(LocaleUtils.getCurrentLanguage(context))

    /**
     * Save [episode] and then add it to the episode vector index.
     *
     * EMBEDDING-FAILURE RULE: indexing is secondary to saving the episode. The episode is saved
     * first, so it is durable before any embedding request is made. If embedding or index writing
     * then fails, the failure is logged at error level with the episode id and the review is still
     * treated as succeeded: an embedding failure alone must never mark the review failed or cause
     * it to be retried, because the lesson itself was captured. This is an explicit rule, not a
     * silent catch: the only exception that is not handled here is cancellation, which is
     * rethrown so structured concurrency is preserved.
     *
     * @return the stored episode id.
     */
    private suspend fun saveEpisodeAndIndex(
        episodeStore: EpisodeStore,
        episode: TaskEpisode,
        reviewId: Long
    ): Long =
        saveEpisodeAndIndex(episodeStore, episode, reviewId) { episodeId, error ->
            AppLogger.e(
                TAG,
                "episode saved but vector indexing failed: episodeId=$episodeId, reviewId=$reviewId",
                error
            )
        }

    private suspend fun drainPendingReviewsForSpace(profileId: String): PassStats {
        val pendingRepository = PendingReviewRepository(context, profileId)
        val pending = pendingRepository.getPendingAndFailed().take(MAX_REVIEWS_PER_RUN)
        if (pending.isEmpty()) return PassStats()
        AppLogger.d(TAG, "开始处理待复盘队列: profileId=$profileId, pending=${pending.size}")
        pendingRepository.markProcessing(pending.map { it.id })
        val episodeStore = episodeStoreFactory(profileId)
        val succeededIds = mutableListOf<Long>()
        // Reviews the Reflector closed with "no lesson": processed successfully, nothing saved.
        val noLessonIds = mutableListOf<Long>()
        var failedMarked = 0
        // B02: the locale does not change mid-batch, so resolve it once for the whole batch.
        val useEnglish = useEnglishForLessons()
        pending.forEach { review ->
            var failure: Throwable? = null
            var reflectorInput = ""
            // Explicit try/catch instead of runCatching: runCatching catches Throwable, which would
            // also swallow CancellationException and break structured concurrency. Cancellation is
            // rethrown; only a genuine reflector failure is recorded and logged.
            val reflection = try {
                // The Reflector receives labeled turn evidence (trigger reason, outcome, the
                // previous reply, failed tools and their errors) instead of two unlabeled texts.
                // Built inside this try: an unreadable stored evidence value fails this review
                // loudly and follows the normal retry/dead-letter path; it is never read as empty.
                // docs/TODO/review-evidence-capture-20261030/README.md
                reflectorInput =
                    ReflectorInput.build(
                        triggerReason = review.triggerReason,
                        observedOutcome = review.outcome,
                        evidenceJson = review.evidenceJson,
                        legacyFinalReply = review.taskSummary,
                        legacyUserRequest = review.contextSummary
                    )
                ReflectorCurator.reflect(
                    context = context,
                    taskDescription = reflectorInput,
                    useEnglish = useEnglish
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                failure = error
                AppLogger.e(TAG, "复盘失败: reviewId=${review.id}, profileId=$profileId", error)
                null
            }
            val disposition =
                PendingReviewBatchPolicy.dispositionFor(
                    reviewId = review.id,
                    // A "no lesson" answer is a completed review, not a failure: the row is closed.
                    producedEpisode = reflection != null,
                    currentAttemptCount = review.attemptCount
                )
            if (disposition.deleteAfterProcessing && reflection is ReflectorCurator.Reflection.Lesson) {
                // Keep the exact input on the lesson: this review row (and its evidence) is deleted
                // below, and the (evidence, lesson) pair is what a future local model trains on.
                saveEpisodeAndIndex(
                    episodeStore,
                    reflection.episode.copy(reflectorInputJson = reflectorInput),
                    review.id
                )
                succeededIds.add(review.id)
            } else if (disposition.deleteAfterProcessing && reflection is ReflectorCurator.Reflection.NoLesson) {
                AppLogger.d(
                    TAG,
                    "复盘无经验可学: reviewId=${review.id}, trigger=${review.triggerReason}, reason=${reflection.reason}"
                )
                noLessonIds.add(review.id)
            } else {
                val thrown = failure
                val errorMessage =
                    if (thrown != null) {
                        "reflector threw: ${thrown.message ?: thrown.javaClass.simpleName}"
                    } else {
                        "reflector produced no episode"
                    }
                pendingRepository.markFailedSingle(review.id, errorMessage)
                failedMarked++
            }
        }
        // Delete ONLY the reviews that completed (a lesson saved, or an explicit "no lesson"). A
        // partial failure must not discard the reviews that still need another attempt.
        pendingRepository.delete(succeededIds + noLessonIds)
        if (noLessonIds.isNotEmpty()) {
            AppLogger.d(TAG, "复盘完成但无经验: profileId=$profileId, count=${noLessonIds.size}")
        }
        return PassStats(
            pendingFound = pending.size,
            succeeded = succeededIds.size,
            failedMarked = failedMarked
        )
    }
}
