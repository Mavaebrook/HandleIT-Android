package com.ai.assistance.operit.data.learning

import com.ai.assistance.operit.data.model.TaskEpisode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic, Android-free replay harness over the EXISTING retrieval and formatter logic
 * (Step 1, Area 1). TEST-ONLY: no production file is touched and no production behaviour is changed.
 *
 * WHAT THIS HARNESS IS: a fixed, bounded fixture set ([ReplayFixtures]) replayed through the real
 * [TaskEpisodeRetrieval] and [TaskPreflightFormatter] code, asserting the CURRENT outputs.
 *
 * WHAT THIS HARNESS IS NOT: it is not evidence of end-to-end task improvement, not a device latency
 * measurement, and not a structural-transfer result. It measures only what the fixtures actually
 * support: whether the expected episode is ranked first, and whether the expected cautions are
 * rendered.
 *
 * KNOWN-LIMITATION POLICY: where the current behaviour is wrong, the assertion records the CURRENT
 * (wrong) output and is marked `KNOWN LIMITATION`. It is never asserted as desired behaviour, so a
 * later fix will visibly change the assertion instead of silently passing.
 */
class ReplayHarnessTest {

    private val topK = CurrentReplayRunner.TOP_K

    // --- Fixture set sanity: fixed, bounded, labelled ------------------------------------------

    @Test
    fun fixtures_areFixedBoundedAndAllSynthetic() {
        // The set is bounded: 8-12 fixtures, one per required scenario.
        assertTrue(
            "fixture set must be bounded to 8..12 entries, was ${ReplayFixtures.all.size}",
            ReplayFixtures.all.size in 8..12
        )
        // Ids are unique and stable.
        assertEquals(
            "fixture ids must be unique",
            ReplayFixtures.all.size,
            ReplayFixtures.all.map { it.id }.toSet().size
        )
        // Every fixture is explicitly labelled SYNTHETIC: no real evidence exists, so no fixture may
        // claim SANITIZED_REAL provenance.
        ReplayFixtures.all.forEach { fixture ->
            assertEquals(
                "fixture ${fixture.id} must be labelled SYNTHETIC",
                FixtureProvenance.SYNTHETIC,
                fixture.provenance
            )
        }
        // Both families are populated, so the dev/held-out split is real and not decorative.
        assertTrue("DEVELOPMENT family must be non-empty", ReplayFixtures.development.isNotEmpty())
        assertTrue("HELD_OUT family must be non-empty", ReplayFixtures.heldOut.isNotEmpty())
        // The split is disjoint and covers the whole set.
        assertEquals(
            ReplayFixtures.all.size,
            ReplayFixtures.development.size + ReplayFixtures.heldOut.size
        )
    }

    // --- Retrieval: expected top episode -------------------------------------------------------

    @Test
    fun retrieval_returnsExpectedTopEpisode_forDevelopmentFixtures() {
        assertExpectedTopEpisodes(ReplayFixtures.development)
    }

    @Test
    fun retrieval_returnsExpectedTopEpisode_forHeldOutFixtures() {
        assertExpectedTopEpisodes(ReplayFixtures.heldOut)
    }

    private fun assertExpectedTopEpisodes(fixtures: List<ReplayFixture>) {
        fixtures.filter { it.expectedTopEpisodeId != null }.forEach { fixture ->
            val ranked = TaskEpisodeRetrieval.rankRelevantEpisodes(
                query = fixture.query,
                episodes = fixture.episodes,
                topK = topK
            )
            assertEquals(
                "fixture ${fixture.id}: unexpected top episode",
                fixture.expectedTopEpisodeId,
                ranked.firstOrNull()?.id
            )
        }
    }

    // --- Formatter: expected cautions ----------------------------------------------------------

    @Test
    fun formatter_rendersExpectedCautions() {
        val fixtures = ReplayFixtures.all.filter { it.expectedCautionContains.isNotEmpty() }
        assertTrue("at least one fixture must assert a rendered caution", fixtures.isNotEmpty())
        fixtures.forEach { fixture ->
            val block = blockFor(fixture)
            fixture.expectedCautionContains.forEach { expected ->
                assertTrue(
                    "fixture ${fixture.id}: block must contain '$expected' but was:\n$block",
                    block.contains(expected)
                )
            }
        }
    }

    @Test
    fun formatter_omitsExpectedAbsentCautions() {
        val fixtures = ReplayFixtures.all.filter { it.expectedCautionAbsent.isNotEmpty() }
        assertTrue("at least one fixture must assert an absent caution", fixtures.isNotEmpty())
        fixtures.forEach { fixture ->
            val block = blockFor(fixture)
            fixture.expectedCautionAbsent.forEach { absent ->
                assertFalse(
                    "fixture ${fixture.id}: block must NOT contain '$absent' but was:\n$block",
                    block.contains(absent)
                )
            }
        }
    }

