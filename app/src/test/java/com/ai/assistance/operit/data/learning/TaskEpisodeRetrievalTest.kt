package com.ai.assistance.operit.data.learning

import com.ai.assistance.operit.data.model.TaskEpisode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Ignore
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
    fun rankRelevantEpisodes_returnsOnlyOverlappingEpisodesOrderedByRelevance() {
        // Criterion 2: only episodes with token overlap are returned, ordered by relevance.
        val strong = episode(id = 1L, taskSummary = "signing failed on in-place apk update")
        val weak = episode(id = 2L, taskSummary = "signing")
        val unrelated = episode(id = 3L, taskSummary = "unrelated database migration")

        val ranked = TaskEpisodeRetrieval.rankRelevantEpisodes(
            query = "signing failed in-place apk update",
            episodes = listOf(weak, unrelated, strong),
            topK = 3
        )

        // Unrelated episode (no overlap) is excluded.
        assertFalse(ranked.contains(unrelated))
        // Stronger overlap ranks first.
        assertEquals(listOf(strong, weak), ranked)
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

    // Requires the C03 Unicode-aware tokenizer; enabled with C03.
    @Ignore("Requires the C03 Unicode-aware tokenizer")
    @Test
    fun isPrerequisiteApplicable_chineseUpdateVersusFreshInstall_behavesTheSame() {
        // C02: the Chinese case mirrors the English update-vs-fresh-install case. It cannot pass
        // with the current `[^a-z0-9]+` tokenizer, which does not split CJK text at all (the whole
        // Chinese string becomes one token), so it is ignored until C03 lands the Unicode tokenizer.
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
