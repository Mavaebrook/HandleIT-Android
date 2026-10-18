package com.ai.assistance.operit.api.chat.library

import com.ai.assistance.operit.data.model.TaskEpisode
import com.ai.assistance.operit.data.repository.EpisodeStore
import com.ai.assistance.operit.data.repository.TaskEpisodeRepository
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Regression tests for the episode vector-indexing path of the review scheduler.
 *
 * [ReviewScheduler] needs an Android `Context` and [com.ai.assistance.operit.data.repository
 * .PendingReviewRepository] needs a real ObjectBox store, and this project has no Robolectric, so
 * the whole scheduler cannot be instantiated here. These tests therefore cover the two pieces that
 * carry the rule:
 *  - [TaskEpisodeRepository.episodeIndexText], the pure text builder that is embedded;
 *  - [ReviewScheduler.saveEpisodeAndIndex], the extracted embedding-failure rule, exercised through
 *    a fake [EpisodeStore].
 *
 * NOTE ON THE "LOGGED AT ERROR LEVEL" HALF OF THE RULE: the production `AppLogger.e(...)` call site
 * for an embedding failure is the *private instance* helper
 * `ReviewScheduler.saveEpisodeAndIndex(episodeStore, episode, reviewId)` (the 3-arg one), which
 * delegates to the companion with a lambda that logs
 * `"episode saved but vector indexing failed: episodeId=$episodeId, reviewId=$reviewId"`.
 * That private helper is not reachable from a JVM test without an ObjectBox store and an Android
 * `Context`, so the log call itself is verified by inspection plus CI compilation, NOT by a test
 * here. What IS reachable — and what the production lambda actually consumes — is the
 * `onIndexFailure(savedId, error)` callback, so that is what these tests assert. A test that
 * supplied its own logging lambda and then verified that lambda's own `AppLogger.e` call would be
 * vacuous (it would prove nothing about production code), so no such test exists.
 */
class ReviewSchedulerIndexingTest {

    private var previousSystemLogEnabled = true

    @Before
    fun disableAndroidSystemLogForJvmTests() {
        previousSystemLogEnabled = AppLogger.enableSystemLog
        AppLogger.enableSystemLog = false
    }

    @After
    fun restoreAndroidSystemLog() {
        AppLogger.enableSystemLog = previousSystemLogEnabled
    }

    // --- (a) the documented index text ---

    @Test
    fun episodeIndexText_isTaskContextAndResolutionJoinedBySpaces() {
        val episode =
            TaskEpisode(
                taskSummary = "rename the build variant",
                contextSummary = "gradle project with two flavors",
                resolutionSummary = "updated the flavor dimension",
                failureSummary = "must not appear",
                causeSummary = "must not appear either"
            )

        val text = TaskEpisodeRepository.episodeIndexText(episode)

        assertEquals(
            "rename the build variant gradle project with two flavors updated the flavor dimension",
            text
        )
        // Failure/cause text is deliberately excluded so a past failure does not pull unrelated
        // failing tasks towards it.
        assertTrue(!text.contains("must not appear"))
    }

    // --- (b) the embedding-failure rule ---

    @Test
    fun embeddingFailure_keepsEpisodeSavedAndReportsFailureWithoutPropagating() = runTest {
        val episode = TaskEpisode(taskSummary = "task", contextSummary = "context", resolutionSummary = "resolution")
        val store = FakeEpisodeStore(savedId = 42L, indexFailure = IllegalStateException("embedding down"))
        val reported = mutableListOf<Pair<Long, Exception>>()

        val returnedId =
            ReviewScheduler.saveEpisodeAndIndex(store, episode, reviewId = 7L) { id, error ->
                reported.add(id to error)
            }

        // The episode stays saved: save happened exactly once and its id is returned.
        assertEquals(1, store.saveCalls)
        assertEquals(42L, returnedId)
        // The failure was reported with the episode id, and did not propagate.
        assertEquals(1, reported.size)
        assertEquals(42L, reported.single().first)
        assertTrue(reported.single().second is IllegalStateException)
        // Indexing was attempted exactly once for the saved id.
        assertEquals(listOf(42L), store.indexedIds)
    }

