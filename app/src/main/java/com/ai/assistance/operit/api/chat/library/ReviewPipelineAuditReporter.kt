package com.ai.assistance.operit.api.chat.library

import android.content.Context
import android.os.Process
import com.ai.assistance.operit.data.model.PendingReviewEntity
import com.ai.assistance.operit.data.model.TaskEpisodeEntity
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.repository.PendingReviewRepository
import com.ai.assistance.operit.data.repository.TaskEpisodeRepository
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * DEBUG-only, read-only audit snapshot for the review pipeline.
 *
 * Answers, once per launch, the three runtime audit questions that currently have no
 * diagnostics surface (the UIDebugger screen exposes none of them):
 *
 * 1. Was [ReviewScheduler] constructed and started? ([ReviewAuditSnapshot.schedulerConstructed])
 * 2. How many persisted pending reviews exist, grouped by status? ([ReviewAuditSnapshot.pendingByStatus])
 * 3. How many [TaskEpisodeEntity] rows exist in the active profile's store? ([ReviewAuditSnapshot.taskEpisodeCount])
 *
 * Safety contract (deliberate; review before relaxing any of it):
 * - Read-only. Only non-mutating repository reads are used:
 *   [PendingReviewRepository.getPendingAndFailed], [PendingReviewRepository.getDeadLettered],
 *   [TaskEpisodeRepository.count]. Never calls recoverStaleProcessing / markProcessing /
 *   markFailed / markFailedSingle / delete / migrateListFieldsToVersioned / ReviewScheduler.runOnce,
 *   and never touches ObjectBoxManager.delete() / closeAll().
 * - Never creates a store. ObjectBoxManager.buildStore() performs no existence check and
 *   PendingReviewRepository opens the store in its field initializer, so the expected store
 *   directory is tested BEFORE either repository is constructed; an absent store short-circuits
 *   every count to "UNAVAILABLE (store absent)" instead of materializing an empty database.
 * - Non-fatal. Any read failure renders "not observed" and never propagates to the caller;
 *   the startup hook logs failures at error level so the host process is unaffected.
 *
 * The three states are rendered unambiguously and are deliberately distinct:
 * - an observed number (0 is a real observation);
 * - "UNAVAILABLE (store absent)" — answering would require creating the store, which is forbidden;
 * - "not observed" — the store is present but the read could not be completed.
 *
 * Note on dead letters: a review that produced an episode is deleted after processing, but a
 * dead-lettered review is never deleted, so "dead_letter" rows are permanent residual evidence
 * and always appear in the grouped counts when present.
 */
object ReviewPipelineAuditReporter {

    /**
     * One point-in-time snapshot. `pendingByStatus` / `maxAttemptCountObserved` / `taskEpisodeCount`
     * are null when the observation could not be made; [storePresent] decides whether null renders
     * as "UNAVAILABLE (store absent)" or "not observed".
     */
    data class ReviewAuditSnapshot(
        val observedAtEpochMillis: Long,
        val processScope: Int,
        val activeProfileId: String,
        val expectedStoreDir: String,
        val storePresent: Boolean,
        val schedulerConstructed: Boolean?,
        val pendingByStatus: Map<String, Int>?,
        val maxAttemptCountObserved: Int?,
        val taskEpisodeCount: Long?
    ) {
        fun toReportText(): String {
            fun scheduler(): String = schedulerConstructed?.toString() ?: "not observed"
            fun counts(): String = when {
                !storePresent -> "UNAVAILABLE (store absent)"
                pendingByStatus == null -> "not observed"
                else -> pendingByStatus.toSortedMap().toString()
            }
            fun maxAttempt(): String = when {
                !storePresent -> "UNAVAILABLE (store absent)"
                pendingByStatus == null -> "not observed"
                maxAttemptCountObserved == null -> "none (no review rows observed)"
                else -> maxAttemptCountObserved.toString()
            }
            fun episodes(): String = when {
                !storePresent -> "UNAVAILABLE (store absent)"
                taskEpisodeCount == null -> "not observed"
                else -> taskEpisodeCount.toString()
            }
            return listOf(
                "observedAtEpochMillis=$observedAtEpochMillis",
                "processScope=pid=$processScope",
                "activeProfileId=$activeProfileId",
                "expectedStoreDir=$expectedStoreDir",
                "storePresent=$storePresent",
                "schedulerConstructed=${scheduler()}",
                "pendingByStatus=${counts()}",
                "maxAttemptCountObserved=${maxAttempt()}",
                "taskEpisodeCount=${episodes()}"
            ).joinToString(separator = "\n") { "[ReviewPipelineAudit] $it" }
        }
    }

    /**
     * Collects one snapshot. Suspend because the active profile id comes from a Flow.
     *
     * The pure pieces ([expectedStoreDirName], [groupByStatus], [maxAttemptCount],
     * [ReviewAuditSnapshot.toReportText]) are separate so they are JVM-testable without an
     * Android Context or an ObjectBox store.
     */
    suspend fun collect(context: Context): ReviewAuditSnapshot {
        val appContext = context.applicationContext
        val profileId = UserPreferencesManager.getInstance(appContext).activeMemorySpaceIdFlow.first()
        val storeDir = File(appContext.filesDir, expectedStoreDirName(profileId))
        val storePresent = storeDir.exists() && storeDir.isDirectory
        val base = ReviewAuditSnapshot(
            observedAtEpochMillis = System.currentTimeMillis(),
            processScope = Process.myPid(),
            activeProfileId = profileId,
            expectedStoreDir = storeDir.absolutePath,
            storePresent = storePresent,
            schedulerConstructed = ReviewScheduler.getInstance() != null,
            pendingByStatus = null,
            maxAttemptCountObserved = null,
            taskEpisodeCount = null
        )
        if (!storePresent) return base
        return try {
            val reviewRepository = PendingReviewRepository(appContext, profileId)
            val rows = reviewRepository.getPendingAndFailed() + reviewRepository.getDeadLettered()
            base.copy(
                pendingByStatus = groupByStatus(rows),
                maxAttemptCountObserved = maxAttemptCount(rows),
                taskEpisodeCount = TaskEpisodeRepository(appContext, profileId).count()
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            base
        }
    }

    /**
     * Mirrors ObjectBoxManager.buildStore's directory naming rule exactly: the "default" profile
     * uses the unsuffixed "objectbox" directory and every other profile uses "objectbox_<profileId>".
     * Duplicated on purpose: computing the directory here avoids calling ObjectBoxManager.get(),
     * whose buildStore() would create the directory when absent. Keep in sync with
     * ObjectBoxManager.buildStore.
     */
    fun expectedStoreDirName(profileId: String): String =
        if (profileId == "default") "objectbox" else "objectbox_$profileId"

    /**
     * Counts rows per raw status literal (see [PendingReviewEntity.STATUS_PENDING],
     * [PendingReviewEntity.STATUS_FAILED], [PendingReviewEntity.STATUS_DEAD_LETTER]).
     * "processing" rows are normally absent because the scheduler holds that status only for the
     * duration of a batch and recovers abandoned ones at startup.
     */
    fun groupByStatus(rows: List<PendingReviewEntity>): Map<String, Int> =
        rows.groupingBy { it.status }.eachCount()

    /** Highest attempt count across all observed rows; null when no rows exist. */
    fun maxAttemptCount(rows: List<PendingReviewEntity>): Int? =
        rows.maxOfOrNull { it.attemptCount }
}