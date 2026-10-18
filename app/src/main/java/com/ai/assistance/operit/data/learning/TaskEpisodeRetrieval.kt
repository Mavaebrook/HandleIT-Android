package com.ai.assistance.operit.data.learning

import com.ai.assistance.operit.data.model.TaskEpisode

/**
 * Pure, bounded similar-task retrieval, independent-evidence deduplication, and
 * missing-prerequisite applicability checks.
 *
 * Bounded by design: top-k is clamped to a small configurable range (initial target 3-5),
 * and no full-history scan or raw-trace analysis is performed here.
 */
object TaskEpisodeRetrieval {

    const val DEFAULT_TOP_K = 3
    const val MAX_TOP_K = 5

    /** Clamp a requested top-k into the bounded [1, MAX_TOP_K] range. */
    fun clampTopK(requested: Int): Int {
        if (requested <= 0) return DEFAULT_TOP_K
        return requested.coerceAtMost(MAX_TOP_K)
    }

    /**
     * Rank episodes by simple token-overlap relevance to the query, returning at most
     * clampTopK(topK) episodes. Deterministic: ties broken by higher createdAtMs then id.
     */
    fun rankRelevantEpisodes(query: String, episodes: List<TaskEpisode>, topK: Int = DEFAULT_TOP_K): List<TaskEpisode> {
        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) return emptyList()
        return episodes
            .map { episode -> episode to relevanceScore(queryTokens, episode) }
            .filter { (_, score) -> score > 0.0 }
            .sortedWith(compareByDescending<Pair<TaskEpisode, Double>> { it.second }
                .thenByDescending { it.first.createdAtMs }
                .thenByDescending { it.first.id })
            .take(clampTopK(topK))
            .map { it.first }
    }

    /**
     * Deduplicate evidence keys so that one incident repeated in summaries counts as ONE
     * evidence source. Paraphrases that normalize to the same key are collapsed.
     */
    fun deduplicateEvidenceKeys(evidenceKeys: List<String>): List<String> {
        val seen = LinkedHashSet<String>()
        evidenceKeys.forEach { raw ->
            val normalized = normalizeEvidenceKey(raw)
            if (normalized.isNotEmpty()) seen.add(normalized)
        }
        return seen.toList()
    }

    /**
     * Count independent evidence sources across a set of episodes (after dedup).
     */
    fun countIndependentEvidence(episodes: List<TaskEpisode>): Int {
        return deduplicateEvidenceKeys(episodes.flatMap { it.evidenceKeys }).size
    }

    /**
     * Given the prerequisites required by a similar past episode and the prerequisites the
     * current task actually satisfies, return the prerequisites that are MISSING (required but
     * not satisfied). Applicability is checked against the present task, not assumed.
     */
    fun missingPrerequisites(required: List<String>, satisfied: List<String>): List<String> {
        val satisfiedKeys = satisfied.map { normalizeEvidenceKey(it) }.toHashSet()
        return required.filter { normalizeEvidenceKey(it) !in satisfiedKeys }
    }

    /**
     * Deterministically decide which of [required] are EVIDENTLY covered by [requestText].
     *
     * No model call is made: this is a pure lexical heuristic over the request text.
     *
     * Rule (single rule, no fallback chain): a required prerequisite is considered evidently
     * satisfied when the request text contains the prerequisite's normalized key as a substring
     * (see [normalizeEvidenceKey]: lowercase, trimmed, internal whitespace collapsed to single
     * spaces). The prerequisite must normalize to a non-empty key; an empty/blank prerequisite is
     * never reported as satisfied.
     *
     * Tokenization is deliberately NOT duplicated here: the tokenizer used by
     * [isPrerequisiteApplicable] is the single source of truth for token-level matching, and the
     * stopword lists / tokenizer introduced by C02 will apply to that helper automatically.
     *
     * Limits: a request that merely MENTIONS a prerequisite is treated as covering it. This is a
     * lexical heuristic and can be wrong (e.g. "we do not have signing continuity" mentions the
     * prerequisite but does not satisfy it). Callers must therefore still frame every remaining
     * item as a caution to verify, never as a verified fact.
     *
     * @return the subset of [required] whose normalized key occurs in the normalized request text,
     *         preserving the input order.
     */
    fun satisfiedPrerequisites(required: List<String>, requestText: String): List<String> {
        val requestKey = normalizeEvidenceKey(requestText)
        if (requestKey.isEmpty()) return emptyList()
        return required.filter { prerequisite ->
            val prerequisiteKey = normalizeEvidenceKey(prerequisite)
            prerequisiteKey.isNotEmpty() && requestKey.contains(prerequisiteKey)
        }
    }

    /**
     * Applicability check for a conditional prerequisite. A prerequisite applies only when its
     * condition matches the current task context. Example: signing continuity applies to an
     * in-place APK update, not automatically to every installation.
     */
    fun isPrerequisiteApplicable(prerequisiteCondition: String, currentTaskContext: String): Boolean {
        val conditionTokens = tokenize(prerequisiteCondition)
        if (conditionTokens.isEmpty()) return false
        val contextTokens = tokenize(currentTaskContext)
        return conditionTokens.any { it in contextTokens }
    }

    private fun relevanceScore(queryTokens: Set<String>, episode: TaskEpisode): Double {
        val episodeTokens = tokenize(
            listOf(
                episode.taskSummary,
                episode.contextSummary,
                episode.failureSummary,
                episode.causeSummary,
                episode.resolutionSummary,
                episode.requiredPrerequisites.joinToString(" "),
                episode.applicableConstraints.joinToString(" ")
            ).joinToString(" ")
        )
        if (episodeTokens.isEmpty()) return 0.0
        val overlap = queryTokens.count { it in episodeTokens }
        return overlap.toDouble() / queryTokens.size.toDouble()
    }

    private fun tokenize(text: String): Set<String> {
        return text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 2 }
            .toSet()
    }

    private fun normalizeEvidenceKey(raw: String): String {
        return raw.lowercase().trim().replace(Regex("\\s+"), " ")
    }
}