    @Test
    fun embeddingFailure_reportsSavedEpisodeIdToCallback() = runTest {
        // This is the reachable half of "the failure is logged at error level WITH the episode id":
        // the production lambda in the private instance helper builds its log message from exactly
        // these two callback arguments (`episodeId` and `error`), so asserting that the callback
        // receives the *saved* id (not the review id, not the pre-save id) is the meaningful,
        // non-vacuous assertion available at this seam. The `AppLogger.e` call itself lives in the
        // unreachable private helper and is covered by inspection + CI compilation.
        val episode = TaskEpisode(taskSummary = "task", contextSummary = "context", resolutionSummary = "resolution")
        val store = FakeEpisodeStore(savedId = 99L, indexFailure = IllegalStateException("embedding down"))
        val reported = mutableListOf<Pair<Long, Exception>>()

        val returnedId =
            ReviewScheduler.saveEpisodeAndIndex(store, episode, reviewId = 3L) { id, error ->
                reported.add(id to error)
            }

        // The id handed to the callback is the id returned by save(), i.e. the id that ends up in
        // the production message "episodeId=$episodeId".
        assertEquals(99L, returnedId)
        assertEquals(1, store.saveCalls)
        assertEquals(1, reported.size)
        assertEquals(99L, reported.single().first)
        assertTrue(reported.single().second is IllegalStateException)
        assertEquals(listOf(99L), store.indexedIds)
    }

    @Test
    fun disabledEmbeddingProvider_savesEpisodeAndReportsNoFailure() = runTest {
        // The documented disabled state of TaskEpisodeVectorIndex.indexEpisode is a no-op that
        // returns normally, so the fake simply returns without throwing.
        //
        // The companion reports failures ONLY through `onIndexFailure`; the production lambda is
        // what calls `AppLogger.e`. The companion never touches AppLogger itself, so "no error was
        // logged" is exactly equivalent to "the callback was never invoked" — which is what is
        // asserted here. Mocking AppLogger would prove nothing, because the companion cannot call
        // it.
        val episode = TaskEpisode(taskSummary = "task", contextSummary = "context", resolutionSummary = "resolution")
        val store = FakeEpisodeStore(savedId = 5L, indexFailure = null)
        val reported = mutableListOf<Pair<Long, Exception>>()

        val returnedId =
            ReviewScheduler.saveEpisodeAndIndex(store, episode, reviewId = 1L) { id, error ->
                reported.add(id to error)
            }

        assertEquals(5L, returnedId)
        assertEquals(1, store.saveCalls)
        assertEquals(listOf(5L), store.indexedIds)
        // No failure reported => the production lambda never ran => nothing was logged as an error.
        assertTrue(reported.isEmpty())
    }

    @Test
    fun cancellationDuringIndexing_propagates() = runTest {
        val episode = TaskEpisode(taskSummary = "task", contextSummary = "context", resolutionSummary = "resolution")
        val store = FakeEpisodeStore(savedId = 11L, indexFailure = CancellationException("cancelled"))
        val reported = mutableListOf<Pair<Long, Exception>>()

        var propagated: Throwable? = null
        try {
            ReviewScheduler.saveEpisodeAndIndex(store, episode, reviewId = 2L) { id, error ->
                reported.add(id to error)
            }
        } catch (error: Throwable) {
            propagated = error
        }

        // Cancellation is rethrown so structured concurrency is preserved, and it is NOT reported
        // as an embedding failure.
        assertNotNull(propagated)
        assertTrue(propagated is CancellationException)
        assertTrue(reported.isEmpty())
        // The episode was still saved before indexing was attempted.
        assertEquals(1, store.saveCalls)
    }

    /**
     * Fake [EpisodeStore] recording calls. [indexFailure] is thrown from [indexSavedEpisode] when
     * non-null; a null value represents the documented disabled state (a no-op that returns).
     */
    private class FakeEpisodeStore(
        private val savedId: Long,
        private val indexFailure: Exception?
    ) : EpisodeStore {
        var saveCalls: Int = 0
            private set
        val indexedIds: MutableList<Long> = mutableListOf()

        override fun save(episode: TaskEpisode): Long {
            saveCalls += 1
            return savedId
        }

        override suspend fun indexSavedEpisode(episodeId: Long, episode: TaskEpisode) {
            indexedIds.add(episodeId)
            val failure = indexFailure
            if (failure != null) {
                throw failure
            }
        }
    }
}
