package com.ai.assistance.operit.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM regression and measurement tests for [GraphPropagationBatching] (D04b).
 *
 * The query-count plan and the contribution engine are pure Kotlin, so the round-trip
 * reduction and the exact parity with the legacy per-seed/per-link loop are asserted
 * deterministically without an ObjectBox store.
 */
class GraphPropagationBatchingTest {

    // --- D04b measurement: query round-trip plan ---

    @Test
    fun planQueryCounts_multiSeed_batchedPlanIsStrictlyCheaper() {
        val (legacy, batched) = GraphPropagationBatching.planQueryCounts(
            seedCount = 10,
            linkRowsConsidered = 40
        )
        assertEquals(60, legacy)
        assertEquals(3, batched)
        assertTrue(batched < legacy)
    }

    @Test
    fun planQueryCounts_singleSeedWithLinks_batchedPlanIsCheaper() {
        val (legacy, batched) = GraphPropagationBatching.planQueryCounts(
            seedCount = 1,
            linkRowsConsidered = 2
        )
        assertEquals(4, legacy)
        assertEquals(3, batched)
    }

    @Test
    fun planQueryCounts_singleSeedWithoutLinks_batchedPlanCostsOneExtraRoundTrip() {
        // Documented break-even: only this degenerate case is more expensive, by exactly one query.
        val (legacy, batched) = GraphPropagationBatching.planQueryCounts(
            seedCount = 1,
            linkRowsConsidered = 0
        )
        assertEquals(2, legacy)
        assertEquals(3, batched)
    }

    @Test
    fun planQueryCounts_noSeeds_noQueries() {
        val (legacy, batched) = GraphPropagationBatching.planQueryCounts(
            seedCount = 0,
            linkRowsConsidered = 0
        )
        assertEquals(0, legacy)
        assertEquals(0, batched)
    }

    // --- parity with the legacy per-seed/per-link loop ---

    @Test
    fun propagate_batchedRows_matchLegacyLoopExactly() {
        val seeds = listOf(1L, 2L)
        val allowed = setOf(1L, 2L, 3L, 4L, 5L) // memory 6 exists but is out of scope
        val existing = setOf(1L, 2L, 3L, 4L, 5L, 6L) // link to 7 is dangling
        val outgoing = listOf(
            GraphPropagationBatching.LinkRow(1, 1, 3, 0.5f),
            GraphPropagationBatching.LinkRow(2, 1, 4, 0.2f),
            GraphPropagationBatching.LinkRow(3, 2, 5, 0.8f),
            GraphPropagationBatching.LinkRow(4, 2, 6, 0.6f), // out of scope
            GraphPropagationBatching.LinkRow(5, 2, 7, 0.4f)  // dangling
        )
        val incoming = listOf(
            GraphPropagationBatching.LinkRow(6, 3, 1, 0.3f),
            GraphPropagationBatching.LinkRow(7, 4, 2, -0.5f) // non-positive contribution
        )

        val legacyScores = referenceLegacyPropagation(
            seeds, allowed, existing, outgoing, incoming, graphWeight = 2.0
        )
        val batchedScores = mutableMapOf(1L to 10.0, 2L to 20.0)
        val scoreParts = mutableMapOf<Long, Double>()
        val stats = GraphPropagationBatching.propagate(
            seeds = seeds,
            outgoingLinks = outgoing,
            incomingLinks = incoming,
            scores = batchedScores,
            allowedScopedIds = allowed,
            existingEndpointIds = existing,
            graphPropagationWeight = 2.0
        ) { targetId, contribution ->
            scoreParts[targetId] = scoreParts.getOrDefault(targetId, 0.0) + contribution
        }

        assertEquals(legacyScores, batchedScores)
        // Positive contributions land on the traversed neighbours: outgoing targets 3/4/5 and
        // the incoming link's source (memory 3), so memory 1 receives no edge score part.
        assertEquals(setOf(3L, 4L, 5L), scoreParts.keys)
        assertEquals(2, stats.seedsProcessed)
        assertEquals(7, stats.linkRowsConsidered)
        assertEquals(5, stats.edgesTraversed)
        assertEquals(1, stats.skippedDangling)
        assertEquals(1, stats.skippedOutOfScope)
        assertEquals(1, stats.skippedNonPositive)
        assertEquals(11, stats.legacyQueryCount)
        assertEquals(3, stats.batchedQueryCount)
        assertEquals(8, stats.queryRoundTripsSaved)
    }

