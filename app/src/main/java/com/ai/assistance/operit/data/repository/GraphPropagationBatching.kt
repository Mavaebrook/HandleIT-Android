package com.ai.assistance.operit.data.repository

/**
 * D04b — batched query planning and contribution computation for memory graph propagation.
 *
 * Legacy graph expansion resolved every seed's outgoing and incoming relation separately and
 * then resolved each traversed link's endpoint with a single-entity lookup: for S seeds and
 * R considered link rows that is 2*S + R query round trips. ObjectBox 5.3.0 relation queries
 * (`QueryBuilder.in(RelationInfo, ids)`) load all seeds' links with two box queries, and the
 * endpoints are verified with one batch `get` — a constant 3 round trips. No schema change is
 * required: batching reads the existing relation-id properties only.
 *
 * This object is pure Kotlin so the query-count plan and the contribution parity with the
 * legacy loop are verifiable in deterministic JVM tests; native-store parity is covered by
 * [GraphPropagationBatchingNativeTest] and runs in hosted JVM CI.
 */
object GraphPropagationBatching {

    /** A MemoryLink row loaded from the link box without resolving any ToOne endpoint. */
    data class LinkRow(
        val id: Long,
        val sourceId: Long,
        val targetId: Long,
        val weight: Float
    )

    /** Counters for one propagation run; reported on-device and asserted in JVM tests. */
    data class Stats(
        val seedsProcessed: Int = 0,
        val linkRowsConsidered: Int = 0,
        val edgesTraversed: Int = 0,
        val skippedDangling: Int = 0,
        val skippedOutOfScope: Int = 0,
        val skippedNonPositive: Int = 0,
        val legacyQueryCount: Int = 0,
        val batchedQueryCount: Int = 0
    ) {
        /** Round trips the legacy loop would need on identical data, minus the batched plan. */
        val queryRoundTripsSaved: Int get() = legacyQueryCount - batchedQueryCount
    }

    /**
     * Query round-trip plan for S seeds and R considered link rows, on identical data.
     *
     * Legacy: two relation resolutions per seed (outgoing and incoming ToMany) plus one
     * single-entity endpoint lookup per considered row = 2*S + R.
     * Batched: two link-box relation queries plus one batch endpoint get = 3 (0 when no seed
     * is processed).
     *
     * With S >= 2 the batched plan never issues more queries. With S == 1 it wins once any
     * link row is considered and costs exactly one extra round trip only for a single seed
     * with no links at all.
     */
    fun planQueryCounts(seedCount: Int, linkRowsConsidered: Int): Pair<Int, Int> {
        val legacy = 2 * seedCount + linkRowsConsidered
        val batched = if (seedCount > 0) 3 else 0
        return legacy to batched
    }

    /**
     * Compute graph contributions from batched link rows with the legacy semantics and
     * iteration order: seeds in the given order, then each seed's outgoing rows and incoming
     * rows, both ordered by link id ascending (ObjectBox ToMany default order).
     *
     * @param seeds seed memory ids in expansion order; seeds outside [allowedScopedIds] are
     * skipped exactly like the legacy per-seed in-scope lookup.
     * @param outgoingLinks all outgoing rows of the seeds, typically one box query result
     * @param incomingLinks all incoming rows of the seeds, typically one box query result
     * @param scores the shared score map; positive contributions are added to it
     * @param allowedScopedIds ids of memories that are in scope AND active for this query
     * @param existingEndpointIds endpoint ids verified with one batch get; rows pointing at
     * missing ids are dangling and skipped like the legacy null-target check
     * @param graphPropagationWeight the global propagation multiplier
     * @param onContribution score-part callback invoked for every positive contribution
     */
    fun propagate(
        seeds: List<Long>,
        outgoingLinks: List<LinkRow>,
        incomingLinks: List<LinkRow>,
        scores: MutableMap<Long, Double>,
        allowedScopedIds: Set<Long>,
        existingEndpointIds: Set<Long>,
        graphPropagationWeight: Double,
        onContribution: (targetId: Long, contribution: Double) -> Unit
    ): Stats {
        val seedSet = seeds.toHashSet()
        val outgoingBySource = outgoingLinks
            .filter { it.sourceId in seedSet }
            .groupBy { it.sourceId }
            .mapValues { (_, rows) -> rows.sortedBy { it.id } }
        val incomingByTarget = incomingLinks
            .filter { it.targetId in seedSet }
            .groupBy { it.targetId }
            .mapValues { (_, rows) -> rows.sortedBy { it.id } }

        var seedsProcessed = 0
        var linkRowsConsidered = 0
        var edgesTraversed = 0
        var skippedDangling = 0
        var skippedOutOfScope = 0
        var skippedNonPositive = 0
        for (seed in seeds) {
            if (seed !in allowedScopedIds) continue
            seedsProcessed++
            val sourceScore = scores[seed] ?: 0.0
            val outgoing = outgoingBySource[seed].orEmpty()
            val incoming = incomingByTarget[seed].orEmpty()
            linkRowsConsidered += outgoing.size + incoming.size
            for (row in outgoing) {
                val targetId = row.targetId
                if (targetId !in existingEndpointIds) { skippedDangling++; continue }
                if (!MemoryGraphScopeFilter.shouldPropagateTo(allowedScopedIds, targetId)) { skippedOutOfScope++; continue }
                edgesTraversed++
                val contribution = MemoryPropagationScoring.propagatedScore(sourceScore, row.weight, graphPropagationWeight)
                if (contribution <= 0.0) skippedNonPositive++
                MemoryPropagationScoring.addContribution(scores, targetId, contribution) {
                    onContribution(targetId, it)
                }
            }
            for (row in incoming) {
                val targetId = row.sourceId
                if (targetId !in existingEndpointIds) { skippedDangling++; continue }
                if (!MemoryGraphScopeFilter.shouldPropagateTo(allowedScopedIds, targetId)) { skippedOutOfScope++; continue }
                edgesTraversed++
                val contribution = MemoryPropagationScoring.propagatedScore(sourceScore, row.weight, graphPropagationWeight)
                if (contribution <= 0.0) skippedNonPositive++
                MemoryPropagationScoring.addContribution(scores, targetId, contribution) {
                    onContribution(targetId, it)
                }
            }
        }

        val (legacyQueries, batchedQueries) = planQueryCounts(seedsProcessed, linkRowsConsidered)
        return Stats(
            seedsProcessed = seedsProcessed,
            linkRowsConsidered = linkRowsConsidered,
            edgesTraversed = edgesTraversed,
            skippedDangling = skippedDangling,
            skippedOutOfScope = skippedOutOfScope,
            skippedNonPositive = skippedNonPositive,
            legacyQueryCount = legacyQueries,
            batchedQueryCount = batchedQueries
        )
    }
}