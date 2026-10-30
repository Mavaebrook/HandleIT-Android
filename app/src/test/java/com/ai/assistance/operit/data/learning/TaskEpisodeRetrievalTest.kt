package com.ai.assistance.operit.data.learning

import com.ai.assistance.operit.data.model.TaskEpisode
import com.ai.assistance.operit.data.repository.TaskEpisodeRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM regression tests for Stage C (part 1): pure retrieval / dedup / prerequisite logic in
 * [TaskEpisodeRetrieval]. No Android or ObjectBox dependencies.
 */
class TaskEpisodeRetrievalTest {

    // --- Criterion 1: clampTopK bounds requested top-k into [1, MAX_TOP_K] ---

    @Test
    fun clampTopK_requestedZeroOrNegative_returnsDefaultTopK() {
        // Criterion 1: requested 0 or negative returns DEFAULT_TOP_K (3).
        assertEquals(TaskEpisodeRetrieval.DEFAULT_TOP_K, TaskEpisodeRetrieval.clampTopK(0))
        assertEquals(TaskEpisodeRetrieval.DEFAULT_TOP_K, TaskEpisodeRetrieval.clampTopK(-1))
        assertEquals(TaskEpisodeRetrieval.DEFAULT_TOP_K, TaskEpisodeRetrieval.clampTopK(-100))
        assertEquals(3, TaskEpisodeRetrieval.clampTopK(0))
    }

    @Test
    fun clampTopK_requestedAboveMax_returnsMaxTopK() {
        // Criterion 1: requested 10 returns MAX_TOP_K (5).
        assertEquals(TaskEpisodeRetrieval.MAX_TOP_K, TaskEpisodeRetrieval.clampTopK(10))
        assertEquals(5, TaskEpisodeRetrieval.clampTopK(10))
    }

    @Test
    fun clampTopK_requestedWithinRange_returnsRequested() {
        // Criterion 1: requested 4 returns 4.
        assertEquals(4, TaskEpisodeRetrieval.clampTopK(4))
    }

    // --- Criterion 2: rankRelevantEpisodes filters by overlap, orders by relevance, bounded by clampTopK ---

    @Test
    fun rankRelevantEpisodes_returnsOnlyLexicalMatchesOrderedByRelevance() {
        // Criterion 2: only lexical matches are returned, ordered by relevance.
        // query meaningful tokens = {signing, failed, place, apk, update} (5).
        val strong = episode(id = 1L, taskSummary = "signing failed on in-place apk update")
        val partial = episode(id = 4L, taskSummary = "apk update signing")
        val singleWord = episode(id = 2L, taskSummary = "signing")
        val unrelated = episode(id = 3L, taskSummary = "unrelated database migration")

        val ranked = TaskEpisodeRetrieval.rankRelevantEpisodes(
            query = "signing failed in-place apk update",
            episodes = listOf(singleWord, unrelated, partial, strong),
            topK = 3
        )

        // Unrelated episode (no overlap) is excluded.
        assertFalse(ranked.contains(unrelated))
        // One shared word ("signing", 1 of 5) is coincidence, not a match: excluded.
        assertFalse(ranked.contains(singleWord))
        // Stronger overlap ranks first; 3 of 5 shared tokens still qualifies.
        assertEquals(listOf(strong, partial), ranked)
    }

    @Test
    fun scoreEpisodes_twoGenericWordsInALongRequest_isNotAMatch() {
        // Two shared tokens ("fix", "problem"), but only 2 of 10 meaningful request tokens
        // (0.20 < MIN_LEXICAL_SCORE): generic words in a long request are coincidence.
        val generic = episode(id = 1L, taskSummary = "fix the problem with the printer driver")
        val scored = TaskEpisodeRetrieval.scoreEpisodes(
            query = "please fix this problem where gradle cache corrupts kotlin incremental compilation builds",
            episodes = listOf(generic)
        )
        assertTrue(scored.isEmpty())
    }

