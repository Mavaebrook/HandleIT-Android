package com.ai.assistance.operit.data.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM regression tests for Stage D (part 2a): pure bounded review-trigger policy in
 * [ReviewTriggerPolicy]. No Android or ObjectBox dependencies.
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

    // --- Criterion 4: complex success with reusable lesson triggers review ---

    @Test
    fun complexSuccessWithReusableLesson_triggersReview() {
        // Criterion 4: a complex success carrying a reusable lesson must trigger a review.
        val decision = ReviewTriggerPolicy.decide(
            ReviewTriggerPolicy.ReviewSignals(complexSuccessWithReusableLesson = true)
        )

        assertTrue(decision.shouldReview)
        assertTrue(decision.reason.contains("reusable lesson"))
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
                complexSuccessWithReusableLesson = true
            )
        )

        assertTrue(decision.shouldReview)
        assertTrue(decision.reason.contains("correction"))
    }
}
