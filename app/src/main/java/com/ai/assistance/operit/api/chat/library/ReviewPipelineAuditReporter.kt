package com.ai.assistance.operit.api.chat.library

import android.content.Context
import android.os.Process
import com.ai.assistance.operit.data.db.ObjectBoxManager
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
 * Answers the three runtime audit questions with one report per launch:
 *
 * 1. Was [ReviewScheduler] constructed and started? ([ReviewAuditSnapshot.schedulerConstructed])
 * 2. Has it executed passes, and with what outcomes? ([ReviewAuditSnapshot.schedulerAudit] —
 *    recorded by [ReviewPassAuditRecorder] inside ReviewScheduler for every pass, including
 *    passes over an empty queue, plus the startup recovery sweep)
 * 3. How many persisted reviews exist grouped by status, and how many [TaskEpisodeEntity] rows
 *    exist in the active profile's store? ([ReviewAuditSnapshot.pendingByStatus] /
 *    [ReviewAuditSnapshot.taskEpisodeCount], read from ONE consistent all-status query)
 *
 * Safety contract (deliberate; review before relaxing any of it):
 * - Read-only. Only non-mutating reads: [PendingReviewRepository.getAll],
 *   [TaskEpisodeRepository.count]. Never recoverStaleProcessing / markProcessing / markFailed /
 *   markFailedSingle / delete / migrateListFieldsToVersioned / ReviewScheduler.runOnce, and
 *   never ObjectBoxManager.delete() / closeAll().
 * - Never creates a store. Two gates protect this: the LMDB data file ([DATA_FILE_NAME]) must
 *   exist, and [ObjectBoxManager.getExisting] must return an already-open store. Every read goes
 *   through the existing-store-only accessors ([PendingReviewRepository.getAllFrom],
 *   [TaskEpisodeRepository.countFrom]); the create-capable ObjectBoxManager.get / repository
 *   constructors are never called on this path. A concurrent close/delete therefore cannot make
 *   diagnostics recreate a store: acquisition is coordinated with close/delete under
 *   ObjectBoxManager's lock, and if the store vanishes between the gate and the read the failure
 *   lands in [ReviewAuditSnapshot.readErrors] — this path never falls back to creating anything.
 * - Read failures are recorded, not swallowed: each read segment's error lands in
 *   [ReviewAuditSnapshot.readErrors] (partial results are kept), and only unexpected failures
 *   outside the read segments propagate to the caller's error logger.
 *
 * Rendered states are deliberately distinct:
 * - an observed number (0 is a real observation);
 * - "UNAVAILABLE (store absent)" — no LMDB data file; answering would require creating the
 *   store, which is forbidden;
 * - "UNAVAILABLE (store not open)" — the data file exists but no store is open for the profile
 *   (e.g. concurrently closed or deleted); answering would require opening — and possibly
 *   creating — the store, which is forbidden;
 * - "not observed" — the value could not be obtained at snapshot time.
 */
object ReviewPipelineAuditReporter {

    /** ObjectBox/LMDB database file name; its presence is the store-existence guard. */
    const val DATA_FILE_NAME = "data.mdb"

