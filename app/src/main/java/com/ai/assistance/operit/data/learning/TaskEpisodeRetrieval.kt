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

    /**
     * English function words that carry no task-specific meaning.
     *
     * Rationale (C02): raw token overlap counts words such as "the", "of" or "for" as evidence of
     * similarity. Two unrelated sentences that merely share "the app" would then look related, and
     * a conditional prerequisite would be declared applicable on the strength of one shared common
     * word. Removing these tokens before matching is what makes the applicability rule in
     * [isPrerequisiteApplicable] and the relevance score in [relevanceScore] depend on CONTENT
     * words only.
     *
     * The list is a fixed, documented constant: it is not learned, not configurable at runtime and
     * not extended by any fallback path.
     */
    private val ENGLISH_STOPWORDS: Set<String> = setOf(
        "a", "an", "the", "and", "or", "but", "if", "then", "else", "of", "to", "in", "on", "at",
        "by", "for", "with", "from", "as", "is", "are", "was", "were", "be", "been", "being", "it",
        "its", "this", "that", "these", "those", "i", "you", "we", "they", "he", "she", "my",
        "your", "our", "their", "do", "does", "did", "done", "have", "has", "had", "will", "would",
        "can", "could", "should", "shall", "may", "might", "must", "not", "no", "so", "such", "than",
        "too", "very", "just", "also", "into", "onto", "over", "under", "about", "after", "before",
        "when", "while", "where", "which", "who", "whom", "what", "how", "all", "any", "both",
        "each", "few", "more", "most", "other", "some", "only", "own", "same", "up", "down", "out",
        "off", "again", "further", "once", "here", "there", "why", "s", "t", "re", "ve", "ll", "d",
        "m"
    )

    /**
     * Chinese function words and particles that carry no task-specific meaning.
     *
     * Rationale (C02): the same as [ENGLISH_STOPWORDS] — a shared "的" or "在" is not evidence that
     * a prerequisite condition applies to the present task.
     *
     * NOTE ON SINGLE-CHARACTER ENTRIES: unlike English, Chinese is written without spaces, so a
     * "word" is often one character (的, 了, 和, 在, 就, 也, ...). Single-character stopwords are
     * therefore meaningful and necessary here; they are not an accident of the list. Multi-character
     * entries (我们, 这个, 因为, 可以, ...) are included as well so that both whole-word and
     * character-level tokenization (see C03) remove the same function words.
     *
     * The list is a fixed, documented constant: it is not learned, not configurable at runtime and
     * not extended by any fallback path.
     */
    private val CHINESE_STOPWORDS: Set<String> = setOf(
        "的", "了", "和", "与", "及", "或", "是", "在", "有", "我", "你", "他", "她", "它",
        "我们", "你们", "他们", "这", "那", "这个", "那个", "这些", "那些", "一个", "一些",
        "什么", "怎么", "如何", "为什么", "因为", "所以", "但是", "而且", "如果", "就", "也",
        "都", "还", "又", "再", "很", "太", "更", "最", "会", "能", "可以", "要", "需要", "请",
        "把", "被", "给", "对", "从", "到", "向", "于", "为", "以", "之", "其", "并", "而", "则",
        "着", "过", "吧", "呢", "啊", "吗", "呀", "哦", "嗯", "上", "下", "里", "中", "后", "前",
        "时", "时候", "现在", "已经", "没有", "不", "没", "别", "让", "使", "得", "地", "所",
        "等", "等等", "各", "每", "某", "该", "此", "本"
    )

    /**
     * Minimum number of DISTINCT meaningful tokens that a prerequisite condition and the current
     * task context must share before the condition is considered applicable.
     *
     * Rationale (C02): one shared content word is not evidence of applicability. "app", "update",
     * "install" and "apk" appear in almost every Android task, so a single shared token would make
     * nearly every conditional prerequisite apply to nearly every task — which is exactly the
     * behaviour C02 removes. Two distinct shared content words is the smallest overlap that can
     * express a real topical match.
     */
    private const val MIN_DISTINCT_MATCHED_TOKENS = 2

    /**
     * Minimum fraction of the condition's meaningful tokens that must be matched by the context.
     *
     * Rationale (C02): the distinct-token floor alone is not enough — a long condition that shares
     * two incidental words with the context is still a different condition. Requiring at least half
     * of the condition's content words to be present keeps applicability tied to the condition as a
     * whole rather than to a couple of common words inside it.
     */
    private const val MIN_MATCHED_FRACTION = 0.5

    /** Clamp a requested top-k into the bounded [1, MAX_TOP_K] range. */
    fun clampTopK(requested: Int): Int {
        if (requested <= 0) return DEFAULT_TOP_K
        return requested.coerceAtMost(MAX_TOP_K)
    }

    /**
     * Rank episodes by simple token-overlap relevance to the query, returning at most
     * clampTopK(topK) episodes. Deterministic: ties broken by higher createdAtMs then id.
     *
     * C02: both the query and each episode are reduced to MEANINGFUL tokens (see
     * [meaningfulTokens]) before scoring, so stopwords cannot create relevance on their own.
     */
    fun rankRelevantEpisodes(query: String, episodes: List<TaskEpisode>, topK: Int = DEFAULT_TOP_K): List<TaskEpisode> {
        val queryTokens = meaningfulTokens(query)
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
     *
     * Rule (C02, single rule, no fallback chain):
     *  1. Both sides are reduced to MEANINGFUL tokens ([meaningfulTokens]): stopwords and
     *     numeric-only tokens are dropped.
     *  2. A condition that reduces to NO meaningful tokens (empty, blank, or made only of
     *     stopwords such as "the and of to") is NOT applicable — there is nothing to match on.
     *  3. A context that reduces to no meaningful tokens is NOT applicable either.
     *  4. The condition is applicable only when BOTH thresholds are met:
     *     at least [MIN_DISTINCT_MATCHED_TOKENS] distinct meaningful tokens are shared, AND the
     *     shared count is at least [MIN_MATCHED_FRACTION] of the condition's meaningful tokens.
     *
     * There is deliberately NO loosening fallback: if the thresholds are not met the answer is
     * `false`. A single shared common token such as "app", "install", "apk" or "update" must never
     * make a condition applicable.
     */
    fun isPrerequisiteApplicable(prerequisiteCondition: String, currentTaskContext: String): Boolean {
        val conditionTokens = meaningfulTokens(prerequisiteCondition)
        if (conditionTokens.isEmpty()) return false
        val contextTokens = meaningfulTokens(currentTaskContext)
        if (contextTokens.isEmpty()) return false
        val matched = conditionTokens.count { it in contextTokens }
        return matched >= MIN_DISTINCT_MATCHED_TOKENS &&
            matched.toDouble() / conditionTokens.size.toDouble() >= MIN_MATCHED_FRACTION
    }

    /**
     * Relevance of one episode to an already-tokenized query.
     *
     * C02: the episode text is reduced to MEANINGFUL tokens ([meaningfulTokens]) instead of raw
     * tokens, so stopwords in a summary cannot inflate the score. The formula is unchanged:
     * `overlap / queryTokens.size`, where [queryTokens] is the caller's meaningful query token set.
     * An episode with no meaningful tokens scores 0.0 and is therefore filtered out by
     * [rankRelevantEpisodes].
     */
    private fun relevanceScore(queryTokens: Set<String>, episode: TaskEpisode): Double {
        val episodeTokens = meaningfulTokens(
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

    /**
     * Tokens of [text] that carry task-specific meaning.
     *
     * Starts from [tokenize] (unchanged in C02 — the tokenizer itself is replaced in C03) and then
     * removes:
     *  - tokens listed in [ENGLISH_STOPWORDS] or [CHINESE_STOPWORDS];
     *  - purely numeric tokens (all digits, e.g. "0" from a path such as
     *    "/storage/emulated/0/...").
     *
     * Numeric-only tokens are not meaningful for applicability: a version number, a path segment
     * or a port is not evidence that a conditional prerequisite applies to the present task.
     */
    private fun meaningfulTokens(text: String): Set<String> {
        return tokenize(text).filterNot { token ->
            token in ENGLISH_STOPWORDS ||
                token in CHINESE_STOPWORDS ||
                token.all { it in '0'..'9' }
        }.toSet()
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