    @Test
    fun rankRelevantEpisodes_neverReturnsMoreThanClampTopK() {
        // Criterion 2: topK=10 requested but only MAX_TOP_K (5) are returned max.
        val episodes = (1L..8L).map { id ->
            episode(id = id, taskSummary = "signing failed on in-place apk update")
        }

        val ranked = TaskEpisodeRetrieval.rankRelevantEpisodes(
            query = "signing failed in-place apk update",
            episodes = episodes,
            topK = 10
        )

        assertEquals(TaskEpisodeRetrieval.MAX_TOP_K, ranked.size)
        assertEquals(5, ranked.size)
    }

    // --- Criterion 3: empty/blank query returns empty list ---

    @Test
    fun rankRelevantEpisodes_emptyOrBlankQuery_returnsEmptyList() {
        // Criterion 3: empty/blank query returns empty list.
        val episodes = listOf(episode(id = 1L, taskSummary = "signing failed on in-place apk update"))

        assertTrue(TaskEpisodeRetrieval.rankRelevantEpisodes("", episodes).isEmpty())
        assertTrue(TaskEpisodeRetrieval.rankRelevantEpisodes("   ", episodes).isEmpty())
        assertTrue(TaskEpisodeRetrieval.rankRelevantEpisodes("\t\n", episodes).isEmpty())
    }

    // --- Criterion 4: deterministic tie-break by createdAtMs then id ---

    @Test
    fun rankRelevantEpisodes_equalRelevance_breaksTiesByCreatedAtMsThenId() {
        // Criterion 4: deterministic tie-break by createdAtMs then id (both descending).
        val older = episode(id = 100L, taskSummary = "signing failed", createdAtMs = 1_000L)
        val newerLowId = episode(id = 1L, taskSummary = "signing failed", createdAtMs = 2_000L)
        val newerHighId = episode(id = 2L, taskSummary = "signing failed", createdAtMs = 2_000L)

        val ranked = TaskEpisodeRetrieval.rankRelevantEpisodes(
            query = "signing failed",
            episodes = listOf(older, newerLowId, newerHighId),
            topK = 3
        )

        // Higher createdAtMs first; among equal timestamps, higher id first.
        assertEquals(listOf(newerHighId, newerLowId, older), ranked)
    }

    // --- Criterion 5: repeated summary of one incident = one evidence source ---

    @Test
    fun deduplicateEvidenceKeys_repeatedIncidentCollapsesToOneKey() {
        // Criterion 5: one incident repeated in summaries collapses to ONE key.
        val keys = listOf(
            "Signing failed on update",
            "signing failed on update",
            "  Signing   failed on update "
        )

        val deduped = TaskEpisodeRetrieval.deduplicateEvidenceKeys(keys)

        assertEquals(1, deduped.size)
        assertEquals(listOf("signing failed on update"), deduped)
    }

    // --- Criterion 6: distinct incidents remain distinct ---

    @Test
    fun deduplicateEvidenceKeys_distinctIncidentsRemainDistinct() {
        // Criterion 6: distinct incidents remain distinct.
        val keys = listOf(
            "Signing failed on update",
            "Network timeout during download",
            "Storage permission denied"
        )

        val deduped = TaskEpisodeRetrieval.deduplicateEvidenceKeys(keys)

        assertEquals(3, deduped.size)
        assertEquals(
            listOf(
                "signing failed on update",
                "network timeout during download",
                "storage permission denied"
            ),
            deduped
        )
    }

    // --- Criterion 7: countIndependentEvidence counts paraphrases of one incident as one ---

    @Test
    fun countIndependentEvidence_paraphrasesOfSameIncidentCountOnce() {
        // Criterion 7: paraphrases of the same incident count as one; independent incidents separately.
        val episodeA = episode(
            id = 1L,
            taskSummary = "signing failed on update",
            evidenceKeys = listOf("Signing failed on update", "signing failed on update")
        )
        val episodeB = episode(
            id = 2L,
            taskSummary = "signing failed on update",
            evidenceKeys = listOf("  Signing   failed on update ")
        )
        val episodeC = episode(
            id = 3L,
            taskSummary = "network timeout",
            evidenceKeys = listOf("Network timeout during download")
        )

        val count = TaskEpisodeRetrieval.countIndependentEvidence(listOf(episodeA, episodeB, episodeC))

        // Two distinct incidents: the signing failure (paraphrased across A and B) and the timeout.
        assertEquals(2, count)
    }

