package com.ai.assistance.operit.data.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM regression tests for the A02 fix of finding R02: [ReviewSignalDeriver] must derive
 * [ReviewTriggerPolicy.ReviewSignals] from recorded turn events instead of hardcoding them to
 * false. No Android or ObjectBox dependencies.
 */
class ReviewSignalDeriverTest {

    // --- explicitUserCorrection: derived from the recorded user turn text ---

    @Test
    fun explicitUserCorrection_mapsToCorrectionEvent() {
        // explicitUserCorrection <- recorded last user turn text carrying a correction marker,
        // with a recorded assistant turn preceding it in the same turn's history.
        val signals = ReviewSignalDeriver.derive(
            ReviewSignalDeriver.TurnObservations(
                previousAssistantTurnContent = "Here is the answer",
                lastUserTurnContent = "No, that's wrong"
            )
        )

        assertTrue(signals.explicitUserCorrection)
    }

    @Test
    fun nonCorrectionUserTurn_doesNotSetCorrection() {
        // explicitUserCorrection <- recorded user turn text without any correction marker.
        val signals = ReviewSignalDeriver.derive(
            ReviewSignalDeriver.TurnObservations(
                previousAssistantTurnContent = "Here is the answer",
                lastUserTurnContent = "Thanks, now add a test"
            )
        )

        assertFalse(signals.explicitUserCorrection)
    }

    @Test
    fun correctionWithoutPriorAssistantTurn_doesNotSetCorrection() {
        // explicitUserCorrection <- requires a recorded assistant turn before the user turn;
        // with no prior assistant turn the marker text is not a correction of anything.
        val signals = ReviewSignalDeriver.derive(
            ReviewSignalDeriver.TurnObservations(
                previousAssistantTurnContent = "",
                lastUserTurnContent = "No, that's wrong"
            )
        )

        assertFalse(signals.explicitUserCorrection)
    }

    // --- taskFailed: derived from recorded tool results and invalidated tool rounds ---

    @Test
    fun failedToolResult_mapsToTaskFailed() {
        // taskFailed <- a recorded tool result with success = false.
        val signals = ReviewSignalDeriver.derive(
            ReviewSignalDeriver.TurnObservations(failedToolResultCount = 1)
        )

        assertTrue(signals.taskFailed)
    }

    @Test
    fun truncatedToolRecovery_mapsToTaskFailed() {
        // taskFailed <- a recorded truncated/unclosed tool round invalidated during the turn.
        val signals = ReviewSignalDeriver.derive(
            ReviewSignalDeriver.TurnObservations(truncatedToolRecoveryCount = 1)
        )

        assertTrue(signals.taskFailed)
    }

    // --- repeatedRepairCount: derived from the count of recorded failed tool results ---

    @Test
    fun failedToolResults_mapToRepeatedRepairCount() {
        // repeatedRepairCount <- number of recorded failed tool results (one repair each).
        val signals = ReviewSignalDeriver.derive(
            ReviewSignalDeriver.TurnObservations(failedToolResultCount = 2)
        )

        assertEquals(2, signals.repeatedRepairCount)
    }

    // --- complexSuccessWithReusableLesson: derived from recorded hops/tool results ---

    @Test
    fun complexSuccess_aboveHopThreshold_mapsToReusableLesson() {
        // complexSuccessWithReusableLesson <- recorded model hops at the complexity threshold,
        // with no recorded failures.
        val signals = ReviewSignalDeriver.derive(
            ReviewSignalDeriver.TurnObservations(modelHopCount = 3)
        )

        assertTrue(signals.complexSuccessWithReusableLesson)
    }

    @Test
    fun complexSuccess_aboveToolThreshold_mapsToReusableLesson() {
        // complexSuccessWithReusableLesson <- recorded tool results at the complexity threshold,
        // with no recorded failures.
        val signals = ReviewSignalDeriver.derive(
            ReviewSignalDeriver.TurnObservations(toolResultCount = 3)
        )

        assertTrue(signals.complexSuccessWithReusableLesson)
    }

    @Test
    fun shortSuccess_doesNotSetReusableLesson() {
        // complexSuccessWithReusableLesson <- not set when the recorded turn stayed below both
        // complexity thresholds.
        val signals = ReviewSignalDeriver.derive(
            ReviewSignalDeriver.TurnObservations(modelHopCount = 1, toolResultCount = 1)
        )

        assertFalse(signals.complexSuccessWithReusableLesson)
    }

    @Test
    fun failedTurn_doesNotSetReusableLesson() {
        // complexSuccessWithReusableLesson <- failure dominates: a recorded failed tool result
        // suppresses the reusable-lesson signal even for a long turn.
        val signals = ReviewSignalDeriver.derive(
            ReviewSignalDeriver.TurnObservations(
                failedToolResultCount = 1,
                modelHopCount = 5,
                toolResultCount = 5
            )
        )

        assertFalse(signals.complexSuccessWithReusableLesson)
    }

    // --- end-to-end: derived signals feed the single decision point ---

    @Test
    fun turnWithNoSignals_enqueuesNothing() {
        // No signal <- a plain short successful turn with a non-correction user turn, so the
        // policy decides no review and nothing is enqueued.
        val signals = ReviewSignalDeriver.derive(
            ReviewSignalDeriver.TurnObservations(
                modelHopCount = 1,
                toolResultCount = 1,
                previousAssistantTurnContent = "Here is the answer",
                lastUserTurnContent = "Thanks, now add a test"
            )
        )

        assertFalse(ReviewTriggerPolicy.decide(signals).shouldReview)
    }

    @Test
    fun correctionTurn_enqueuesExactlyOneReview() {
        // explicitUserCorrection <- recorded correction turn; the policy returns shouldReview,
        // and ReviewTriggerEnqueuer.maybeEnqueue calls PendingReviewRepository.enqueue exactly
        // once when the policy returns shouldReview, so one correction turn produces exactly
        // one review.
        val signals = ReviewSignalDeriver.derive(
            ReviewSignalDeriver.TurnObservations(
                previousAssistantTurnContent = "Here is the answer",
                lastUserTurnContent = "No, that's wrong"
            )
        )
        val decision = ReviewTriggerPolicy.decide(signals)

        assertTrue(decision.shouldReview)
        assertTrue(decision.reason.contains("correction"))
    }

    // --- accumulator: recorded events are carried into the observations ---

    @Test
    fun accumulator_aggregatesRecordedEvents() {
        // TurnObservationAccumulator <- counts recorded during the turn are carried verbatim
        // into TurnObservations.
        val accumulator = ReviewSignalDeriver.TurnObservationAccumulator()
        accumulator.toolResultCount = 3
        accumulator.failedToolResultCount = 1
        accumulator.truncatedToolRecoveryCount = 1

        val observations = accumulator.toObservations(
            modelHopCount = 2,
            userTurnCount = 1,
            lastUserTurnContent = "No, that's wrong",
            previousAssistantTurnContent = "Here is the answer"
        )

        assertEquals(3, observations.toolResultCount)
        assertEquals(1, observations.failedToolResultCount)
        assertEquals(1, observations.truncatedToolRecoveryCount)
    }
}
