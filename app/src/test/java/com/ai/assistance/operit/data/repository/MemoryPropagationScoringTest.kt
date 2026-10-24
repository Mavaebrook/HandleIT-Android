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

    @Test
    fun addContribution_nonPositiveEdges_doNotCreateCandidatesAtZeroThreshold() {
        val scores = mutableMapOf(1L to 2.0)
        val scoreParts = mutableMapOf<Long, Double>()

        listOf(2L to 0.0f, 3L to -0.5f).forEach { (targetId, edgeWeight) ->
            val contribution = MemoryPropagationScoring.propagatedScore(2.0, edgeWeight, 1.0)
            MemoryPropagationScoring.addContribution(scores, targetId, contribution) {
                scoreParts[targetId] = it
            }
        }

        // A zero threshold would admit accidental 0.0 map entries.
        assertEquals(setOf(1L), scores.filterValues { it >= 0.0 }.keys)
        assertTrue(scoreParts.isEmpty())
    }

    @Test
    fun addContribution_zeroSourceOrGlobalWeight_doesNotCreateCandidates() {
        val scores = mutableMapOf<Long, Double>()
        var scorePartUpdates = 0
        listOf(0.0 to 1.0, 2.0 to 0.0).forEach { (sourceScore, globalWeight) ->
            val contribution = MemoryPropagationScoring.propagatedScore(sourceScore, 0.5f, globalWeight)
            MemoryPropagationScoring.addContribution(scores, 2L, contribution) { scorePartUpdates++ }
        }
        assertTrue(scores.isEmpty())
        assertEquals(0, scorePartUpdates)
    }

    @Test
    fun addContribution_nonPositiveContribution_preservesIndependentRetrievalScore() {
        val scores = mutableMapOf(2L to 0.25)
        var scorePartUpdates = 0
        listOf(0.0, -0.5).forEach { contribution ->
            MemoryPropagationScoring.addContribution(scores, 2L, contribution) { scorePartUpdates++ }
        }
        assertEquals(0.25, scores.getValue(2L), 1e-9)
        assertEquals(0, scorePartUpdates)
    }

    @Test
    fun addContribution_positiveEdges_accumulateCandidateAndScoreParts() {
        val scores = mutableMapOf(2L to 0.25)
        val scoreParts = mutableMapOf<Long, Double>()
        // Existing and previously absent targets both receive repeated positive contributions.
        listOf(2L, 3L, 2L, 3L).forEach { targetId ->
            val contribution = MemoryPropagationScoring.propagatedScore(2.0, 0.5f, 1.0)
            MemoryPropagationScoring.addContribution(scores, targetId, contribution) {
                scoreParts[targetId] = scoreParts.getOrDefault(targetId, 0.0) + it
            }
        }
        assertEquals(2.25, scores.getValue(2L), 1e-9)
        assertEquals(2.0, scores.getValue(3L), 1e-9)
        assertEquals(mapOf(2L to 2.0, 3L to 2.0), scoreParts)
        assertEquals(setOf(2L, 3L), scores.filterValues { it >= 0.0 }.keys)
    }
}
