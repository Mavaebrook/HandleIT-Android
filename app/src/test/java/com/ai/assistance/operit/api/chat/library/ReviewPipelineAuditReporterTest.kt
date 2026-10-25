package com.ai.assistance.operit.api.chat.library

import com.ai.assistance.operit.data.model.PendingReviewEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM-only tests for [ReviewPipelineAuditReporter]'s pure logic.
 *
 * [ReviewPipelineAuditReporter.collect] is intentionally not covered here: like
 * `PendingReviewRepository` it needs a real ObjectBox store plus an Android `Context`, which
 * cannot be instantiated in the JVM test source set (the same reason `ReviewSchedulerBatchTest`
 * tests the pure `PendingReviewBatchPolicy` instead of the scheduler loop). Everything `collect`
 * does that is Android/ObjectBox-free lives in the pure helpers and renderer asserted below.
 */
class ReviewPipelineAuditReporterTest {

    private fun row(status: String, attemptCount: Int): PendingReviewEntity =
        PendingReviewEntity(status = status, attemptCount = attemptCount)

    private fun snapshot(
        storePresent: Boolean = true,
        schedulerConstructed: Boolean? = true,
        pendingByStatus: Map<String, Int>? = emptyMap(),
        maxAttemptCountObserved: Int? = null,
        taskEpisodeCount: Long? = 0L
    ): ReviewPipelineAuditReporter.ReviewAuditSnapshot =
        ReviewPipelineAuditReporter.ReviewAuditSnapshot(
            observedAtEpochMillis = 1_726_000_000_000L,
            processScope = 4242,
            activeProfileId = "default",
            expectedStoreDir = "/data/user/0/com.ai.assistance.operit.debug/files/objectbox",
            storePresent = storePresent,
            schedulerConstructed = schedulerConstructed,
            pendingByStatus = pendingByStatus,
            maxAttemptCountObserved = maxAttemptCountObserved,
            taskEpisodeCount = taskEpisodeCount
        )

    @Test
    fun expectedStoreDirName_defaultProfile_isUnsuffixed() {
        assertEquals("objectbox", ReviewPipelineAuditReporter.expectedStoreDirName("default"))
    }

    @Test
    fun expectedStoreDirName_customProfile_isSuffixed() {
        assertEquals("objectbox_memory_123", ReviewPipelineAuditReporter.expectedStoreDirName("memory_123"))
    }

    @Test
    fun groupByStatus_countsEachStatus() {
        val rows = listOf(
            row(PendingReviewEntity.STATUS_PENDING, 1),
            row(PendingReviewEntity.STATUS_PENDING, 2),
            row(PendingReviewEntity.STATUS_FAILED, 1),
            row(PendingReviewEntity.STATUS_DEAD_LETTER, 1)
        )
        assertEquals(
            mapOf("pending" to 2, "failed" to 1, "dead_letter" to 1),
            ReviewPipelineAuditReporter.groupByStatus(rows)
        )
    }

    @Test
    fun groupByStatus_emptyRows_isEmptyMapNotNull() {
        assertTrue(ReviewPipelineAuditReporter.groupByStatus(emptyList()).isEmpty())
    }

    @Test
    fun maxAttemptCount_usesLargestAttemptCount() {
        val rows = listOf(row("pending", 1), row("failed", 3), row("pending", 2))
        assertEquals(3, ReviewPipelineAuditReporter.maxAttemptCount(rows))
    }

    @Test
    fun maxAttemptCount_noRows_isNull() {
        assertNull(ReviewPipelineAuditReporter.maxAttemptCount(emptyList()))
    }

    @Test
    fun toReportText_storeAbsent_rendersUnavailableForStoreBackedCounts() {
        val text = snapshot(storePresent = false).toReportText()
        assertTrue(text.contains("storePresent=false"))
        assertTrue(text.contains("pendingByStatus=UNAVAILABLE (store absent)"))
        assertTrue(text.contains("maxAttemptCountObserved=UNAVAILABLE (store absent)"))
        assertTrue(text.contains("taskEpisodeCount=UNAVAILABLE (store absent)"))
        assertFalse(text.contains("pendingByStatus={}"))
        assertFalse(text.contains("taskEpisodeCount=0"))
    }

    @Test
    fun toReportText_storePresentWithEmptyStore_rendersObservedZeroDistinctFromUnavailable() {
        val text = snapshot().toReportText()
        assertTrue(text.contains("storePresent=true"))
        assertTrue(text.contains("pendingByStatus={}"))
        assertTrue(text.contains("maxAttemptCountObserved=none (no review rows observed)"))
        assertTrue(text.contains("taskEpisodeCount=0"))
        assertFalse(text.contains("UNAVAILABLE"))
    }

    @Test
    fun toReportText_storePresentWithNullCounts_rendersNotObserved() {
        val text = snapshot(
            pendingByStatus = null,
            maxAttemptCountObserved = null,
            taskEpisodeCount = null
        ).toReportText()
        assertTrue(text.contains("pendingByStatus=not observed"))
        assertTrue(text.contains("maxAttemptCountObserved=not observed"))
        assertTrue(text.contains("taskEpisodeCount=not observed"))
        assertFalse(text.contains("UNAVAILABLE"))
    }

    @Test
    fun toReportText_includesObservedAtPidProfileAndStoreDir() {
        val text = snapshot().toReportText()
        assertTrue(text.contains("observedAtEpochMillis=1726000000000"))
        assertTrue(text.contains("processScope=pid=4242"))
        assertTrue(text.contains("activeProfileId=default"))
        assertTrue(text.contains("expectedStoreDir=/data/user/0/com.ai.assistance.operit.debug/files/objectbox"))
    }

    @Test
    fun toReportText_schedulerUnknown_rendersNotObserved() {
        val text = snapshot(schedulerConstructed = null).toReportText()
        assertTrue(text.contains("schedulerConstructed=not observed"))
    }
}