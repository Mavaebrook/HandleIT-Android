package com.ai.assistance.operit.data.learning

/**
 * Pure, bounded policy deciding whether a post-task Reflector/Curator review should be triggered.
 *
 * Design goals (docs/TODO/reliable-learning-architect-20261016/01-design.md §4):
 *  - trigger on explicit corrections, failures/repeated repairs, or complex successes with
 *    reusable lessons;
 *  - NO every-turn teacher call.
 */
object ReviewTriggerPolicy {

    /** Signals observed for a completed task turn. */
    data class ReviewSignals(
        val explicitUserCorrection: Boolean = false,
        val taskFailed: Boolean = false,
        val repeatedRepairCount: Int = 0,
        val complexSuccessWithReusableLesson: Boolean = false
    )

    data class ReviewDecision(
        val shouldReview: Boolean,
        val reason: String
    )

    const val REPEATED_REPAIR_THRESHOLD = 2

    fun decide(signals: ReviewSignals): ReviewDecision {
        return when {
            signals.explicitUserCorrection ->
                ReviewDecision(true, "explicit user correction")
            signals.taskFailed ->
                ReviewDecision(true, "task failure")
            signals.repeatedRepairCount >= REPEATED_REPAIR_THRESHOLD ->
                ReviewDecision(true, "repeated repairs ($REPEATED_REPAIR_THRESHOLD+)")
            signals.complexSuccessWithReusableLesson ->
                ReviewDecision(true, "complex success with reusable lesson")
            else ->
                ReviewDecision(false, "no review trigger")
        }
    }
}