    // --- Criterion 8: missingPrerequisites returns required not satisfied (case/whitespace-insensitive) ---

    @Test
    fun missingPrerequisites_returnsRequiredNotSatisfied_caseAndWhitespaceInsensitive() {
        // Criterion 8: returns required prerequisites not present in satisfied (case/whitespace-insensitive).
        val required = listOf("Signing continuity", "  Storage   permission  ", "Network access")
        val satisfied = listOf("signing continuity", "network access")

        val missing = TaskEpisodeRetrieval.missingPrerequisites(required, satisfied)

        assertEquals(listOf("  Storage   permission  "), missing)
    }

    // --- Criterion 9: missingPrerequisites returns empty when all required are satisfied ---

    @Test
    fun missingPrerequisites_allSatisfied_returnsEmpty() {
        // Criterion 9: returns empty when all required are satisfied.
        val required = listOf("Signing continuity", "Network access")
        val satisfied = listOf("  signing   continuity ", "NETWORK ACCESS")

        val missing = TaskEpisodeRetrieval.missingPrerequisites(required, satisfied)

        assertTrue(missing.isEmpty())
    }

    // --- Criterion 10: conditional prerequisite applicability (update vs fresh install) ---

    @Test
    fun isPrerequisiteApplicable_signingContinuity_appliesToInPlaceUpdateNotFreshInstall() {
        // Criterion 10: conditional prerequisite applies to in-place apk update, not fresh installation.
        val condition = "signing continuity for in-place apk update"

        assertTrue(
            TaskEpisodeRetrieval.isPrerequisiteApplicable(
                prerequisiteCondition = condition,
                currentTaskContext = "performing an in-place apk update of the installed app"
            )
        )
        assertFalse(
            TaskEpisodeRetrieval.isPrerequisiteApplicable(
                prerequisiteCondition = condition,
                currentTaskContext = "performing a fresh installation on a clean device"
            )
        )
    }

    // --- Criterion 11: empty condition returns false ---

    @Test
    fun isPrerequisiteApplicable_emptyCondition_returnsFalse() {
        // Criterion 11: empty condition returns false.
        assertFalse(
            TaskEpisodeRetrieval.isPrerequisiteApplicable(
                prerequisiteCondition = "",
                currentTaskContext = "performing an in-place apk update"
            )
        )
        assertFalse(
            TaskEpisodeRetrieval.isPrerequisiteApplicable(
                prerequisiteCondition = "   ",
                currentTaskContext = "performing an in-place apk update"
            )
        )
    }

    // --- C01: satisfiedPrerequisites is a deterministic lexical check over the request text ---

    @Test
    fun satisfiedPrerequisites_prerequisiteMentionedInRequest_isReturnedAsSatisfied() {
        // C01: a prerequisite whose normalized key occurs in the request text is evidently covered.
        val required = listOf("signing continuity", "network access")

        val satisfied = TaskEpisodeRetrieval.satisfiedPrerequisites(
            required = required,
            requestText = "Please do an in-place apk update; signing continuity is already configured."
        )

        assertEquals(listOf("signing continuity"), satisfied)
    }

    @Test
    fun satisfiedPrerequisites_prerequisiteNotMentionedInRequest_isNotReturned() {
        // C01: a prerequisite absent from the request text is NOT reported as satisfied.
        val required = listOf("signing continuity", "network access")

        val satisfied = TaskEpisodeRetrieval.satisfiedPrerequisites(
            required = required,
            requestText = "Please do an in-place apk update of the installed app."
        )

        assertTrue(satisfied.isEmpty())
    }

    @Test
    fun satisfiedPrerequisites_emptyRequest_returnsEmpty() {
        // C01: an empty/blank request covers nothing.
        val required = listOf("signing continuity", "network access")

        assertTrue(TaskEpisodeRetrieval.satisfiedPrerequisites(required, "").isEmpty())
        assertTrue(TaskEpisodeRetrieval.satisfiedPrerequisites(required, "   ").isEmpty())
        assertTrue(TaskEpisodeRetrieval.satisfiedPrerequisites(required, "\t\n").isEmpty())
    }