    // --- Known limitation 1: substring satisfaction --------------------------------------------

    /**
     * KNOWN LIMITATION (not desired behaviour).
     *
     * [TaskEpisodeRetrieval.satisfiedPrerequisites] decides satisfaction by normalized SUBSTRING
     * containment. A prerequisite whose text happens to occur inside an unrelated word is therefore
     * reported as satisfied. The assertions below record the CURRENT behaviour so that a later
     * token-boundary fix changes them visibly.
     */
    @Test
    fun knownLimitation_substringSatisfaction() {
        // "root" is a substring of "rootedness", which has nothing to do with root access.
        val satisfied = TaskEpisodeRetrieval.satisfiedPrerequisites(
            required = listOf("root"),
            requestText = "check the rootedness of the build tree"
        )
        // KNOWN LIMITATION: the prerequisite is reported satisfied purely because "root" occurs
        // inside "rootedness". This is NOT desired behaviour.
        assertEquals(listOf("root"), satisfied)

        // The same limitation reaches the formatter: the caution is omitted for a prerequisite that
        // was never actually stated.
        val fixture = ReplayFixtures.all.first { it.id == "substringSatisfaction" }
        val block = blockFor(fixture)
        // KNOWN LIMITATION: no "verify before relying on this: root" line is rendered.
        assertFalse(block.contains("verify before relying on this: root"))

        // Control: a request that does NOT contain the substring is correctly reported as missing,
        // which shows the limitation is specifically about substring matching, not about the
        // missing-prerequisite path being broken.
        val notSatisfied = TaskEpisodeRetrieval.satisfiedPrerequisites(
            required = listOf("root"),
            requestText = "check the build tree before packaging"
        )
        assertTrue(notSatisfied.isEmpty())
    }

    // --- Known limitation 2: analogous structure, different vocabulary -------------------------

    /**
     * KNOWN LIMITATION (not desired behaviour).
     *
     * Retrieval is lexical: it scores token overlap only. Two tasks with the same structure but
     * different words ("install apk" vs "deploy package") are matched only if they happen to share
     * a word, and then the match is driven by that word rather than by structure. This is the
     * motivation for later structural matching; it is not implemented here.
     */
    @Test
    fun knownLimitation_analogousStructureDifferentVocabulary() {
        val fixture = ReplayFixtures.all.first { it.id == "analogousStructureDifferentVocabulary" }

        // The only shared meaningful token is "package", which occurs in the episode inside the
        // incidental phrase "package installer" — it is not the object being installed.
        val queryTokens = TaskEpisodeRetrieval.tokens(fixture.query)
            .filterNot { it in ENGLISH_FUNCTION_WORDS }
            .toSet()
        val episodeTokens = TaskEpisodeRetrieval.tokens(
            fixture.episodes.single().let {
                listOf(
                    it.taskSummary,
                    it.contextSummary,
                    it.failureSummary,
                    it.causeSummary,
                    it.resolutionSummary,
                    it.requiredPrerequisites.joinToString(" "),
                    it.applicableConstraints.joinToString(" ")
                ).joinToString(" ")
            }
        )
        val shared = queryTokens.intersect(episodeTokens)
        // KNOWN LIMITATION: the entire "match" is one incidental word.
        assertEquals(setOf("package"), shared)

        // Since the lexical threshold (MIN_LEXICAL_SHARED_TOKENS = 2), one coincidental shared word
        // no longer retrieves the episode. KNOWN LIMITATION that remains: the structural match is not
        // found lexically either; only the vector generator can find it.
        val scored = TaskEpisodeRetrieval.scoreEpisodes(fixture.query, fixture.episodes)
        assertTrue("an incidental single-word overlap must not be a lexical match", scored.isEmpty())

        // Control: the same episode IS retrieved strongly when the vocabulary matches, which shows
        // the weakness is vocabulary dependence, not an unusable episode.
        val sameVocabulary = TaskEpisodeRetrieval.scoreEpisodes(
            "install the apk on the emulator",
            fixture.episodes
        )
        assertEquals(
            "same-vocabulary query must still retrieve the episode",
            listOf(701L),
            sameVocabulary.map { it.first.id }
        )
    }

    // --- Known limitation 3: no executor or scope scoping --------------------------------------

