package com.ai.assistance.operit.data.repository

/**
 * Scoring and candidate admission for memory graph propagation.
 *
 * Graph expansion propagates a source memory's score to its neighbours across edges. A zero-weight (or otherwise
 * non-positive) link is semantically meaningless and must contribute no score at all, so such
 * neighbours are never boosted into results.
 */
object MemoryPropagationScoring {

    /**
     * Add a graph contribution without creating zero-only retrieval candidates.
     *
     * Both link directions use this gate. The callback updates score parts only when the
     * candidate score changes; existing scores from other retrieval signals are preserved.
     */
    internal fun addContribution(
        scores: MutableMap<Long, Double>,
        targetId: Long,
        contribution: Double,
        onContribution: (Double) -> Unit
    ) {
        if (contribution > 0.0) {
            scores[targetId] = scores.getOrDefault(targetId, 0.0) + contribution
            onContribution(contribution)
        }
    }

    /**
     * Score propagated from a source memory to a neighbour across one graph edge.
     *
     * A zero-weight (or non-positive) link contributes
     * no score, so semantically meaningless neighbours are not boosted into results.
     *
     * @param sourceScore the current score of the source memory.
     * @param edgeWeight the weight of the traversed link.
     * @param graphPropagationWeight the global propagation multiplier (effectiveEdgeWeight).
     * @return the propagated score; 0.0 when edgeWeight <= 0.0.
     */
    fun propagatedScore(sourceScore: Double, edgeWeight: Float, graphPropagationWeight: Double): Double {
        if (edgeWeight <= 0.0f) return 0.0
        return sourceScore * edgeWeight * graphPropagationWeight
    }
}