    @Test
    fun satisfiedPrerequisites_blankPrerequisite_isNeverSatisfied() {
        // C01: a blank prerequisite normalizes to an empty key and is never reported as satisfied.
        val required = listOf("", "   ")

        val satisfied = TaskEpisodeRetrieval.satisfiedPrerequisites(
            required = required,
            requestText = "anything at all"
        )

        assertTrue(satisfied.isEmpty())
    }

    // --- C02: stopword-aware, threshold-based applicability ---

    @Test
    fun isPrerequisiteApplicable_freshInstallation_doesNotMatchUpdateOnlyPrerequisite() {
        // C02: the condition and the context deliberately share "the", "app", "install" and "apk",
        // but a fresh install is not an in-place update, so the condition must NOT apply.
        assertFalse(
            TaskEpisodeRetrieval.isPrerequisiteApplicable(
                prerequisiteCondition = "signing continuity for the in-place apk update of the app",
                currentTaskContext = "install the app fresh on a clean device and verify the apk"
            )
        )
    }

    @Test
    fun isPrerequisiteApplicable_inPlaceUpdate_matchesUpdateOnlyPrerequisite() {
        // C02: condition meaningful = {signing, continuity, place, apk, update, app} (6);
        // context meaningful = {perform, place, apk, update, app, device} (6);
        // matched = {place, apk, update, app} = 4 >= 2 and 4/6 = 0.667 >= 0.5 -> applicable.
        assertTrue(
            TaskEpisodeRetrieval.isPrerequisiteApplicable(
                prerequisiteCondition = "signing continuity for the in-place apk update of the app",
                currentTaskContext = "perform the in-place apk update of the app on the device"
            )
        )
    }

    @Test
    fun isPrerequisiteApplicable_pathLikeContext_doesNotMatchOnComOrStorage() {
        // C02: a path-like context shares "storage" and "app" with the condition, but that is only
        // 2 of the condition's 5 meaningful tokens (0.4 < 0.5), so the condition must NOT apply.
        assertFalse(
            TaskEpisodeRetrieval.isPrerequisiteApplicable(
                prerequisiteCondition = "storage permission for the app data directory",
                currentTaskContext = "/storage/emulated/0/com.example.app/files"
            )
        )
    }

    @Test
    fun isPrerequisiteApplicable_allStopwordCondition_isNotApplicable() {
        // C02: a condition that reduces to no meaningful tokens is never applicable.
        assertFalse(
            TaskEpisodeRetrieval.isPrerequisiteApplicable(
                prerequisiteCondition = "the and of to in for with",
                currentTaskContext = "perform the in-place apk update of the app on the device"
            )
        )
        assertFalse(
            TaskEpisodeRetrieval.isPrerequisiteApplicable(
                prerequisiteCondition = "的了和与或在",
                currentTaskContext = "对已安装的应用执行就地更新安装包"
            )
        )
    }

    @Test
    fun isPrerequisiteApplicable_chineseUpdateVersusFreshInstall_behavesTheSame() {
        // C02/C03: the Chinese case mirrors the English update-vs-fresh-install case. The C03
        // Unicode-aware tokenizer segments CJK runs into overlapping bigrams, so the Chinese
        // condition and context are compared on the same footing as English words.
        val condition = "就地更新安装包时的签名连续性"

        assertTrue(
            TaskEpisodeRetrieval.isPrerequisiteApplicable(
                prerequisiteCondition = condition,
                currentTaskContext = "对已安装的应用执行就地更新安装包"
            )
        )
        assertFalse(
            TaskEpisodeRetrieval.isPrerequisiteApplicable(
                prerequisiteCondition = condition,
                currentTaskContext = "在干净的设备上全新安装应用"
            )
        )
    }

    @Test
    fun rankRelevantEpisodes_stopwordsDoNotCreateRelevance() {
        // C02: an episode made only of stopwords has no meaningful tokens and must not be returned.
        val relevant = episode(id = 1L, taskSummary = "the app update")
        val allStopwords = episode(id = 2L, taskSummary = "the of and to")

        val ranked = TaskEpisodeRetrieval.rankRelevantEpisodes(
            query = "the app update",
            episodes = listOf(relevant, allStopwords),
            topK = 3
        )

        assertTrue(ranked.contains(relevant))
        assertFalse(ranked.contains(allStopwords))
    }