    data class ReviewAuditSnapshot(
        val observedAtEpochMillis: Long,
        val processScope: Int,
        val activeProfileId: String,
        val expectedStoreDir: String,
        val storePresent: Boolean,
        val storeOpenExisting: Boolean,
        val schedulerConstructed: Boolean?,
        val schedulerAudit: ReviewPassAudit?,
        val pendingByStatus: Map<String, Int>?,
        val maxAttemptCountObserved: Int?,
        val taskEpisodeCount: Long?,
        val readErrors: List<String>
    ) {
        /**
         * One report line per field, each carrying the "[ReviewPipelineAudit]" prefix. Callers
         * MUST log each line as its own AppLogger call: AppLogger only prefixes the first line
         * of a multiline message, and tag-anchored extraction would otherwise drop every field
         * after the first.
         */
        fun toReportLines(): List<String> {
            val lines = listOf(
                "observedAtEpochMillis=$observedAtEpochMillis",
                "processScope=pid=$processScope",
                "activeProfileId=$activeProfileId",
                "expectedStoreDir=$expectedStoreDir",
                "storePresent=$storePresent",
                "storeOpenExisting=$storeOpenExisting",
                "schedulerConstructed=${renderSchedulerConstructed()}",
                "schedulerPassCount=${renderAudit { it.passesStarted }}",
                "schedulerPassesCompleted=${renderAudit { it.passesCompleted }}",
                "schedulerLastPassStartedAtEpochMillis=${renderAudit { it.lastPassStartedAtEpochMillis }}",
                "schedulerLastPassCompletedAtEpochMillis=${renderAudit { it.lastPassCompletedAtEpochMillis }}",
                "schedulerLastPassOutcome=${renderAudit { it.lastPassOutcome }}",
                "schedulerLastPassPendingFound=${renderAudit { it.lastPassPendingFound }}",
                "schedulerLastPassSucceeded=${renderAudit { it.lastPassSucceeded }}",
                "schedulerLastPassFailedMarked=${renderAudit { it.lastPassFailedMarked }}",
                "schedulerLastPassFailedProfiles=${renderAudit { it.lastPassFailedProfiles }}",
                "schedulerStartupRecoveryCompletedAtEpochMillis=${renderAudit { it.startupRecoveryCompletedAtEpochMillis }}",
                "schedulerStartupRecoveryRecovered=${renderAudit { it.startupRecoveryRecovered }}",
                "schedulerStartupRecoveryFailedProfiles=${renderAudit { it.startupRecoveryFailedProfiles }}",
                "pendingByStatus=${renderCounts()}",
                "maxAttemptCountObserved=${renderMaxAttempt()}",
                "taskEpisodeCount=${renderEpisodes()}",
                "readErrors=${renderErrors()}"
            )
            return lines.map { "[ReviewPipelineAudit] $it" }
        }

        fun toReportText(): String = toReportLines().joinToString(separator = "\n")

        private fun renderSchedulerConstructed(): String =
            schedulerConstructed?.toString() ?: "not observed"

        private fun renderAudit(field: (ReviewPassAudit) -> Any?): String {
            val audit = schedulerAudit ?: return "not observed"
            return field(audit)?.toString() ?: "not observed"
        }

        private fun renderCounts(): String = when {
            !storePresent -> "UNAVAILABLE (store absent)"
            !storeOpenExisting -> "UNAVAILABLE (store not open)"
            pendingByStatus == null -> "not observed"
            else -> pendingByStatus.toSortedMap().toString()
        }

        private fun renderMaxAttempt(): String = when {
            !storePresent -> "UNAVAILABLE (store absent)"
            !storeOpenExisting -> "UNAVAILABLE (store not open)"
            pendingByStatus == null -> "not observed"
            maxAttemptCountObserved == null -> "none (no review rows observed)"
            else -> maxAttemptCountObserved.toString()
        }

        private fun renderEpisodes(): String = when {
            !storePresent -> "UNAVAILABLE (store absent)"
            !storeOpenExisting -> "UNAVAILABLE (store not open)"
            taskEpisodeCount == null -> "not observed"
            else -> taskEpisodeCount.toString()
        }

        private fun renderErrors(): String =
            if (readErrors.isEmpty()) "none" else readErrors.joinToString("; ")
    }

