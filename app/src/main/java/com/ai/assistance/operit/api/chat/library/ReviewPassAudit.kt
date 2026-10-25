package com.ai.assistance.operit.api.chat.library

/**
 * Thread-safe recorder of review-scheduler pass events, kept in memory for the lifetime of the
 * process. Exists so a runtime audit can answer "does ReviewScheduler start AND execute passes?"
 * with real evidence: pass start/completion/outcome counters plus the startup recovery sweep.
 *
 * All mutators and [snapshot] are synchronized so concurrent callers (the scheduler loop and the
 * audit reporter) observe consistent counters. State is deliberately in-memory only: audit
 * evidence is consumed via the DEBUG log lines written by ReviewScheduler (one per pass) and via
 * ReviewPipelineAuditReporter snapshots; nothing here persists across process restarts.
 */
class ReviewPassAuditRecorder {

    companion object {
        /** Every attempted profile drained successfully; the counts are a complete observation. */
        const val OUTCOME_COMPLETED = "completed"

        /** At least one profile drained and at least one failed; the counts are partial. */
        const val OUTCOME_PARTIAL = "partial"

        /** Every attempted profile failed; no counts are available. */
        const val OUTCOME_FAILED = "failed"

        /** The pass itself threw before completing its drain. */
        const val OUTCOME_ERROR = "error"

        /** The pass was cancelled; structured concurrency is preserved (the exception is rethrown). */
        const val OUTCOME_CANCELLED = "cancelled"

        /**
         * Terminal outcome for a drain pass, derived from the per-profile failure count so an
         * all-failed pass can never be confused with a completed pass over an empty queue.
         * Pure and side-effect free: directly unit-testable.
         */
        fun completionOutcome(failedProfiles: Int, profilesAttempted: Int): String = when {
            profilesAttempted <= 0 -> OUTCOME_COMPLETED
            failedProfiles <= 0 -> OUTCOME_COMPLETED
            failedProfiles < profilesAttempted -> OUTCOME_PARTIAL
            else -> OUTCOME_FAILED
        }
    }

    private var passesStarted: Long = 0
    private var passesCompleted: Long = 0
    private var lastPassStartedAtEpochMillis: Long? = null
    private var lastPassCompletedAtEpochMillis: Long? = null
    private var lastPassOutcome: String? = null
    private var lastPassPendingFound: Int? = null
    private var lastPassSucceeded: Int? = null
    private var lastPassFailedMarked: Int? = null
    private var lastPassFailedProfiles: Int? = null
    private var startupRecoveryCompletedAtEpochMillis: Long? = null
    private var startupRecoveryRecovered: Int? = null
    private var startupRecoveryFailedProfiles: Int? = null

    @Synchronized
    fun recordPassStart(nowMillis: Long = System.currentTimeMillis()) {
        passesStarted++
        lastPassStartedAtEpochMillis = nowMillis
    }

    @Synchronized
    fun recordPassCompleted(
        outcome: String,
        pendingFound: Int?,
        succeeded: Int?,
        failedMarked: Int?,
        failedProfiles: Int? = null,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        passesCompleted++
        lastPassCompletedAtEpochMillis = nowMillis
        lastPassOutcome = outcome
        lastPassPendingFound = pendingFound
        lastPassSucceeded = succeeded
        lastPassFailedMarked = failedMarked
        lastPassFailedProfiles = failedProfiles
    }

    @Synchronized
    fun recordStartupRecovery(
        recovered: Int,
        failedProfiles: Int = 0,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        startupRecoveryCompletedAtEpochMillis = nowMillis
        startupRecoveryRecovered = recovered
        startupRecoveryFailedProfiles = failedProfiles
    }

    @Synchronized
    fun snapshot(): ReviewPassAudit =
        ReviewPassAudit(
            passesStarted = passesStarted,
            passesCompleted = passesCompleted,
            lastPassStartedAtEpochMillis = lastPassStartedAtEpochMillis,
            lastPassCompletedAtEpochMillis = lastPassCompletedAtEpochMillis,
            lastPassOutcome = lastPassOutcome,
            lastPassPendingFound = lastPassPendingFound,
            lastPassSucceeded = lastPassSucceeded,
            lastPassFailedMarked = lastPassFailedMarked,
            lastPassFailedProfiles = lastPassFailedProfiles,
            startupRecoveryCompletedAtEpochMillis = startupRecoveryCompletedAtEpochMillis,
            startupRecoveryRecovered = startupRecoveryRecovered,
            startupRecoveryFailedProfiles = startupRecoveryFailedProfiles
        )
}

/** Immutable point-in-time copy of [ReviewPassAuditRecorder] state. */
data class ReviewPassAudit(
    val passesStarted: Long,
    val passesCompleted: Long,
    val lastPassStartedAtEpochMillis: Long?,
    val lastPassCompletedAtEpochMillis: Long?,
    val lastPassOutcome: String?,
    val lastPassPendingFound: Int?,
    val lastPassSucceeded: Int?,
    val lastPassFailedMarked: Int?,
    val lastPassFailedProfiles: Int?,
    val startupRecoveryCompletedAtEpochMillis: Long?,
    val startupRecoveryRecovered: Int?,
    val startupRecoveryFailedProfiles: Int?
)
