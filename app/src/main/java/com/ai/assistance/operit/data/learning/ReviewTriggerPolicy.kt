package com.ai.assistance.operit.data.learning

/**
 * Pure, bounded policy deciding whether a post-task Reflector/Curator review should be triggered.
 *
 * Design goals (docs/TODO/reliable-learning-architect-20261016/01-design.md §4):
 *  - trigger on explicit corrections, failures/repeated repairs, or complex successful turns;
 *  - NO every-turn teacher call.
 *
 * R14 (docs/TODO/learning-pipeline-hardening-20261017/01-findings.md): a trigger establishes only
 * that something is worth looking at. It does not establish why, and it does not establish that a
 * lesson exists. The signal names and the decision reasons therefore claim only what was measured.
 */
object ReviewTriggerPolicy {

    /**
     * Signals observed for a completed task turn.
     *
     * R14: the raw observed event and its interpreted meaning are kept separate. [observed] records
     * what was actually seen during the turn; the boolean fields are the meaning assigned to it.
     * A reader can therefore tell what was observed from what was inferred.
     */
    data class ReviewSignals(
        val observed: ObservedTurnEvents = ObservedTurnEvents(),
        val explicitUserCorrection: Boolean = false,
        val candidateCorrection: Boolean = false,
        val taskFailed: Boolean = false,
        val repeatedRepairCount: Int = 0,
        val complexSuccessfulTurn: Boolean = false
    )

    /**
     * Raw events recorded during the turn, before any interpretation. R14: these are the facts the
     * signals are derived from, kept so the review enqueue can pass what was actually observed to
     * the Reflector instead of only the derived booleans.
     */
    data class ObservedTurnEvents(
        val failedToolResultCount: Int = 0,
        val truncatedToolRecoveryCount: Int = 0,
        val toolResultCount: Int = 0,
        val modelHopCount: Int = 0,
        val userTurnCount: Int = 0,
        /** The lexical correction marker that matched, or "" when none matched. */
        val matchedCorrectionMarker: String = "",
        /** True when the user deliberately marked the reply wrong through an explicit UI action. */
        val explicitCorrectionAction: Boolean = false
    )

    data class ReviewDecision(
        val shouldReview: Boolean,
        val reason: String
    )

    const val REPEATED_REPAIR_THRESHOLD = 2

    /**
     * Decide whether the recorded signals warrant a review.
     *
     * Ordering (R14): [ReviewSignals.explicitUserCorrection] is checked first because it is the
     * strongest evidence — the user deliberately marked the reply wrong. [ReviewSignals.candidateCorrection]
     * is checked last, after [ReviewSignals.complexSuccessfulTurn], so a lexical candidate alone only
     * triggers when nothing stronger fired.
     *
     * Why a candidate correction may trigger a review on its own: a lexical marker is still evidence
     * that the user pushed back, so it is worth investigating. It is bounded, not an every-turn
     * teacher call: the deriver only sets it on a turn that already has a preceding assistant turn
     * and a matched marker, so a first user message, a plain reply, or a turn with no assistant
     * output before it can never set it. This choice is deliberate and recorded here: the candidate
     * is weaker than an explicit correction, and the reason string says so.
     */
    fun decide(signals: ReviewSignals): ReviewDecision {
        return when {
            signals.explicitUserCorrection ->
                ReviewDecision(true, "explicit user correction")
            signals.taskFailed ->
                ReviewDecision(true, "task failure")
            signals.repeatedRepairCount >= REPEATED_REPAIR_THRESHOLD ->
                ReviewDecision(true, "repeated repairs ($REPEATED_REPAIR_THRESHOLD+)")
            signals.complexSuccessfulTurn ->
                ReviewDecision(true, "complex successful turn warranted review")
            signals.candidateCorrection ->
                ReviewDecision(true, "candidate correction (lexical marker, unconfirmed)")
            else ->
                ReviewDecision(false, "no review trigger")
        }
    }
}
