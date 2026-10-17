package com.ai.assistance.operit.api.chat.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A03 regression tests: [ReviewScheduler] must drain every memory space that has pending reviews,
 * not just the hardcoded "default" space.
 *
 * [ReviewScheduler] itself depends on Android `Context` and ObjectBox, so it cannot be instantiated
 * in a plain JVM test. These tests therefore cover the pure space-selection core
 * [ReviewScheduler.memorySpacesToDrain], which is the part that used to be hardcoded to "default".
 */
class ReviewSchedulerMemorySpaceTest {

    // --- A03: every configured space is a drain candidate ---

    @Test
    fun memorySpacesToDrain_returnsAllConfiguredSpaces() {
        // A03: all configured memory spaces must be drained, in order.
        val spaces = ReviewScheduler.memorySpacesToDrain(listOf("default", "work", "personal"))

        assertEquals(listOf("default", "work", "personal"), spaces)
    }

    @Test
    fun memorySpacesToDrain_includesNonDefaultSpace() {
        // A03 regression guard: the old implementation hardcoded profileId = "default", so reviews
        // enqueued into any other space were never drained. A non-default space must be included.
        val spaces = ReviewScheduler.memorySpacesToDrain(listOf("default", "work"))

        assertTrue(spaces.contains("work"))
    }

    @Test
    fun memorySpacesToDrain_dropsBlankAndDuplicates() {
        // Blank ids cannot address a space and duplicates would drain the same queue twice.
        val spaces =
            ReviewScheduler.memorySpacesToDrain(listOf("default", "", "  ", "default", "work"))

        assertEquals(listOf("default", "work"), spaces)
    }

    @Test
    fun memorySpacesToDrain_emptyList_returnsEmpty() {
        // No configured spaces -> nothing to drain.
        val spaces = ReviewScheduler.memorySpacesToDrain(emptyList())

        assertEquals(emptyList<String>(), spaces)
    }

    // --- A03: enqueue-space == drain-space invariant ---

    @Test
    fun reviewEnqueuedInNonDefaultSpace_isDrainedInThatSpace() {
        // The enqueuer resolves its space via
        // `memorySpaceIdOverride ?: preferencesManager.activeMemorySpaceIdFlow.first()`, so a review
        // can land in any space. The scheduler drains every space in `memorySpaceListFlow`, so a
        // review enqueued into "work" is drained in "work".
        val profileId = "work"
        val configuredSpaces = listOf("default", "work")

        assertTrue(ReviewScheduler.memorySpacesToDrain(configuredSpaces).contains(profileId))

        // The resulting episode is saved via `TaskEpisodeRepository(context, profileId)` using the
        // SAME profileId the review was read from, so the episode lands in the enqueuing space.
        // A full ObjectBox round-trip (enqueue in "work" -> drain -> episode stored in "work") is
        // not possible in a plain JVM test; that path is covered by the device/CI integration check
        // against the ObjectBox-backed repositories.
    }
}
