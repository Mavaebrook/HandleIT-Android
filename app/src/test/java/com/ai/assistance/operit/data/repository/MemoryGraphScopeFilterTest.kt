package com.ai.assistance.operit.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM regression tests for [MemoryGraphScopeFilter].
 *
 * These cover the Stage B acceptance criteria: inactive/out-of-scope memories must not be
 * resurrected into normal active retrieval through graph links (outgoing or backlink) or
 * through the final result path, while in-scope active memories are preserved in order.
 */
class MemoryGraphScopeFilterTest {

    // Covers: isAllowed accepts ids that are in scope AND active, rejects everything else.
    @Test
    fun isAllowed_returnsTrueForAllowedId_andFalseOtherwise() {
        val allowed = setOf(1L, 2L, 3L)

        assertTrue(MemoryGraphScopeFilter.isAllowed(allowed, 1L))
        assertTrue(MemoryGraphScopeFilter.isAllowed(allowed, 3L))
        assertFalse(MemoryGraphScopeFilter.isAllowed(allowed, 99L))
        assertFalse(MemoryGraphScopeFilter.isAllowed(emptySet(), 1L))
    }

    // Covers: outgoing-link resurrection regression — an inactive/out-of-scope target id
    // (not in the allowed set) must not receive propagated score.
    @Test
    fun shouldPropagateTo_returnsFalseForInactiveOrOutOfScopeOutgoingTarget() {
        val allowed = setOf(1L, 2L)

        // 99 is a superseded/inactive memory reachable via an outgoing link.
        assertFalse(MemoryGraphScopeFilter.shouldPropagateTo(allowed, 99L))
    }

    // Covers: backlink-direction resurrection regression — a backlink source id not in the
    // allowed set must not receive propagated score.
    @Test
    fun shouldPropagateTo_returnsFalseForOutOfScopeBacklinkSource() {
        val allowed = setOf(1L, 2L)

        // 100 is an out-of-scope memory that links back to an in-scope seed.
        assertFalse(MemoryGraphScopeFilter.shouldPropagateTo(allowed, 100L))
    }

    // Covers: in-scope active targets still receive propagated score (no over-filtering).
    @Test
    fun shouldPropagateTo_returnsTrueForInScopeActiveTarget() {
        val allowed = setOf(1L, 2L, 3L)

        assertTrue(MemoryGraphScopeFilter.shouldPropagateTo(allowed, 2L))
    }

    // Covers: final result path removes out-of-scope/inactive ids while preserving the
    // relative order of the remaining ids.
    @Test
    fun filterAllowedIds_removesDisallowedIds_andPreservesOrder() {
        val allowed = setOf(1L, 2L, 3L)
        val ranked = listOf(3L, 99L, 1L, 100L, 2L)

        assertEquals(listOf(3L, 1L, 2L), MemoryGraphScopeFilter.filterAllowedIds(allowed, ranked))
    }

    // Covers: scope isolation — an empty allowed set yields no results at all.
    @Test
    fun filterAllowedIds_withEmptyAllowedSet_returnsEmptyList() {
        val ranked = listOf(1L, 2L, 3L)

        assertTrue(MemoryGraphScopeFilter.filterAllowedIds(emptySet(), ranked).isEmpty())
    }

    // Covers: SUPERSEDES chain — superseded/out-of-scope ids (99, 100) are dropped even when
    // interleaved with allowed ids (1, 2).
    @Test
    fun filterAllowedIds_supersedesChain_dropsSupersededIds() {
        val allowed = setOf(1L, 2L)
        val ranked = listOf(1L, 99L, 2L, 100L)

        assertEquals(listOf(1L, 2L), MemoryGraphScopeFilter.filterAllowedIds(allowed, ranked))
    }

    // Covers: scope boundary — a memory from another folder (20) is removed even though it
    // is active, because it is outside the query's folder scope.
    @Test
    fun filterAllowedIds_scopeBoundary_removesOtherFolderMemory() {
        val allowed = setOf(10L) // folder A
        val ranked = listOf(10L, 20L) // 20 belongs to folder B

        assertEquals(listOf(10L), MemoryGraphScopeFilter.filterAllowedIds(allowed, ranked))
    }
}