    // --- C03 (b): scoreEpisodes returns the full scored list, without the top-k clamp ---

    @Test
    fun scoreEpisodes_returnsScoredCandidatesWithoutTopKClamp() {
        // C03 (b): scoreEpisodes is the unclamped primitive; rankRelevantEpisodes clamps on top of it.
        val episodes = (1L..8L).map { id ->
            episode(id = id, taskSummary = "signing failed on in-place apk update")
        }

        val scored = TaskEpisodeRetrieval.scoreEpisodes(
            query = "signing failed in-place apk update",
            episodes = episodes
        )
        val ranked = TaskEpisodeRetrieval.rankRelevantEpisodes(
            query = "signing failed in-place apk update",
            episodes = episodes,
            topK = 10
        )

        // All 8 episodes match, so the unclamped scorer returns all 8 ...
        assertEquals(8, scored.size)
        // ... while the ranking entry point still clamps to MAX_TOP_K (5).
        assertEquals(TaskEpisodeRetrieval.MAX_TOP_K, ranked.size)
        assertEquals(5, ranked.size)
        // The clamped list is exactly the head of the unclamped list.
        assertEquals(scored.take(5).map { it.first }, ranked)
    }

    // --- C03 (b): mergeCandidates applies the vector bonus ---

    @Test
    fun mergeCandidates_qualifyingVectorHitOutranksLexicalOnly() {
        // Two episodes with the SAME lexical score; only one is also a qualifying vector hit.
        val lexicalOnly = episode(id = 1L, taskSummary = "signing failed", createdAtMs = 1_000L)
        val vectorHit = episode(id = 2L, taskSummary = "signing failed", createdAtMs = 1_000L)

        val lexical = TaskEpisodeRetrieval.scoreEpisodes(
            query = "signing failed",
            episodes = listOf(lexicalOnly, vectorHit)
        )
        // Precondition: the two lexical scores really are equal, so only the bonus can separate them.
        assertEquals(2, lexical.size)
        assertEquals(lexical[0].second, lexical[1].second, 0.0)

        val merged = TaskEpisodeRetrieval.mergeCandidates(
            query = "signing failed",
            lexical = lexical,
            vectorSimilarities = mapOf(vectorHit.id to 0.8),
            vectorCandidates = listOf(vectorHit),
            topK = 5
        )

        // Matched both ways (1.0 + 0.8) outranks matched lexically only (1.0).
        assertEquals(listOf(vectorHit, lexicalOnly), merged)
    }

    // --- C03 (b): mergeCandidates tie-break is deterministic ---

    @Test
    fun mergeCandidates_tiesBrokenByCreatedAtMsThenId() {
        // C03 (b): equal combined scores -> higher createdAtMs first, then higher id.
        val older = episode(id = 100L, taskSummary = "signing failed", createdAtMs = 1_000L)
        val newerLowId = episode(id = 1L, taskSummary = "signing failed", createdAtMs = 2_000L)
        val newerHighId = episode(id = 2L, taskSummary = "signing failed", createdAtMs = 2_000L)

        val lexical = TaskEpisodeRetrieval.scoreEpisodes(
            query = "signing failed",
            episodes = listOf(older, newerLowId, newerHighId)
        )

        val merged = TaskEpisodeRetrieval.mergeCandidates(
            query = "signing failed",
            lexical = lexical,
            vectorSimilarities = emptyMap(),
            vectorCandidates = emptyList(),
            topK = 5
        )

        assertEquals(listOf(newerHighId, newerLowId, older), merged)
    }

    // --- C03 (b): with no vector candidates the merge equals the lexical ranking ---

