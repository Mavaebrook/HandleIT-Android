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
        const val OUTCOME_COMPLETED = "completed"
        const val OUTCOME_ERROR = "error"
        const val OUTCOME_CANCELLED = "cancelled"
    }

    private var passesStarted: Long = 0
    private var passesCompleted: Long = 0
    private var lastPassStartedAtEpochMillis: Long? = null
    private var lastPassCompletedAtEpochMillis: Long? = null
    private var lastPassOutcome: String? = null
    private var lastPassPendingFound: Int? = null
    private var lastPassSucceeded: Int? = null
    private var lastPassFailedMarked: Int? = null
    private var startupRecoveryCompletedAtEpochMillis: Long? = null
    private var startupRecoveryRecovered: Int? = null

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
        nowMillis: Long = System.currentTimeMillis()
    ) {
        passesCompleted++
        lastPassCompletedAtEpochMillis = nowMillis
        lastPassOutcome = outcome
        lastPassPendingFound = pendingFound
        lastPassSucceeded = succeeded
        lastPassFailedMarked = failedMarked
    }

    @Synchronized
    fun recordStartupRecovery(recovered: Int, nowMillis: Long = System.currentTimeMillis()) {
        startupRecoveryCompletedAtEpochMillis = nowMillis
        startupRecoveryRecovered = recovered
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
            startupRecoveryCompletedAtEpochMillis = startupRecoveryCompletedAtEpochMillis,
            startupRecoveryRecovered = startupRecoveryRecovered
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
    val startupRecoveryCompletedAtEpochMillis: Long?,
    val startupRecoveryRecovered: Int?
)