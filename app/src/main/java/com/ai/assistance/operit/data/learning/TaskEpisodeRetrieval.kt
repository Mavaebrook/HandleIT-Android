package com.ai.assistance.operit.data.learning

import com.ai.assistance.operit.data.model.TaskEpisode
import com.ai.assistance.operit.data.model.TaskEpisodeEntity

/**
 * Machine-readable integrity eligibility of a past episode.
 *
 * INTEGRITY_ELIGIBLE means ONLY that the episode's stored list bytes decoded (its integrity is
 * resolved). It does NOT assert that the episode is fully applicable to the present task:
 * task-relative applicability is computed separately by [TaskEpisodeRetrieval.isPrerequisiteApplicable],
 * [TaskEpisodeRetrieval.satisfiedPrerequisites] and [TaskEpisodeRetrieval.missingPrerequisites].
 *
 * INELIGIBLE_UNRESOLVED_INTEGRITY: the episode's requiredPrerequisites or applicableConstraints
 * could not be resolved from storage, so applicability cannot be established. The episode must not
 * be presented as applicable; the formatter emits a fixed caution instead.
 */
enum class EpisodeApplicability { INTEGRITY_ELIGIBLE, INELIGIBLE_UNRESOLVED_INTEGRITY }

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
     * [isPrerequisiteApplicable] and the relevance score in [qualifiedLexicalScore] depend on CONTENT
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

    /**
     * CJK Unified Ideographs (U+4E00..U+9FFF): the main Han ideograph block used by Chinese,
     * Japanese kanji and Korean hanja.
     */
    private val CJK_UNIFIED_IDEOGRAPHS = '\u4E00'..'\u9FFF'

    /**
     * CJK Unified Ideographs Extension A (U+3400..U+4DBF): rarer Han ideographs.
     */
    private val CJK_EXTENSION_A = '\u3400'..'\u4DBF'

    /** Hiragana (U+3040..U+309F): Japanese syllabary. */
    private val HIRAGANA = '\u3040'..'\u309F'

    /** Katakana (U+30A0..U+30FF): Japanese syllabary. */
    private val KATAKANA = '\u30A0'..'\u30FF'

    /** Hangul Syllables (U+AC00..U+D7AF): Korean precomposed syllables. */
    private val HANGUL_SYLLABLES = '\uAC00'..'\uD7AF'

    /**
     * Minimum number of CONSECUTIVE meaningful CJK bigrams from the condition that must appear as a
     * contiguous run in the context for the condition to be applicable.
     *
     * Rationale: CJK conditions are conjunctions of clauses (e.g. "就地更新安装包" + "时的签名连续性").
     * A set-overlap fraction punishes a condition for containing a clause the context does not
     * mention, even when another clause matches perfectly. Requiring a contiguous run of at least
     * this many bigrams instead asks "does the context contain a real phrase from the condition",
     * which is the question applicability is actually trying to answer.
     *
     * WHY 3 AND NOT 2: the tokenizer emits OVERLAPPING bigrams, so k consecutive bigrams correspond
     * to a k+1 character phrase. With k = 2 the required phrase is only 3 characters long, which is
     * short enough to be shared by unrelated clauses: the condition "就地更新安装包时的签名连续性"
     * contains the run (新安, 安装) — the 3-character phrase "新安装" — which also occurs inside
     * "全新安装" in a FRESH-install context, so a fresh install would be wrongly declared an
     * in-place update. With k = 3 the required phrase is 4 characters ("更新安装", "新安装包"), which
     * is specific enough to distinguish an update clause from a fresh-install clause while still
     * tolerating a condition that contains an extra clause the context never mentions.
     */
    private const val MIN_CONTIGUOUS_CJK_BIGRAMS = 3

    /** Clamp a requested top-k into the bounded [1, MAX_TOP_K] range. */
    fun clampTopK(requested: Int): Int {
        if (requested <= 0) return DEFAULT_TOP_K
        return requested.coerceAtMost(MAX_TOP_K)
    }

    /**
     * Machine-readable applicability of [episode] to the present task.
     *
     * FAIL CLOSED: when the episode's `requiredPrerequisitesJoined` or
     * `applicableConstraintsJoined` field could not be resolved from storage, the decoded list is
     * empty for a reason that has nothing to do with the task, so applicability cannot be
     * established. Such an episode is reported as [EpisodeApplicability.INELIGIBLE_UNRESOLVED_INTEGRITY]
     * and must not be presented as applicable. Only those two fields matter here: they are the ones
     * the applicability and constraint checks read.
     */
    fun episodeApplicability(episode: TaskEpisode): EpisodeApplicability {
        val unresolved = episode.listIntegrity.fields.filterKeys {
            it == TaskEpisodeEntity.FIELD_REQUIRED_PREREQUISITES ||
                it == TaskEpisodeEntity.FIELD_APPLICABLE_CONSTRAINTS
        }.values.any { it.isUnresolved }
        return if (unresolved) EpisodeApplicability.INELIGIBLE_UNRESOLVED_INTEGRITY else EpisodeApplicability.INTEGRITY_ELIGIBLE
    }

    /**
     * Rank episodes by token-overlap relevance to the query, returning at most clampTopK(topK)
     * episodes. Only lexical matches are returned (see [qualifiedLexicalScore]): sharing a single
     * word with the request is not a match. Deterministic: ties broken by higher createdAtMs then id.
     *
     * C02: both the query and each episode are reduced to MEANINGFUL tokens (see
     * [meaningfulTokens]) before scoring, so stopwords cannot create relevance on their own.
     *
     * Delegates to [scoreEpisodes] so the scoring and tie-break rules have a single definition.
     */
    fun rankRelevantEpisodes(query: String, episodes: List<TaskEpisode>, topK: Int = DEFAULT_TOP_K): List<TaskEpisode> {
        return scoreEpisodes(query, episodes).take(clampTopK(topK)).map { it.first }
    }

    /**
     * Score [episodes] against [query] and return the lexical matches (see [qualifiedLexicalScore]), sorted by descending
     * relevance with the documented deterministic tie-break (higher createdAtMs, then higher id).
     *
     * Unlike [rankRelevantEpisodes] this does NOT apply the top-k clamp, so callers that merge several
     * candidate sources (for example lexical plus vector candidates) can combine the full scored lists
     * before clamping once at the end.
     */
    fun scoreEpisodes(query: String, episodes: List<TaskEpisode>): List<Pair<TaskEpisode, Double>> {
        val queryTokens = meaningfulTokens(query)
        if (queryTokens.isEmpty()) return emptyList()
        return episodes
            .mapNotNull { episode -> qualifiedLexicalScore(queryTokens, episode)?.let { episode to it } }
            .sortedWith(
                compareByDescending<Pair<TaskEpisode, Double>> { it.second }
                    .thenByDescending { it.first.createdAtMs }
                    .thenByDescending { it.first.id }
            )
    }

    /**
     * Minimum number of DISTINCT meaningful tokens an episode must share with the query to be a
     * lexical match.
     *
     * Rationale (docs/TODO/episode-retrieval-threshold-20261030/README.md): before this rule any
     * episode sharing a single word with the request was retrieved, so the top-k slots filled with
     * whatever happened to mention "app", "build" or "update". One shared word is coincidence; the
     * same floor ([MIN_DISTINCT_MATCHED_TOKENS]) already applies to prerequisite applicability.
     */
    const val MIN_LEXICAL_SHARED_TOKENS = 2

    /**
     * Minimum fraction of the query's meaningful tokens an episode must share to be a lexical match.
     *
     * Rationale: in a long request two shared generic words ("fix", "problem") are still
     * coincidence. Requiring a quarter of the request's content words keeps a lexical match tied to
     * the request as a whole. Requests that a lexical match cannot capture (different vocabulary,
     * same task) are what the vector generator is for.
     */
    const val MIN_LEXICAL_SCORE = 0.25

    /**
     * Minimum cosine similarity for a vector-search candidate to qualify.
     *
     * Nearest-neighbour search always returns the k nearest episodes, however unrelated, so a vector
     * hit is not evidence of similarity by itself. Before this rule every vector hit qualified via a
     * fixed bonus and the top-k slots always filled whenever any episode existed.
     *
     * CALIBRATION (2026-10-30, Qwen3-Embedding-0.6B Q8_0 served by llama.cpp, last-token pooling, no
     * query instruction): over 7 realistic requests against 6 episode index texts, the intended
     * episode scored 0.540–0.741 (mean 0.671) and was always ranked first; every other pairing scored
     * at most 0.485 (mean 0.313), and off-topic requests (weather, recipes, jokes) at most 0.390.
     * 0.50 separates the two groups. Cosine scales differ between embedding models, so this value is
     * specific to that model and must be re-calibrated if the embedding provider changes.
     */
    const val MIN_VECTOR_SIMILARITY = 0.50

    /**
     * Merge lexical and vector candidates into one ranked list.
     *
     * MERGE RULE (single explicit rule, no fallback):
     *  1. A candidate QUALIFIES when it is a lexical match ([MIN_LEXICAL_SHARED_TOKENS] and
     *     [MIN_LEXICAL_SCORE], as applied by [scoreEpisodes]) OR its vector similarity is at least
     *     [MIN_VECTOR_SIMILARITY]. A candidate that meets neither is excluded: being among the
     *     nearest neighbours is not, on its own, evidence of similarity.
     *  2. UNION the qualifying candidates by episode id. The lexical path is a DEFINED MEMBER of the
     *     union, not a fallback.
     *  3. Each episode's combined score is its qualifying lexical score (0.0 when it is not a lexical
     *     match) plus its qualifying vector similarity (0.0 when it does not reach the threshold),
     *     so an episode matched both ways outranks one matched one way.
     *  4. Sort by combined score descending, then by createdAtMs descending, then by id descending.
     *     This tie-break is deterministic and identical to [scoreEpisodes].
     *  5. Take at most clampTopK(topK).
     *
     * When vector search is disabled the caller passes an empty [vectorSimilarities] map; the union
     * then contains exactly the lexical candidates and the result equals [rankRelevantEpisodes]. That
     * is the defined behaviour of this rule with no vector candidates, not a separate code path.
     *
     * DUPLICATE IDS: an id present in both [lexical] and [vectorCandidates] is stored ONCE and the
     * lexical entry wins; both describe the same stored episode.
     *
     * EMPTY QUERY: if [query] reduces to no meaningful tokens (see [meaningfulTokens]) the result is
     * empty — the same rule [scoreEpisodes] applies.
     *
     * @param lexical scored lexical candidates from [scoreEpisodes] (already qualified).
     * @param vectorSimilarities cosine similarity per episode id from vector search.
     * @param vectorCandidates episodes fetched for the ids in [vectorSimilarities].
     */
    fun mergeCandidates(
        query: String,
        lexical: List<Pair<TaskEpisode, Double>>,
        vectorSimilarities: Map<Long, Double>,
        vectorCandidates: List<TaskEpisode>,
        topK: Int = DEFAULT_TOP_K
    ): List<TaskEpisode> {
        val queryTokens = meaningfulTokens(query)
        if (queryTokens.isEmpty()) return emptyList()

        // Union by id. Lexical entries are inserted first and win on collision (same episode).
        val episodesById = LinkedHashMap<Long, TaskEpisode>()
        lexical.forEach { (episode, _) -> episodesById.putIfAbsent(episode.id, episode) }
        vectorCandidates.forEach { episode -> episodesById.putIfAbsent(episode.id, episode) }

        val lexicalScores = lexical.associate { (episode, score) -> episode.id to score }

        return episodesById.values
            .mapNotNull { episode ->
                val lexicalScore =
                    lexicalScores[episode.id] ?: qualifiedLexicalScore(queryTokens, episode) ?: 0.0
                val vectorScore =
                    vectorSimilarities[episode.id]?.takeIf { it >= MIN_VECTOR_SIMILARITY } ?: 0.0
                val combined = lexicalScore + vectorScore
                if (combined > 0.0) episode to combined else null
            }
            .sortedWith(
                compareByDescending<Pair<TaskEpisode, Double>> { it.second }
                    .thenByDescending { it.first.createdAtMs }
                    .thenByDescending { it.first.id }
            )
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
     * Rule (single documented rule, no fallback chain). Both sides are first reduced to MEANINGFUL
     * tokens ([meaningfulTokens]): stopwords and numeric-only tokens are dropped. A condition that
     * reduces to NO meaningful tokens (empty, blank, or made only of stopwords such as "the and of
     * to") is NOT applicable — there is nothing to match on — and a context that reduces to no
     * meaningful tokens is NOT applicable either. The condition is then matched by ONE of two
     * branches, selected by the SCRIPT of the condition, not by whether the other branch failed:
     *
     *  - CJK BRANCH (chosen when the condition contains any CJK token, see [isCjkToken]): the
     *    condition is applicable when the context contains a contiguous run of at least
     *    [MIN_CONTIGUOUS_CJK_BIGRAMS] consecutive meaningful CJK bigrams from the condition (see
     *    [hasContiguousCjkRun]). CJK text has no spaces, so a set-overlap fraction would punish a
     *    condition for containing a clause the context never mentions even when another clause
     *    matches perfectly; contiguous phrase containment asks the question applicability is
     *    actually trying to answer.
     *  - NON-CJK BRANCH (chosen when the condition contains no CJK token): the C02 rule — the
     *    condition is applicable only when BOTH thresholds are met: at least
     *    [MIN_DISTINCT_MATCHED_TOKENS] distinct meaningful tokens are shared, AND the shared count
     *    is at least [MIN_MATCHED_FRACTION] of the condition's meaningful tokens.
     *
     * These are two branches of ONE rule, selected deterministically by the condition's script.
     * There is deliberately NO loosening fallback: if the selected branch's test is not met the
     * answer is `false`. A single shared common token such as "app", "install", "apk" or "update"
     * must never make a condition applicable.
     */
    fun isPrerequisiteApplicable(prerequisiteCondition: String, currentTaskContext: String): Boolean {
        val conditionTokens = meaningfulTokens(prerequisiteCondition)
        if (conditionTokens.isEmpty()) return false
        val contextTokens = meaningfulTokens(currentTaskContext)
        if (contextTokens.isEmpty()) return false

        // CJK conditions are matched by contiguous phrase containment; see hasContiguousCjkRun.
        if (conditionTokens.any { isCjkToken(it) }) {
            return hasContiguousCjkRun(prerequisiteCondition, currentTaskContext)
        }

        // Non-CJK conditions keep the documented distinct-token + fraction rule from C02.
        val matched = conditionTokens.count { it in contextTokens }
        return matched >= MIN_DISTINCT_MATCHED_TOKENS &&
            matched.toDouble() / conditionTokens.size.toDouble() >= MIN_MATCHED_FRACTION
    }

    /**
     * True when the condition's meaningful CJK bigrams contain a run of at least
     * [MIN_CONTIGUOUS_CJK_BIGRAMS] consecutive bigrams that also appear consecutively in the context.
     *
     * Both sides are reduced to their ORDERED sequence of meaningful CJK bigrams (the tokenizer
     * emits overlapping bigrams, so consecutive bigrams in the sequence overlap by one character).
     * A run of k consecutive condition bigrams corresponds to a k+1 character phrase; the rule asks
     * whether that phrase occurs in the context.
     *
     * Limits: the phrase must appear verbatim. A paraphrase will not match, which is the
     * conservative direction for a caution that the model is told to verify.
     */
    private fun hasContiguousCjkRun(conditionText: String, contextText: String): Boolean {
        val conditionBigrams = orderedCjkBigrams(conditionText)
        if (conditionBigrams.size < MIN_CONTIGUOUS_CJK_BIGRAMS) return false
        val contextBigrams = orderedCjkBigrams(contextText)
        if (contextBigrams.size < MIN_CONTIGUOUS_CJK_BIGRAMS) return false

        val lastStart = contextBigrams.size - MIN_CONTIGUOUS_CJK_BIGRAMS
        return conditionBigrams.windowed(MIN_CONTIGUOUS_CJK_BIGRAMS).any { window ->
            (0..lastStart).any { start ->
                contextBigrams.subList(start, start + MIN_CONTIGUOUS_CJK_BIGRAMS) == window
            }
        }
    }

    /**
     * The ORDERED sequence of meaningful CJK bigrams in [text].
     *
     * Walks the text the same way [tokenize] does but keeps only CJK runs and preserves order, then
     * drops bigrams whose two characters are both Chinese stopwords (the same rule as
     * [isAllStopwordCjkBigram]) and single-character runs that are Chinese stopwords.
     */
    private fun orderedCjkBigrams(text: String): List<String> {
        val lowered = text.lowercase()
        val result = ArrayList<String>()
        var index = 0
        while (index < lowered.length) {
            val ch = lowered[index]
            if (isCjk(ch)) {
                var end = index
                while (end < lowered.length && isCjk(lowered[end])) end++
                val run = lowered.substring(index, end)
                if (run.length == 1) {
                    if (run !in CHINESE_STOPWORDS) result.add(run)
                } else {
                    for (i in 0 until run.length - 1) {
                        val bigram = run.substring(i, i + 2)
                        if (!isAllStopwordCjkBigram(bigram)) result.add(bigram)
                    }
                }
                index = end
            } else {
                index++
            }
        }
        return result
    }

    /** True when every character of [token] is CJK. */
    private fun isCjkToken(token: String): Boolean = token.all { isCjk(it) }

    /**
     * Relevance of one episode to an already-tokenized query.
     *
     * C02: the episode text is reduced to MEANINGFUL tokens ([meaningfulTokens]) instead of raw
     * tokens, so stopwords in a summary cannot inflate the score. The score is
     * `overlap / queryTokens.size`, where [queryTokens] is the caller's meaningful query token set.
     *
     * Returns null when the episode is not a lexical match: it has no meaningful tokens, shares fewer
     * than [MIN_LEXICAL_SHARED_TOKENS] distinct tokens with the query, or scores below
     * [MIN_LEXICAL_SCORE]. Such an episode is excluded by [scoreEpisodes] and contributes no lexical
     * score in [mergeCandidates].
     */
    private fun qualifiedLexicalScore(queryTokens: Set<String>, episode: TaskEpisode): Double? {
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
        if (episodeTokens.isEmpty()) return null
        val overlap = queryTokens.count { it in episodeTokens }
        val score = overlap.toDouble() / queryTokens.size.toDouble()
        // Lexical qualification: enough distinct shared content words, and enough of the request.
        if (overlap < MIN_LEXICAL_SHARED_TOKENS || score < MIN_LEXICAL_SCORE) return null
        return score
    }

    /**
     * Tokens of [text] that carry task-specific meaning.
     *
     * Starts from [tokenize] (the tokenizer is the Unicode-aware one described in [tokenize]) and
     * then removes:
     *  - tokens listed in [ENGLISH_STOPWORDS] or [CHINESE_STOPWORDS];
     *  - purely numeric tokens (all digits, e.g. "0" from a path such as
     *    "/storage/emulated/0/...");
     *  - CJK bigrams whose two characters are BOTH Chinese stopwords (see
     *    [isAllStopwordCjkBigram]).
     *
     * Numeric-only tokens are not meaningful for applicability: a version number, a path segment
     * or a port is not evidence that a conditional prerequisite applies to the present task.
     */
    private fun meaningfulTokens(text: String): Set<String> {
        return tokenize(text).filterNot { token ->
            token in ENGLISH_STOPWORDS ||
                token in CHINESE_STOPWORDS ||
                token.all { it in '0'..'9' } ||
                isAllStopwordCjkBigram(token)
        }.toSet()
    }

    /**
     * True when [token] is a CJK bigram whose two characters are BOTH Chinese stopwords.
     *
     * CJK BIGRAM RULE (C03): a token is a CJK bigram iff it has exactly two characters and both are
     * CJK (see [isCjk]). Such a bigram is dropped when BOTH of its characters are Chinese
     * stopwords, because a bigram made only of function words carries no meaning.
     *
     * SINGLE-CHARACTER CJK STOPWORDS: a CJK run of exactly one character is emitted as that single
     * character by [tokenize], so the `token in CHINESE_STOPWORDS` check in [meaningfulTokens] now
     * actually fires for it.
     */
    private fun isAllStopwordCjkBigram(token: String): Boolean {
        if (token.length != 2) return false
        if (!token.all { isCjk(it) }) return false
        return token[0].toString() in CHINESE_STOPWORDS && token[1].toString() in CHINESE_STOPWORDS
    }

    /**
     * Token set for [text], exposed for tests and diagnostics. Delegates to the same tokenizer used
     * by ranking and applicability, so tests exercise the production rule.
     */
    fun tokens(text: String): Set<String> = tokenize(text)

    /**
     * Unicode-aware, dependency-free tokenizer (C03).
     *
     * The text is lower-cased and walked once, grouping characters into maximal runs of CJK
     * characters (see [isCjk]) and maximal runs of non-CJK word characters
     * (`Character.isLetterOrDigit`, which is Unicode-aware so accented Latin works):
     *  - a NON-CJK run is emitted as ONE token when its length is at least 2, preserving the
     *    previous behaviour for Latin words;
     *  - a CJK run is emitted as every OVERLAPPING BIGRAM (characters i..i+1 for each i); a run of
     *    exactly one character is emitted as that single character.
     *
     * WHY BIGRAMS: CJK text is written without spaces, so there is no whitespace to split on.
     * Overlapping character bigrams are a standard deterministic segmentation that needs no
     * dictionary, no model and no Android dependency (no `android.icu`, no
     * `java.text.BreakIterator`), and they preserve partial matches: a phrase that appears inside a
     * longer sentence still produces the same bigrams.
     */
    private fun tokenize(text: String): Set<String> {
        val lowered = text.lowercase()
        val result = LinkedHashSet<String>()
        var index = 0
        while (index < lowered.length) {
            val ch = lowered[index]
            if (isCjk(ch)) {
                var end = index
                while (end < lowered.length && isCjk(lowered[end])) end++
                emitCjkRun(lowered.substring(index, end), result)
                index = end
            } else if (Character.isLetterOrDigit(ch)) {
                var end = index
                while (end < lowered.length &&
                    !isCjk(lowered[end]) &&
                    Character.isLetterOrDigit(lowered[end])
                ) {
                    end++
                }
                val run = lowered.substring(index, end)
                if (run.length >= 2) result.add(run)
                index = end
            } else {
                index++
            }
        }
        return result
    }

    /**
     * Emit the tokens of one maximal CJK [run]: every overlapping bigram, or the single character
     * when the run has length 1. See [tokenize] for the rationale.
     */
    private fun emitCjkRun(run: String, into: MutableSet<String>) {
        if (run.length == 1) {
            into.add(run)
            return
        }
        for (i in 0 until run.length - 1) {
            into.add(run.substring(i, i + 2))
        }
    }

    /**
     * True when [ch] belongs to one of the documented CJK blocks: [CJK_UNIFIED_IDEOGRAPHS],
     * [CJK_EXTENSION_A], [HIRAGANA], [KATAKANA] or [HANGUL_SYLLABLES].
     */
    private fun isCjk(ch: Char): Boolean {
        return ch in CJK_UNIFIED_IDEOGRAPHS ||
            ch in CJK_EXTENSION_A ||
            ch in HIRAGANA ||
            ch in KATAKANA ||
            ch in HANGUL_SYLLABLES
    }

    private fun normalizeEvidenceKey(raw: String): String {
        return raw.lowercase().trim().replace(Regex("\\s+"), " ")
    }
}