    @Test
    fun mergeCandidates_emptyVectorIds_equalsLexicalRanking() {
        // C03 (b): empty vectorSimilarities is the DEFINED no-vector behaviour, not a separate code path.
        val episodes = listOf(
            episode(id = 1L, taskSummary = "signing failed on in-place apk update", createdAtMs = 1_000L),
            episode(id = 2L, taskSummary = "signing", createdAtMs = 2_000L),
            episode(id = 3L, taskSummary = "unrelated database migration", createdAtMs = 3_000L),
            episode(id = 4L, taskSummary = "apk update signing", createdAtMs = 4_000L)
        )
        val query = "signing failed in-place apk update"
        val topK = 3

        val lexical = TaskEpisodeRetrieval.scoreEpisodes(query, episodes)
        val merged = TaskEpisodeRetrieval.mergeCandidates(
            query = query,
            lexical = lexical,
            vectorSimilarities = emptyMap(),
            vectorCandidates = emptyList(),
            topK = topK
        )

        assertEquals(
            TaskEpisodeRetrieval.rankRelevantEpisodes(query, episodes, topK),
            merged
        )
    }

    // --- C03 (b): mergeCandidates clamps once at the end ---

    @Test
    fun mergeCandidates_resultIsClampedToTopK() {
        // C03 (b): 10 candidates, topK 10 requested -> clampTopK caps the result at MAX_TOP_K (5).
        val episodes = (1L..10L).map { id ->
            episode(id = id, taskSummary = "signing failed on in-place apk update", createdAtMs = id)
        }

        val lexical = TaskEpisodeRetrieval.scoreEpisodes(
            query = "signing failed in-place apk update",
            episodes = episodes
        )
        assertEquals(10, lexical.size)

        val merged = TaskEpisodeRetrieval.mergeCandidates(
            query = "signing failed in-place apk update",
            lexical = lexical,
            vectorSimilarities = emptyMap(),
            vectorCandidates = emptyList(),
            topK = 10
        )

        assertEquals(TaskEpisodeRetrieval.MAX_TOP_K, merged.size)
        assertEquals(5, merged.size)
    }

    // --- C03 (b): the bounded candidate cap is a documented positive constant ---

    @Test
    fun maxCandidatesExamined_isAPositiveDocumentedCap() {
        // C03 (b): retrieval must not load the whole table, so the candidate window is capped.
        //
        // HONEST LIMITATION: the bounded ObjectBox query itself (order by createdAt DESC + find(0, N))
        // CANNOT be unit-tested on the JVM, because ObjectBox requires an Android runtime and a native
        // store. Only the cap constant is asserted here; the query is verified by code inspection of
        // TaskEpisodeRepository.recentCandidates().
        assertTrue(TaskEpisodeRepository.MAX_CANDIDATES_EXAMINED > 0)
        assertTrue(TaskEpisodeRepository.MAX_CANDIDATES_EXAMINED <= 1000)
    }

    // --- C03 (c): vector retrieval as an ADDITIONAL candidate generator ---
    //
    // HONEST LIMITATION: TaskEpisodeVectorIndex requires an Android runtime (Context, SharedPreferences,
    // the HNSW native library and an HTTP embedding provider), so it CANNOT be unit-tested on the JVM.
    // It is verified by inspection plus CI compilation. The tests below exercise only the pure merge
    // rule in TaskEpisodeRetrieval.mergeCandidates, which is what the vector generator feeds.

    @Test
    fun mergeCandidates_vectorOnlyHitAboveThreshold_qualifiesWithoutLexicalMatch() {
        // A vector-only hit with no shared words qualifies on similarity alone when it reaches
        // MIN_VECTOR_SIMILARITY: this is how different-vocabulary requests find the right episode.
        val query = "signing failed"
        val vectorOnly = episode(id = 7L, taskSummary = "database migration rollback", createdAtMs = 1_000L)

        val lexical = TaskEpisodeRetrieval.scoreEpisodes(query, listOf(vectorOnly))
        assertTrue(lexical.isEmpty())

        val merged = TaskEpisodeRetrieval.mergeCandidates(
            query = query,
            lexical = lexical,
            vectorSimilarities = mapOf(vectorOnly.id to TaskEpisodeRetrieval.MIN_VECTOR_SIMILARITY),
            vectorCandidates = listOf(vectorOnly),
            topK = 3
        )

        assertEquals(listOf(vectorOnly), merged)
    }

