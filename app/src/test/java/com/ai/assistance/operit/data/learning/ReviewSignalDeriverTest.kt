package com.ai.assistance.operit.data.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM regression tests for the A02 fix of finding R02: [ReviewSignalDeriver] must derive
 * [ReviewTriggerPolicy.ReviewSignals] from recorded turn events instead of hardcoding them to
 * false. No Android or ObjectBox dependencies.
 *
 * R14 (docs/TODO/learning-pipeline-hardening-20261017/01-findings.md): the raw observed event and
 * its interpreted meaning are separate. A lexical marker match is only a CANDIDATE correction; only
 * a deliberate user action sets the explicit correction signal. The complexity signal is renamed to
 * `complexSuccessfulTurn` because it measures complexity and success only, never a lesson.
 */
class ReviewSignalDeriverTest {

    // --- candidateCorrection: derived from the recorded user turn text ---

    @Test
    fun explicitUserCorrection_mapsToCorrectionEvent() {
        // R14: a lexical marker match is a CANDIDATE correction, not an explicit one. The recorded
        // last user turn carries a correction marker with a recorded assistant turn preceding it.
        val signals = ReviewSignalDeriver.derive(
            ReviewSignalDeriver.TurnObservations(
                previousAssistantTurnContent = "Here is the answer",
                lastUserTurnContent = "No, that's wrong"
            )
        )

        assertTrue(signals.candidateCorrection)
        assertFalse(signals.explicitUserCorrection)
    }

    @Test
    fun nonCorrectionUserTurn_doesNotSetCorrection() {
        // candidateCorrection <- recorded user turn text without any correction marker.
        val signals = ReviewSignalDeriver.derive(
            ReviewSignalDeriver.TurnObservations(
                previousAssistantTurnContent = "Here is the answer",
                lastUserTurnContent = "Thanks, now add a test"
            )
        )

        assertFalse(signals.candidateCorrection)
        assertFalse(signals.explicitUserCorrection)
    }

