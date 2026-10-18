package com.ai.assistance.operit.data.learning

/**
 * Typed, structured observation of a single fact about the task environment (Step 1, Area 2).
 *
 * This is NOT a summary counter like [ReviewSignalDeriver.TurnObservations]. It is a state-ledger
 * entry: one predicate about one subject, with stable event/source identity, explicit scope, an
 * observation time, an optional validity window, and an explicit source/trust classification.
 *
 * Design rules:
 *  - Unknown metadata stays unknown. A field that the runtime did not actually observe is the empty
 *    string (or [TypedValue.Unknown]), never a guessed value.
 *  - Requested, reported and observed state are separated by [TrustLevel]. A generic exit code or a
 *    natural-language tool output is at best REPORTED; it is never OBSERVED unless a structured
 *    field proves it. OS permissions and file existence are never inferred from a generic exit code.
 *  - Independent observations on different resources are separate records keyed by [eventId]; they
 *    never overwrite each other.
 *
 * Persistence is deferred: this model is held in memory only (see [TaskContextSnapshotStore]); a
 * process restart means the fact is unknown again.
 */
data class ObservationRecord(
    /** Stable identity of this observation event. Two records with the same eventId are the same event. */
    val eventId: String,
    /** Identity of the source that produced the observation (e.g. a tool call id). */
    val sourceId: String,
    /** Memory space (profile) this observation belongs to. */
    val memorySpace: String,
    /** Project scope, or "" when unknown. */
    val projectScope: String,
    /** Task scope, or "" when unknown. */
    val taskScope: String,
    /** Executor identity (which executor produced it), or "" when unknown. */
    val executorId: String,
    /** Environment identity (device/host/workspace), or "" when unknown. */
    val environment: String,
    /** Session identity, or "" when unknown. */
    val sessionId: String,
    /** The typed predicate name, e.g. "tool.execution.status". */
    val predicate: String,
    /** The subject the predicate is about, e.g. a tool name or resource id. */
    val subject: String,
    /** The typed value of the predicate for the subject. */
    val value: TypedValue,
    /** When the fact was observed, in epoch milliseconds. */
    val observedAtMs: Long,
    /** When the fact stops being valid, in epoch milliseconds; 0 means no expiry. */
    val validUntilMs: Long,
    /** Where the observation came from. */
    val source: ObservationSource,
    /** How much the observation can be trusted. */
    val trust: TrustLevel,
    /** Schema version of this record. */
    val schemaVersion: Int = 1
)

/** Where an [ObservationRecord] came from. */
enum class ObservationSource {
    /** Produced by the tool-execution runtime from a structured tool result. */
    TOOL_EXECUTION,
    /** Produced by a deliberate user action. */
    USER_ACTION,
    /** Produced by the system itself. */
    SYSTEM
}

/**
 * Separation of requested, reported and observed state.
 *
 *  - [OBSERVED]: the runtime actually saw it (e.g. a structured field of a tool result the executor
 *    produced). Only this level may satisfy an observed-only predicate.
 *  - [REPORTED]: a tool or model claimed it in text or in an unstructured field. It is evidence of a
 *    claim, not proof of the fact.
 *  - [REQUESTED]: the user asked for it; it is a goal, not an achieved state.
 */
enum class TrustLevel {
    OBSERVED,
    REPORTED,
    REQUESTED
}

/** A typed value. [Unknown] is explicit: the value is not known, not empty. */
sealed class TypedValue {
    data class StringValue(val value: String) : TypedValue()
    data class BooleanValue(val value: Boolean) : TypedValue()
    data class IntValue(val value: Int) : TypedValue()
    object Unknown : TypedValue()
}

/**
 * A bounded, scoped snapshot of observations for one task context (Step 1, Area 2).
 *
 * A snapshot is only valid for the exact scope/executor/environment/session it was captured in.
 * [isApplicableTo] encodes that rule; a snapshot captured in one session must not be applied to
 * another. The snapshot is advisory: it is passed through preflight plumbing alongside the bounded
 * text context and does NOT gate execution.
 */
data class TaskContextSnapshot(
    val memorySpace: String,
    val projectScope: String,
    val taskScope: String,
    val executorId: String,
    val environment: String,
    val sessionId: String,
    val observations: List<ObservationRecord>,
    val capturedAtMs: Long,
    val schemaVersion: Int = 1
) {
    /**
     * True when this snapshot was captured in the same scope/executor/environment/session as the
     * given coordinates. A mismatch means the snapshot must be treated as invalid (unknown).
     */
    fun isApplicableTo(
        memorySpace: String,
        projectScope: String,
        taskScope: String,
        executorId: String,
        environment: String,
        sessionId: String
    ): Boolean =
        this.memorySpace == memorySpace &&
            this.projectScope == projectScope &&
            this.taskScope == taskScope &&
            this.executorId == executorId &&
            this.environment == environment &&
            this.sessionId == sessionId
}