    @Test
    fun mergeCandidates_vectorOnlyHitBelowThreshold_isExcluded() {
        // Nearest-neighbour search always returns the nearest episodes, however unrelated. A vector
        // hit below MIN_VECTOR_SIMILARITY with no lexical match is not evidence of similarity and is
        // excluded (it used to survive on a fixed bonus, which filled every top-k slot).
        //
        // HAND TRACE:
        //  query = "signing failed" -> meaningfulTokens = {signing, failed} (size 2).
        //  vectorOnly = episode(id=7, taskSummary="database migration rollback", createdAtMs=1_000).
        //    meaningfulTokens(vectorOnly) = {database, migration, rollback}; overlap with the query is
        //    0, so it is not a lexical match.
        //  lexical = scoreEpisodes(query, listOf(vectorOnly)) -> the 0.0 score is filtered out, so
        //    lexical = [] (the episode is NOT a lexical candidate at all).
        //  vectorSimilarities = {7 -> 0.30}, vectorCandidates = [vectorOnly].
        //  mergeCandidates:
        //    union by id -> {7 -> vectorOnly} (from vectorCandidates; lexical contributed nothing).
        //    lexical score = 0.0 (not a lexical match).
        //    vector score = 0.0 because 0.30 < MIN_VECTOR_SIMILARITY.
        //    combined = 0.0, so the episode is excluded.
        //    result = [].
        val query = "signing failed"
        val vectorOnly = episode(id = 7L, taskSummary = "database migration rollback", createdAtMs = 1_000L)

        val lexical = TaskEpisodeRetrieval.scoreEpisodes(query, listOf(vectorOnly))
        // Precondition: the episode is not a lexical candidate at all (score 0.0 is filtered out).
        assertTrue(lexical.isEmpty())

        val merged = TaskEpisodeRetrieval.mergeCandidates(
            query = query,
            lexical = lexical,
            vectorSimilarities = mapOf(vectorOnly.id to 0.30),
            vectorCandidates = listOf(vectorOnly),
            topK = 3
        )

        assertTrue(merged.isEmpty())
    }

    @Test
    fun mergeCandidates_lexicalEntryWinsOnDuplicateId() {
        // C03 (c): the same id in both lexical and vectorCandidates yields ONE entry (no duplicate).
        //
        // HAND TRACE:
        //  query = "signing failed" -> meaningfulTokens = {signing, failed} (size 2).
        //  shared = episode(id=3, taskSummary="signing failed", createdAtMs=1_000).
        //    lexical score = 2 / 2 = 1.0, so lexical = [(shared, 1.0)].
        //  vectorSimilarities = {3 -> 0.9}, vectorCandidates = [shared] (the same stored episode).
        //  mergeCandidates:
        //    union by id -> lexical inserts 3 first; putIfAbsent for the vector entry is a no-op,
        //      so episodesById has exactly ONE entry.
        //    lexicalScore = 1.0 (taken from lexicalScores), vector score = 0.9, combined = 1.9.
        //    result = [shared] with size 1.
        val query = "signing failed"
        val shared = episode(id = 3L, taskSummary = "signing failed", createdAtMs = 1_000L)

        val lexical = TaskEpisodeRetrieval.scoreEpisodes(query, listOf(shared))
        assertEquals(1, lexical.size)

        val merged = TaskEpisodeRetrieval.mergeCandidates(
            query = query,
            lexical = lexical,
            vectorSimilarities = mapOf(shared.id to 0.9),
            vectorCandidates = listOf(shared),
            topK = 3
        )

        // Exactly one entry: the duplicate id is collapsed, not repeated.
        assertEquals(1, merged.size)
        assertEquals(listOf(shared), merged)
    }

    private fun episode(
        id: Long = 0L,
        taskSummary: String = "",
        contextSummary: String = "",
        evidenceKeys: List<String> = emptyList(),
        createdAtMs: Long = 0L
    ): TaskEpisode = TaskEpisode(
        id = id,
        taskSummary = taskSummary,
        contextSummary = contextSummary,
        evidenceKeys = evidenceKeys,
        createdAtMs = createdAtMs
    )
}
