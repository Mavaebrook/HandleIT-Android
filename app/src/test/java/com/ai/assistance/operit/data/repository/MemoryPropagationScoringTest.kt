package com.ai.assistance.operit.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM regression tests for [MemoryPropagationScoring].
 *
 * These cover the D04/R10 acceptance criterion: a zero-weight link contributes no score.
 * Edge weight is the sole determinant of propagation, so semantically meaningless
 * (zero/negative-weight) neighbours are never boosted into results.
 */
class MemoryPropagationScoringTest {

    // Covers: R10 acceptance — a zero-weight link contributes NO score, even with a high
    // source score and a positive global propagation weight.
    @Test
    fun propagatedScore_zeroWeightLink_contributesNoScore() {
        val actual = MemoryPropagationScoring.propagatedScore(
            sourceScore = 100.0,
            edgeWeight = 0.0f,
            graphPropagationWeight = 1.0
        )

        assertEquals(0.0, actual, 1e-9)
    }

    // Covers: a negative-weight link contributes no score (non-positive weights are meaningless).
    @Test
    fun propagatedScore_negativeWeightLink_contributesNoScore() {
        val actual = MemoryPropagationScoring.propagatedScore(
            sourceScore = 100.0,
            edgeWeight = -0.5f,
            graphPropagationWeight = 1.0
        )

        assertEquals(0.0, actual, 1e-9)
    }

    // Covers: positive weight scales exactly as sourceScore * weight * graphPropagationWeight.
    @Test
    fun propagatedScore_positiveWeight_scalesWithSourceWeightAndGlobalWeight() {
        val actual = MemoryPropagationScoring.propagatedScore(
            sourceScore = 2.0,
            edgeWeight = 0.5f,
            graphPropagationWeight = 3.0
        )

        assertEquals(3.0, actual, 1e-9)
    }

    // Covers: edge weight is the determinant — a higher weight yields a strictly higher score
    // for the same source score and global propagation weight.
    @Test
    fun propagatedScore_higherWeight_yieldsStrictlyHigherScore() {
        val low = MemoryPropagationScoring.propagatedScore(
            sourceScore = 10.0,
            edgeWeight = 0.2f,
            graphPropagationWeight = 1.0
        )
        val high = MemoryPropagationScoring.propagatedScore(
            sourceScore = 10.0,
            edgeWeight = 0.8f,
            graphPropagationWeight = 1.0
        )

        assertTrue("expected $high > $low", high > low)
    }

    // Covers: a zero source score yields zero propagated score regardless of edge weight.
    @Test
    fun propagatedScore_zeroSourceScore_yieldsZero() {
        val actual = MemoryPropagationScoring.propagatedScore(
            sourceScore = 0.0,
            edgeWeight = 0.9f,
            graphPropagationWeight = 1.0
        )

        assertEquals(0.0, actual, 1e-9)
    }
}
