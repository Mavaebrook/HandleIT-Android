package com.ai.assistance.operit.data.repository

/**
 * Pure scoring helper for memory graph score propagation.
 *
 * Graph expansion propagates a source memory's score to its neighbours across edges. The edge
 * weight is the sole determinant of how much score crosses a link: a zero-weight (or otherwise
 * non-positive) link is semantically meaningless and must contribute no score at all, so such
 * neighbours are never boosted into results.
 */
object MemoryPropagationScoring {

    /**
     * Score propagated from a source memory to a neighbour across one graph edge.
     *
     * Edge weight is the sole determinant: a zero-weight (or non-positive) link contributes
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
