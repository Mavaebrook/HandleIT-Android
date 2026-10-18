package com.ai.assistance.operit.data.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM regression tests for Stage D (part 2a): pure bounded review-trigger policy in
 * [ReviewTriggerPolicy]. No Android or ObjectBox dependencies.
 *
 * R14 (docs/TODO/learning-pipeline-hardening-20261017/01-findings.md): the complexity signal is
 * renamed to `complexSuccessfulTurn` because it measures complexity and success only, never a
 * lesson; a lexical marker match is a weaker CANDIDATE correction, and an explicit user correction
 * takes precedence over it.
 */
class ReviewTriggerPolicyTest {

    // --- Criterion 1: explicit user correction triggers review ---

    @Test
    fun explicitUserCorrection_triggersReview() {
        // Criterion 1: an explicit user correction must trigger a post-task review.
        val decision = ReviewTriggerPolicy.decide(
            ReviewTriggerPolicy.ReviewSignals(explicitUserCorrection = true)
        )

        assertTrue(decision.shouldReview)
        assertTrue(decision.reason.contains("correction"))
    }

    // --- Criterion 2: task failure triggers review ---

    @Test
    fun taskFailure_triggersReview() {
        // Criterion 2: a failed task must trigger a post-task review.
        val decision = ReviewTriggerPolicy.decide(
            ReviewTriggerPolicy.ReviewSignals(taskFailed = true)
        )

        assertTrue(decision.shouldReview)
        assertTrue(decision.reason.contains("failure"))
    }

    // --- Criterion 3: repeated repairs at/above threshold trigger; below does not ---

    @Test
    fun repeatedRepairs_atThreshold_triggersReview() {
        // Criterion 3: repeated repairs at the threshold (2) must trigger a review.
        val decision = ReviewTriggerPolicy.decide(
            ReviewTriggerPolicy.ReviewSignals(
                repeatedRepairCount = ReviewTriggerPolicy.REPEATED_REPAIR_THRESHOLD
            )
        )

        assertTrue(decision.shouldReview)
        assertTrue(decision.reason.contains("repeated repairs"))
    }

    @Test
    fun repeatedRepairs_aboveThreshold_triggersReview() {
        // Criterion 3: repeated repairs above the threshold must trigger a review.
        val decision = ReviewTriggerPolicy.decide(
            ReviewTriggerPolicy.ReviewSignals(
                repeatedRepairCount = ReviewTriggerPolicy.REPEATED_REPAIR_THRESHOLD + 1
            )
        )

        assertTrue(decision.shouldReview)
    }

    @Test
    fun repeatedRepairs_belowThreshold_doesNotTriggerReview() {
        // Criterion 3: repeated repairs below the threshold must NOT trigger a review.
        val decision = ReviewTriggerPolicy.decide(
            ReviewTriggerPolicy.ReviewSignals(
                repeatedRepairCount = ReviewTriggerPolicy.REPEATED_REPAIR_THRESHOLD - 1
            )
        )

        assertFalse(decision.shouldReview)
    }

    // --- Criterion 4: a complex successful turn triggers review, without asserting a lesson ---

    @Test
    fun complexSuccessfulTurn_triggersReview() {
        // Criterion 4 (R14): a complex successful turn must trigger a review. The reason must not
        // assert that a lesson exists — the derivation never checks for one.
        val decision = ReviewTriggerPolicy.decide(
            ReviewTriggerPolicy.ReviewSignals(complexSuccessfulTurn = true)
        )

        assertTrue(decision.shouldReview)
        assertTrue(decision.reason.contains("complex successful turn"))
        assertFalse(decision.reason.contains("lesson"))
    }

    // --- Criterion 5: no signals -> no review (no every-turn teacher call) ---

    @Test
    fun noSignals_doesNotTriggerReview() {
        // Criterion 5: with no signals there is no review -> no every-turn teacher call.
        val decision = ReviewTriggerPolicy.decide(ReviewTriggerPolicy.ReviewSignals())

        assertFalse(decision.shouldReview)
        assertEquals("no review trigger", decision.reason)
    }

    // --- Criterion 6: precedence - explicit correction wins over other signals ---

    @Test
    fun explicitCorrection_takesPrecedenceOverOtherSignals() {
        // Criterion 6: explicit correction wins over other signals; reason mentions correction.
        val decision = ReviewTriggerPolicy.decide(
            ReviewTriggerPolicy.ReviewSignals(
                explicitUserCorrection = true,
                taskFailed = true,
                repeatedRepairCount = 5,
                complexSuccessfulTurn = true
            )
        )

        assertTrue(decision.shouldReview)
        assertTrue(decision.reason.contains("correction"))
    }

    // --- R14: candidate correction is a weaker signal, checked last ---

    @Test
    fun candidateCorrection_triggersReview() {
        // R14: a candidate correction alone triggers a review, and the reason says it is a
        // candidate rather than an explicit correction.
        val decision = ReviewTriggerPolicy.decide(
            ReviewTriggerPolicy.ReviewSignals(candidateCorrection = true)
        )

        assertTrue(decision.shouldReview)
        assertTrue(decision.reason.contains("candidate"))
    }

    @Test
    fun explicitCorrection_takesPrecedenceOverCandidate() {
        // R14: an explicit correction is stronger than a candidate and is checked first, so the
        // reason reports the explicit correction.
        val decision = ReviewTriggerPolicy.decide(
            ReviewTriggerPolicy.ReviewSignals(
                explicitUserCorrection = true,
                candidateCorrection = true
            )
        )

        assertTrue(decision.shouldReview)
        assertTrue(decision.reason.contains("explicit"))
    }
}
