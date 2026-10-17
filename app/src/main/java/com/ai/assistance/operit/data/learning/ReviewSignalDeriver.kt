package com.ai.assistance.operit.data.learning

/**
 * Deterministic derivation of [ReviewTriggerPolicy.ReviewSignals] from recorded turn events.
 *
 * Every signal is computed from events that were actually recorded during the turn — tool
 * results, model hops, and conversation turns — never from model self-judgement. This is the
 * A02 fix for finding R02: the signals were previously hardcoded to false, so no review was
 * ever enqueued.
 *
 * Signal-to-event mapping (implemented behaviour, documented in 01-findings.md under R02):
 *  - explicitUserCorrection: the turn's last user message is a correction turn, detected
 *    lexically by [CORRECTION_MARKERS] against the trimmed, lower-cased user content, and only
 *    when an assistant turn precedes it in the same turn's history.
 *  - taskFailed: at least one tool result in the turn reported success = false, or at least one
 *    truncated/unclosed tool round was invalidated during the turn.
 *  - repeatedRepairCount: the number of failed tool results in the turn; each failed tool result
 *    is one repair attempt.
 *  - complexSuccessWithReusableLesson: the turn had no failed tool results and no invalidated
 *    tool round, and it exceeded [COMPLEX_TURN_HOP_THRESHOLD] model hops or
 *    [COMPLEX_TURN_TOOL_THRESHOLD] tool results.
 *
 * [ReviewTriggerPolicy] remains the single decision point; this class only produces signals.
 */
object ReviewSignalDeriver {

    /** Model hops at or above which a successful turn counts as complex. */
    const val COMPLEX_TURN_HOP_THRESHOLD = 3

    /** Tool results at or above which a successful turn counts as complex. */
    const val COMPLEX_TURN_TOOL_THRESHOLD = 3

    /**
     * Deterministic lexical correction markers. A user turn is treated as a correction when its
     * trimmed, lower-cased content contains one of these markers. This is a recorded-text
     * detector, not a model judgement.
     */
    val CORRECTION_MARKERS: List<String> = listOf(
        "no,",
        "that's wrong",
        "that is wrong",
        "incorrect",
        "not what i asked",
        "actually,",
        "instead,",
        "you misunderstood",
        "wrong",
        "fix that",
        "redo",
        "try again",
        "不对",
        "错了",
        "不是这样",
        "重新",
        "改一下"
    )

    /** Deterministic per-turn facts recorded while the turn ran. */
    data class TurnObservations(
        val toolResultCount: Int = 0,
        val failedToolResultCount: Int = 0,
        val truncatedToolRecoveryCount: Int = 0,
        val modelHopCount: Int = 0,
        val userTurnCount: Int = 0,
        val lastUserTurnContent: String = "",
        val previousAssistantTurnContent: String = ""
    )

    /** Mutable accumulator written during the turn, converted to [TurnObservations] at the end. */
    class TurnObservationAccumulator {
        var toolResultCount: Int = 0
        var failedToolResultCount: Int = 0
        var truncatedToolRecoveryCount: Int = 0

        fun toObservations(
            modelHopCount: Int,
            userTurnCount: Int,
            lastUserTurnContent: String,
            previousAssistantTurnContent: String
        ): TurnObservations = TurnObservations(
            toolResultCount = toolResultCount,
            failedToolResultCount = failedToolResultCount,
            truncatedToolRecoveryCount = truncatedToolRecoveryCount,
            modelHopCount = modelHopCount,
            userTurnCount = userTurnCount,
            lastUserTurnContent = lastUserTurnContent,
            previousAssistantTurnContent = previousAssistantTurnContent
        )
    }

    fun derive(observations: TurnObservations): ReviewTriggerPolicy.ReviewSignals {
        val taskFailed =
            observations.failedToolResultCount > 0 || observations.truncatedToolRecoveryCount > 0
        val turnSucceeded = !taskFailed
        val complexSuccess =
            turnSucceeded &&
                (observations.modelHopCount >= COMPLEX_TURN_HOP_THRESHOLD ||
                    observations.toolResultCount >= COMPLEX_TURN_TOOL_THRESHOLD)
        return ReviewTriggerPolicy.ReviewSignals(
            explicitUserCorrection = isCorrectionTurn(observations),
            taskFailed = taskFailed,
            repeatedRepairCount = observations.failedToolResultCount,
            complexSuccessWithReusableLesson = complexSuccess
        )
    }

    /**
     * A turn is a correction turn when an assistant turn precedes the user turn in the same
     * turn's history and the user content carries a [CORRECTION_MARKERS] marker.
     */
    fun isCorrectionTurn(observations: TurnObservations): Boolean {
        if (observations.previousAssistantTurnContent.isBlank()) return false
        val normalized = observations.lastUserTurnContent.trim().lowercase()
        if (normalized.isEmpty()) return false
        return CORRECTION_MARKERS.any { normalized.contains(it) }
    }
}