    /**
     * KNOWN LIMITATION (not desired behaviour).
     *
     * [TaskEpisode] carries no executor or scope field, and [TaskEpisodeRetrieval] has no executor
     * or scope parameter. Retrieval therefore cannot distinguish an episode recorded under a
     * different executor or in a different project scope from one recorded in the present context.
     * The assertions below record the CURRENT behaviour.
     */
    @Test
    fun knownLimitation_noExecutorOrScopeScoping() {
        // Executor mismatch: the episode was recorded by a shell executor, the present task uses a
        // workspace file executor. Retrieval still returns it.
        val executorFixture = ReplayFixtures.all.first { it.id == "executorMismatch" }
        val executorRanked = TaskEpisodeRetrieval.rankRelevantEpisodes(
            query = executorFixture.query,
            episodes = executorFixture.episodes,
            topK = topK
        )
        // KNOWN LIMITATION: no executor scoping, so the mismatched episode is retrieved.
        assertEquals(listOf(501L), executorRanked.map { it.id })

        // Scope mismatch: the episode belongs to project alpha, the present task to project beta.
        val scopeFixture = ReplayFixtures.all.first { it.id == "scopeMismatch" }
        val scopeRanked = TaskEpisodeRetrieval.rankRelevantEpisodes(
            query = scopeFixture.query,
            episodes = scopeFixture.episodes,
            topK = topK
        )
        // KNOWN LIMITATION: no scope scoping, so the out-of-scope episode is retrieved.
        assertEquals(listOf(601L), scopeRanked.map { it.id })

        // The limitation is structural, not a missing filter a caller could apply: the retrieval
        // API's only inputs are query, episodes and topK, and TaskEpisode has no executor/scope
        // field. Both facts are asserted here so that adding either later changes this test.
        val episodeFields = TaskEpisode::class.java.declaredFields.map { it.name }
        assertFalse(
            "TaskEpisode must not yet carry executor/scope fields; if it does, this limitation is fixed",
            episodeFields.any { it.contains("executor", ignoreCase = true) || it.contains("scope", ignoreCase = true) }
        )
    }

    // --- Metrics: only what the fixtures actually support --------------------------------------

    /**
     * Retrieval recall and prerequisite-caution precision over the fixed fixture set.
     *
     * These are the ONLY metrics the fixtures support. They are computed from the fixtures at
     * runtime and printed, and the assertions use the values the current fixtures actually achieve
     * (computed below, not invented). No end-to-end task improvement, device latency or
     * structural-transfer claim is made or implied.
     *
     * Definitions:
     *  - retrieval recall = fixtures with a non-null expectedTopEpisodeId whose top-ranked episode
     *    IS that id, divided by the number of fixtures with a non-null expectedTopEpisodeId.
     *  - caution precision = expected caution substrings actually rendered, divided by all expected
     *    caution substrings across fixtures that declare any.
     */
    @Test
    fun metrics_retrievalRecallAndPrerequisitePrecision() {
        val retrievalFixtures = ReplayFixtures.all.filter { it.expectedTopEpisodeId != null }
        val retrievalHits = retrievalFixtures.count { fixture ->
            TaskEpisodeRetrieval.rankRelevantEpisodes(
                query = fixture.query,
                episodes = fixture.episodes,
                topK = topK
            ).firstOrNull()?.id == fixture.expectedTopEpisodeId
        }
        val recall = retrievalHits.toDouble() / retrievalFixtures.size.toDouble()

        val cautionFixtures = ReplayFixtures.all.filter { it.expectedCautionContains.isNotEmpty() }
        val expectedCautions = cautionFixtures.flatMap { it.expectedCautionContains }
        val renderedCautions = cautionFixtures.sumOf { fixture ->
            val block = blockFor(fixture)
            fixture.expectedCautionContains.count { block.contains(it) }
        }
        val cautionPrecision = renderedCautions.toDouble() / expectedCautions.size.toDouble()

        val report = buildString {
            appendLine("Replay harness metrics (current production logic, synthetic fixtures only):")
            appendLine("  fixtures total            = ${ReplayFixtures.all.size}")
            appendLine("  development / held-out    = ${ReplayFixtures.development.size} / ${ReplayFixtures.heldOut.size}")
            appendLine("  retrieval fixtures        = ${retrievalFixtures.size}")
            appendLine("  retrieval top-1 hits      = $retrievalHits")
            appendLine("  retrieval recall          = $recall")
            appendLine("  caution fixtures          = ${cautionFixtures.size}")
            appendLine("  expected cautions         = ${expectedCautions.size}")
            appendLine("  rendered cautions         = $renderedCautions")
            appendLine("  caution precision         = $cautionPrecision")
            appendLine("  NOTE: these numbers describe the CURRENT logic on SYNTHETIC fixtures.")
            appendLine("        They are not end-to-end task improvement, not device latency and not")
            appendLine("        structural-transfer evidence.")
        }
        println(report)

        // Assert only what the fixtures actually support. The current fixture set achieves full
        // top-1 recall (every fixture with an expected top episode gets it) and full caution
        // precision (every expected caution substring is rendered). Both are asserted as the
        // CURRENT values; a regression below them is a real change in behaviour.
        assertEquals(
            "retrieval recall over fixtures with an expected top episode",
            1.0,
            recall,
            0.0
        )
        assertEquals(
            "caution precision over fixtures with expected cautions",
            1.0,
            cautionPrecision,
            0.0
        )
        // The metrics must be computed over a non-trivial sample, otherwise they mean nothing.
        assertTrue("retrieval metric needs fixtures", retrievalFixtures.size >= 4)
        assertTrue("caution metric needs fixtures", cautionFixtures.size >= 2)
    }