    /**
     * JVM-testable collection pipeline. Production wiring lives in [collect]; tests construct
     * this seam directly with lambdas so collection, short-circuit and failure paths are
     * exercised without an Android Context or an ObjectBox store.
     */
    class Collector(
        private val activeProfileId: suspend () -> String,
        private val storeDirFor: (String) -> File,
        private val storeDataFilePresent: (File) -> Boolean,
        private val storeOpenExisting: (String) -> Boolean,
        private val schedulerAudit: () -> ReviewPassAudit?,
        private val readReviews: (String) -> List<PendingReviewEntity>,
        private val readEpisodeCount: (String) -> Long,
        private val nowMillis: () -> Long = System::currentTimeMillis,
        private val currentPid: () -> Int = Process::myPid
    ) {
        suspend fun collect(): ReviewAuditSnapshot {
            val profileId = activeProfileId()
            val storeDir = storeDirFor(profileId)
            val storePresent = storeDataFilePresent(storeDir)
            val storeOpen = storePresent && storeOpenExisting(profileId)
            val audit = schedulerAudit()
            val base = ReviewAuditSnapshot(
                observedAtEpochMillis = nowMillis(),
                processScope = currentPid(),
                activeProfileId = profileId,
                expectedStoreDir = storeDir.absolutePath,
                storePresent = storePresent,
                storeOpenExisting = storeOpen,
                schedulerConstructed = audit != null,
                schedulerAudit = audit,
                pendingByStatus = null,
                maxAttemptCountObserved = null,
                taskEpisodeCount = null,
                readErrors = emptyList()
            )
            if (!storeOpen) return base

            var rows: List<PendingReviewEntity>? = null
            var rowsError: String? = null
            try {
                rows = readReviews(profileId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                rowsError = error.message ?: error.javaClass.simpleName
            }

            var episodeCount: Long? = null
            var episodeError: String? = null
            if (rowsError == null) {
                try {
                    episodeCount = readEpisodeCount(profileId)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    episodeError = error.message ?: error.javaClass.simpleName
                }
            } else {
                episodeError = "skipped: review read failed"
            }

            return base.copy(
                pendingByStatus = rows?.let(::groupByStatus),
                maxAttemptCountObserved = rows?.let(::maxAttemptCount),
                taskEpisodeCount = episodeCount,
                readErrors = listOfNotNull(
                    rowsError?.let { "reviewRead: $it" },
                    episodeError?.let { "episodeCount: $it" }
                )
            )
        }
    }

    /**
     * Production wiring. Suspend because the active profile id comes from a Flow.
     * Failures inside the read segments are recorded in the snapshot's [ReviewAuditSnapshot.readErrors];
     * anything else (profile lookup, cancellation) propagates to the caller.
     */
    suspend fun collect(context: Context): ReviewAuditSnapshot {
        val appContext = context.applicationContext
        return Collector(
            activeProfileId = {
                UserPreferencesManager.getInstance(appContext).activeMemorySpaceIdFlow.first()
            },
            storeDirFor = { profileId -> File(appContext.filesDir, expectedStoreDirName(profileId)) },
            storeDataFilePresent = { dir -> File(dir, DATA_FILE_NAME).isFile },
            storeOpenExisting = { profileId -> ObjectBoxManager.getExisting(profileId) != null },
            schedulerAudit = { ReviewScheduler.getInstance()?.passAudit?.snapshot() },
            readReviews = { profileId ->
                val store = ObjectBoxManager.getExisting(profileId)
                    ?: error("store closed during audit: profileId=$profileId")
                PendingReviewRepository.getAllFrom(store)
            },
            readEpisodeCount = { profileId ->
                val store = ObjectBoxManager.getExisting(profileId)
                    ?: error("store closed during audit: profileId=$profileId")
                TaskEpisodeRepository.countFrom(store)
            }
        ).collect()
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
     * [PendingReviewEntity.STATUS_PROCESSING], [PendingReviewEntity.STATUS_FAILED],
     * [PendingReviewEntity.STATUS_DEAD_LETTER]). PROCESSING rows are included: the rows come from
     * [PendingReviewRepository.getAll], the single all-status query.
     */
    fun groupByStatus(rows: List<PendingReviewEntity>): Map<String, Int> =
        rows.groupingBy { it.status }.eachCount()

    /** Highest attempt count across all observed rows; null when no rows exist. */
    fun maxAttemptCount(rows: List<PendingReviewEntity>): Int? =
        rows.maxOfOrNull { it.attemptCount }
}