    @Test
    fun propagate_seedOutOfScope_isSkippedLikeLegacy() {
        val seeds = listOf(1L, 2L)
        val allowed = setOf(2L, 3L)
        val existing = setOf(2L, 3L)
        val outgoing = listOf(
            GraphPropagationBatching.LinkRow(1, 1, 3, 0.5f), // seed 1 is out of scope -> skipped entirely
            GraphPropagationBatching.LinkRow(2, 2, 3, 0.5f)
        )
        val scores = mutableMapOf(1L to 5.0, 2L to 10.0)

        val stats = GraphPropagationBatching.propagate(
            seeds = seeds,
            outgoingLinks = outgoing,
            incomingLinks = emptyList(),
            scores = scores,
            allowedScopedIds = allowed,
            existingEndpointIds = existing,
            graphPropagationWeight = 1.0
        ) { _, _ -> }

        assertEquals(1, stats.seedsProcessed)
        assertEquals(1, stats.linkRowsConsidered)
        assertEquals(1, stats.edgesTraversed)
        assertEquals(setOf(1L, 2L, 3L), scores.keys)
        // Seed 2 (score 10) -> memory 3 across weight 0.5 with graph weight 1.0 = contribution 5.
        assertEquals(5.0, scores.getValue(3L), 1e-9)
    }

    @Test
    fun propagate_zeroAndNegativeWeights_contributeNoScoreParts() {
        val seeds = listOf(1L)
        val allowed = setOf(1L, 2L, 3L)
        val outgoing = listOf(
            GraphPropagationBatching.LinkRow(1, 1, 2, 0.0f),
            GraphPropagationBatching.LinkRow(2, 1, 3, -1.0f)
        )
        val scores = mutableMapOf(1L to 10.0)
        var parts = 0

        val stats = GraphPropagationBatching.propagate(
            seeds = seeds,
            outgoingLinks = outgoing,
            incomingLinks = emptyList(),
            scores = scores,
            allowedScopedIds = allowed,
            existingEndpointIds = allowed,
            graphPropagationWeight = 1.0
        ) { _, _ -> parts++ }

        assertEquals(2, stats.edgesTraversed)
        assertEquals(2, stats.skippedNonPositive)
        assertEquals(0, parts)
        assertEquals(setOf(1L), scores.keys)
    }

    /** Reference of the pre-batching loop semantics: per seed, outgoing then incoming rows by id. */
    private fun referenceLegacyPropagation(
        seeds: List<Long>,
        allowed: Set<Long>,
        existing: Set<Long>,
        outgoing: List<GraphPropagationBatching.LinkRow>,
        incoming: List<GraphPropagationBatching.LinkRow>,
        graphWeight: Double
    ): MutableMap<Long, Double> {
        val scores = mutableMapOf(1L to 10.0, 2L to 20.0)
        val outgoingBySource = outgoing.groupBy { it.sourceId }
        val incomingByTarget = incoming.groupBy { it.targetId }
        for (seed in seeds) {
            if (seed !in allowed) continue
            val sourceScore = scores[seed] ?: 0.0
            for (row in outgoingBySource[seed].orEmpty().sortedBy { it.id }) {
                val targetId = row.targetId
                if (targetId !in existing) continue
                if (targetId !in allowed) continue
                val contribution = MemoryPropagationScoring.propagatedScore(sourceScore, row.weight, graphWeight)
                MemoryPropagationScoring.addContribution(scores, targetId, contribution) {}
            }
            for (row in incomingByTarget[seed].orEmpty().sortedBy { it.id }) {
                val targetId = row.sourceId
                if (targetId !in existing) continue
                if (targetId !in allowed) continue
                val contribution = MemoryPropagationScoring.propagatedScore(sourceScore, row.weight, graphWeight)
                MemoryPropagationScoring.addContribution(scores, targetId, contribution) {}
            }
        }
        return scores
    }
}