    // --- Evaluation seam: baseline runner and comparison ---------------------------------------

    /**
     * The seam is exercised, not just declared: the current runner reproduces the same outcomes the
     * harness asserts, and comparing the baseline with itself reports no differences. This proves
     * the seam is wired to the real production logic without implementing any optimization.
     */
    @Test
    fun evaluationSeam_currentRunnerMatchesHarnessAndSelfComparisonIsEmpty() {
        val baseline = ReplayFixtures.all.map { CurrentReplayRunner.run(it) }
        assertEquals(ReplayFixtures.all.size, baseline.size)

        // The runner's retrieved ids agree with a direct call to the production retrieval.
        ReplayFixtures.all.zip(baseline).forEach { (fixture, outcome) ->
            val direct = TaskEpisodeRetrieval.rankRelevantEpisodes(
                query = fixture.query,
                episodes = fixture.episodes,
                topK = topK
            ).map { it.id }
            assertEquals(
                "fixture ${fixture.id}: runner must use production retrieval",
                direct,
                outcome.retrievedIds
            )
        }

        // Baseline vs itself: no differences. The seam reports differences, it does not invent them.
        val selfComparison = ReplayComparison(baseline = baseline, candidate = baseline)
        assertTrue(
            "self-comparison must be empty, was ${selfComparison.diff()}",
            selfComparison.diff().isEmpty()
        )

        // A deliberately altered candidate IS reported, so the seam can actually detect a change.
        val altered = baseline.map { outcome ->
            if (outcome.fixtureId == "sameEntitiesDifferentRoles") {
                outcome.copy(retrievedIds = outcome.retrievedIds.reversed())
            } else {
                outcome
            }
        }
        val diff = ReplayComparison(baseline = baseline, candidate = altered).diff()
        assertEquals(1, diff.size)
        assertTrue(diff.single().startsWith("sameEntitiesDifferentRoles:"))
    }

    // --- Helpers -------------------------------------------------------------------------------

    /** The formatter block the CURRENT production logic renders for [fixture]. */
    private fun blockFor(fixture: ReplayFixture): String {
        val retrieved = TaskEpisodeRetrieval.rankRelevantEpisodes(
            query = fixture.query,
            episodes = fixture.episodes,
            topK = topK
        )
        return TaskPreflightFormatter.buildPreflightBlock(
            episodes = retrieved,
            currentTaskContext = fixture.currentTaskContext,
            useEnglish = true
        )
    }

    private companion object {
        /**
         * The English function words [TaskEpisodeRetrieval] removes before scoring. Duplicated here
         * only so the limitation test can show which tokens actually carried the match; the
         * production list is private and is not modified.
         */
        val ENGLISH_FUNCTION_WORDS = setOf(
            "a", "an", "the", "and", "or", "but", "if", "then", "else", "of", "to", "in", "on", "at",
            "by", "for", "with", "from", "as", "is", "are", "was", "were", "be", "been", "being",
            "it", "its", "this", "that", "these", "those", "i", "you", "we", "they", "he", "she",
            "my", "your", "our", "their", "do", "does", "did", "done", "have", "has", "had", "will",
            "would", "can", "could", "should", "shall", "may", "might", "must", "not", "no", "so",
            "such", "than", "too", "very", "just", "also", "into", "onto", "over", "under", "about",
            "after", "before", "when", "while", "where", "which", "who", "whom", "what", "how",
            "all", "any", "both", "each", "few", "more", "most", "other", "some", "only", "own",
            "same", "up", "down", "out", "off", "again", "further", "once", "here", "there", "why",
            "s", "t", "re", "ve", "ll", "d", "m"
        )
    }
}