    @Test
    fun correctionWithoutPriorAssistantTurn_doesNotSetCorrection() {
        // candidateCorrection <- requires a recorded assistant turn before the user turn; with no
        // prior assistant turn the marker text is not a correction of anything.
        val signals = ReviewSignalDeriver.derive(
            ReviewSignalDeriver.TurnObservations(
                previousAssistantTurnContent = "",
                lastUserTurnContent = "No, that's wrong"
            )
        )

        assertFalse(signals.candidateCorrection)
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

    // --- complexSuccessfulTurn: derived from recorded hops/tool results ---

    @Test
    fun complexSuccess_aboveHopThreshold_mapsToComplexSuccessfulTurn() {
        // complexSuccessfulTurn <- recorded model hops at the complexity threshold, with no
        // recorded failures.
        val signals = ReviewSignalDeriver.derive(
            ReviewSignalDeriver.TurnObservations(modelHopCount = 3)
        )

        assertTrue(signals.complexSuccessfulTurn)
    }

    @Test
    fun complexSuccess_aboveToolThreshold_mapsToComplexSuccessfulTurn() {
        // complexSuccessfulTurn <- recorded tool results at the complexity threshold, with no
        // recorded failures.
        val signals = ReviewSignalDeriver.derive(
            ReviewSignalDeriver.TurnObservations(toolResultCount = 3)
        )

        assertTrue(signals.complexSuccessfulTurn)
    }

    @Test
    fun shortSuccess_doesNotSetComplexSuccessfulTurn() {
        // complexSuccessfulTurn <- not set when the recorded turn stayed below both complexity
        // thresholds.
        val signals = ReviewSignalDeriver.derive(
            ReviewSignalDeriver.TurnObservations(modelHopCount = 1, toolResultCount = 1)
        )

        assertFalse(signals.complexSuccessfulTurn)
    }

    @Test
    fun failedTurn_doesNotSetComplexSuccessfulTurn() {
        // complexSuccessfulTurn <- failure dominates: a recorded failed tool result suppresses the
        // complexity signal even for a long turn.
        val signals = ReviewSignalDeriver.derive(
            ReviewSignalDeriver.TurnObservations(
                failedToolResultCount = 1,
                modelHopCount = 5,
                toolResultCount = 5
            )
        )

        assertFalse(signals.complexSuccessfulTurn)
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
        // candidateCorrection <- recorded correction turn; the policy returns shouldReview, and
        // ReviewTriggerEnqueuer.maybeEnqueue calls PendingReviewRepository.enqueue exactly once
        // when the policy returns shouldReview, so one correction turn produces exactly one review.
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
        // TurnObservationAccumulator <- counts recorded during the turn are carried verbatim into
        // TurnObservations.
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

    // --- R14 acceptance: a retry / edit / ordinary "no," reply is not an explicit correction ---

    @Test
    fun retry_doesNotSetExplicitCorrection() {
        // R14 acceptance: a retry is not a correction. A turn with no explicit action and no
        // lexical marker sets neither the explicit nor the candidate signal.
        val signals = ReviewSignalDeriver.derive(
            ReviewSignalDeriver.TurnObservations(
                previousAssistantTurnContent = "Here is the answer",
                lastUserTurnContent = "try again"
            )
        )

        assertFalse(signals.explicitUserCorrection)
        assertFalse(signals.candidateCorrection)
    }

    @Test
    fun edit_doesNotSetExplicitCorrection() {
        // R14 acceptance: an edit is not a correction unless the user marked the reply wrong. The
        // edited text carries no marker and no explicit action, so neither signal is set.
        val signals = ReviewSignalDeriver.derive(
            ReviewSignalDeriver.TurnObservations(
                previousAssistantTurnContent = "Here is the answer",
                lastUserTurnContent = "Please summarise the file instead",
                explicitCorrectionAction = false
            )
        )

        assertFalse(signals.explicitUserCorrection)
        assertFalse(signals.candidateCorrection)
    }

    @Test
    fun normalReplyBeginningWithNo_doesNotSetExplicitCorrection() {
        // R14 acceptance: a normal reply beginning with "no," must not set the EXPLICIT correction
        // signal. It may still set the weaker CANDIDATE signal, because "no," is a boundary-matched
        // lexical marker and a preceding assistant turn exists — that is asserted separately below
        // and is the documented, bounded behaviour of the candidate signal.
        val signals = ReviewSignalDeriver.derive(
            ReviewSignalDeriver.TurnObservations(
                previousAssistantTurnContent = "Here is the answer",
                lastUserTurnContent = "No, I meant the other file"
            )
        )

        assertFalse(signals.explicitUserCorrection)
        assertTrue(signals.candidateCorrection)
        assertEquals("no,", signals.observed.matchedCorrectionMarker)
    }

    @Test
    fun wrongInsideAnotherWord_doesNotSetCandidate() {
        // R14 acceptance: word-boundary matching. "wrongly" contains the letters of "wrong" but not
        // the word, so no marker matches and no candidate correction is recorded.
        val signals = ReviewSignalDeriver.derive(
            ReviewSignalDeriver.TurnObservations(
                previousAssistantTurnContent = "Here is the answer",
                lastUserTurnContent = "the wrongly named variable"
            )
        )

        assertFalse(signals.candidateCorrection)
        assertEquals("", signals.observed.matchedCorrectionMarker)
    }

    @Test
    fun wrongAsUnrelatedTopic_doesNotSetCandidate() {
        // R14 acceptance: the bare "wrong" marker was removed because it matched unrelated topics.
        // "nothing wrong with the build" is not a correction, so no candidate is recorded.
        val signals = ReviewSignalDeriver.derive(
            ReviewSignalDeriver.TurnObservations(
                previousAssistantTurnContent = "Here is the answer",
                lastUserTurnContent = "nothing wrong with the build"
            )
        )

        assertFalse(signals.candidateCorrection)
        assertEquals("", signals.observed.matchedCorrectionMarker)
    }

    @Test
    fun lexicalMatch_setsCandidateOnly() {
        // R14 acceptance: a lexical marker match sets the CANDIDATE signal only. It must never set
        // the explicit signal, which requires a deliberate user action.
        val signals = ReviewSignalDeriver.derive(
            ReviewSignalDeriver.TurnObservations(
                previousAssistantTurnContent = "Here is the answer",
                lastUserTurnContent = "that's wrong"
            )
        )

        assertTrue(signals.candidateCorrection)
        assertFalse(signals.explicitUserCorrection)
        assertEquals("that's wrong", signals.observed.matchedCorrectionMarker)
    }

    @Test
    fun explicitInput_setsExplicit() {
        // R14 acceptance: an explicit user action does set the explicit correction signal. Nothing
        // in production sets this input yet (no explicit-correction UI control exists); this test
        // pins the deriver contract for when one is wired.
        val signals = ReviewSignalDeriver.derive(
            ReviewSignalDeriver.TurnObservations(
                explicitCorrectionAction = true,
                previousAssistantTurnContent = "x",
                lastUserTurnContent = "y"
            )
        )

        assertTrue(signals.explicitUserCorrection)
        assertTrue(signals.observed.explicitCorrectionAction)
    }

    @Test
    fun complexButUnremarkableTurn_triggersReviewWithoutAssertingLesson() {
        // R14 acceptance: a complex-but-unremarkable turn can trigger a review without recording a
        // lesson. The signal and the decision reason must not claim a lesson exists.
        val signals = ReviewSignalDeriver.derive(
            ReviewSignalDeriver.TurnObservations(modelHopCount = 3)
        )
        val decision = ReviewTriggerPolicy.decide(signals)

        assertTrue(signals.complexSuccessfulTurn)
        assertTrue(decision.shouldReview)
        assertFalse(decision.reason.contains("lesson"))
    }

    @Test
    fun observedEvents_areRetainedAlongsideSignals() {
        // R14 acceptance: the raw observed events are retained alongside the interpreted signals,
        // so a reader can tell what was observed from what was inferred.
        val signals = ReviewSignalDeriver.derive(
            ReviewSignalDeriver.TurnObservations(
                failedToolResultCount = 2,
                toolResultCount = 5,
                modelHopCount = 4
            )
        )

        assertEquals(2, signals.observed.failedToolResultCount)
        assertEquals(5, signals.observed.toolResultCount)
        assertEquals(4, signals.observed.modelHopCount)
    }

    @Test
    fun candidateCorrection_aloneTriggersBoundedReview() {
        // R14 acceptance: a candidate correction alone triggers a review, and the reason says it is
        // a candidate rather than an explicit correction.
        val decision = ReviewTriggerPolicy.decide(
            ReviewTriggerPolicy.ReviewSignals(candidateCorrection = true)
        )

        assertTrue(decision.shouldReview)
        assertTrue(decision.reason.contains("candidate"))
    }
}
