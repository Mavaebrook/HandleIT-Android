package com.ai.assistance.operit.api.chat.library

import com.ai.assistance.operit.data.model.PendingReviewEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * JVM-only tests for [ReviewPipelineAuditReporter]'s pure logic and its
 * [ReviewPipelineAuditReporter.Collector] seam. The seam is what makes the collection pipeline
 * testable without a real ObjectBox store or an Android Context (the same reason
 * ReviewSchedulerBatchTest tests the pure policy instead of the scheduler loop): store existence,
 * short-circuiting, per-segment failure handling and the UNAVAILABLE vs 0 vs not-observed
 * rendering are all exercised with lambdas.
 */
class ReviewPipelineAuditReporterTest {

    private fun row(status: String, attemptCount: Int): PendingReviewEntity =
        PendingReviewEntity(status = status, attemptCount = attemptCount)

    private fun audit(
        started: Long = 0L,
        completed: Long = 0L,
        outcome: String? = null,
        recovered: Int? = null
    ): ReviewPassAudit =
        ReviewPassAudit(
            passesStarted = started,
            passesCompleted = completed,
            lastPassStartedAtEpochMillis = null,
            lastPassCompletedAtEpochMillis = null,
            lastPassOutcome = outcome,
            lastPassPendingFound = null,
            lastPassSucceeded = null,
            lastPassFailedMarked = null,
            startupRecoveryCompletedAtEpochMillis = null,
            startupRecoveryRecovered = recovered
        )

    private fun snapshot(
        storePresent: Boolean = true,
        schedulerConstructed: Boolean? = true,
        schedulerAudit: ReviewPassAudit? = null,
        pendingByStatus: Map<String, Int>? = emptyMap(),
        maxAttemptCountObserved: Int? = null,
        taskEpisodeCount: Long? = 0L,
        readErrors: List<String> = emptyList()
    ): ReviewPipelineAuditReporter.ReviewAuditSnapshot =
        ReviewPipelineAuditReporter.ReviewAuditSnapshot(
            observedAtEpochMillis = 1_726_000_000_000L,
            processScope = 4242,
            activeProfileId = "default",
            expectedStoreDir = "/data/user/0/com.ai.assistance.operit.debug/files/objectbox",
            storePresent = storePresent,
            schedulerConstructed = schedulerConstructed,
            schedulerAudit = schedulerAudit,
            pendingByStatus = pendingByStatus,
            maxAttemptCountObserved = maxAttemptCountObserved,
            taskEpisodeCount = taskEpisodeCount,
            readErrors = readErrors
        )

    /** Test harness around [ReviewPipelineAuditReporter.Collector] with call tracking. */
    private class CollectorProbe(
        val storePresent: Boolean = true,
        val audit: ReviewPassAudit? = null,
        val reviews: () -> List<PendingReviewEntity> = { emptyList() },
        val episodeCount: () -> Long = { 0L },
        val profileId: String = "default"
    ) {
        var reviewsCalled = false
        var episodesCalled = false
        val collector = ReviewPipelineAuditReporter.Collector(
            activeProfileId = { profileId },
            storeDirFor = { id -> File("/tmp/$id") },
            storeDataFilePresent = { storePresent },
            schedulerAudit = { audit },
            readReviews = {
                reviewsCalled = true
                reviews()
            },
            readEpisodeCount = {
                episodesCalled = true
                episodeCount()
            },
            nowMillis = { 1_726_000_000_000L },
            currentPid = { 4242 }
        )
    }

    // ---- pure helpers ----

    @Test
    fun expectedStoreDirName_defaultProfile_isUnsuffixed() {
        assertEquals("objectbox", ReviewPipelineAuditReporter.expectedStoreDirName("default"))
    }

    @Test
    fun expectedStoreDirName_customProfile_isSuffixed() {
        assertEquals("objectbox_memory_123", ReviewPipelineAuditReporter.expectedStoreDirName("memory_123"))
    }

    @Test
    fun groupByStatus_countsEachStatusIncludingProcessing() {
        val rows = listOf(
            row(PendingReviewEntity.STATUS_PENDING, 1),
            row(PendingReviewEntity.STATUS_PENDING, 2),
            row(PendingReviewEntity.STATUS_PROCESSING, 1),
            row(PendingReviewEntity.STATUS_FAILED, 1),
            row(PendingReviewEntity.STATUS_DEAD_LETTER, 1)
        )
        assertEquals(
            mapOf("pending" to 2, "processing" to 1, "failed" to 1, "dead_letter" to 1),
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

    // ---- rendering ----

    @Test
    fun toReportLines_everyLineCarriesThePrefixSoTagFilteredExtractionKeepsAllFields() {
        val lines = snapshot().toReportLines()
        assertTrue(lines.isNotEmpty())
        lines.forEach { line -> assertTrue("line missing prefix: $line", line.startsWith("[ReviewPipelineAudit] ")) }
        assertEquals(lines, snapshot().toReportText().lines())
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
        assertTrue(text.contains("readErrors=none"))
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
    fun toReportText_schedulerUnknown_rendersNotObservedForSchedulerFields() {
        val text = snapshot(schedulerConstructed = null, schedulerAudit = null).toReportText()
        assertTrue(text.contains("schedulerConstructed=not observed"))
        assertTrue(text.contains("schedulerPassCount=not observed"))
        assertTrue(text.contains("schedulerLastPassOutcome=not observed"))
    }

    @Test
    fun toReportText_schedulerAuditPresent_rendersObservedZeros() {
        val text = snapshot(schedulerConstructed = true, schedulerAudit = audit(recovered = 5)).toReportText()
        assertTrue(text.contains("schedulerConstructed=true"))
        assertTrue(text.contains("schedulerPassCount=0"))
        assertTrue(text.contains("schedulerPassesCompleted=0"))
        assertTrue(text.contains("schedulerStartupRecoveryRecovered=5"))
        assertTrue(text.contains("schedulerLastPassOutcome=not observed"))
    }

    @Test
    fun toReportText_readErrors_rendered() {
        val text = snapshot(
            readErrors = listOf("reviewRead: boom", "episodeCount: skipped: review read failed")
        ).toReportText()
        assertTrue(text.contains("readErrors=reviewRead: boom; episodeCount: skipped: review read failed"))
    }

    // ---- collection (Collector seam) ----

    @Test
    fun collector_storeAbsent_shortCircuitsWithoutAnyStoreRead() {
        val probe = CollectorProbe(storePresent = false)
        val snap = runBlocking { probe.collector.collect() }
        assertFalse(snap.storePresent)
        assertFalse(probe.reviewsCalled)
        assertFalse(probe.episodesCalled)
        assertTrue(snap.readErrors.isEmpty())
        assertTrue(snap.toReportText().contains("pendingByStatus=UNAVAILABLE (store absent)"))
    }

    @Test
    fun collector_storePresent_readsOneAllStatusSnapshotIncludingProcessing() {
        val probe = CollectorProbe(
            reviews = {
                listOf(
                    row(PendingReviewEntity.STATUS_PENDING, 1),
                    row(PendingReviewEntity.STATUS_PROCESSING, 1),
                    row(PendingReviewEntity.STATUS_DEAD_LETTER, 2)
                )
            }
        )
        val snap = runBlocking { probe.collector.collect() }
        assertEquals(
            mapOf("pending" to 1, "processing" to 1, "dead_letter" to 1),
            snap.pendingByStatus
        )
        assertEquals(2, snap.maxAttemptCountObserved)
        assertTrue(snap.readErrors.isEmpty())
    }

    @Test
    fun collector_reviewReadFailure_recordsErrorAndSkipsEpisodeCount() {
        val probe = CollectorProbe(reviews = { throw IllegalStateException("boom") })
        val snap = runBlocking { probe.collector.collect() }
        assertNull(snap.pendingByStatus)
        assertNull(snap.taskEpisodeCount)
        assertFalse(probe.episodesCalled)
        assertEquals(
            listOf("reviewRead: boom", "episodeCount: skipped: review read failed"),
            snap.readErrors
        )
        assertTrue(snap.toReportText().contains("pendingByStatus=not observed"))
    }

    @Test
    fun collector_episodeCountFailure_keepsReviewCounts() {
        val probe = CollectorProbe(
            reviews = { listOf(row(PendingReviewEntity.STATUS_PENDING, 1)) },
            episodeCount = { throw RuntimeException("boom") }
        )
        val snap = runBlocking { probe.collector.collect() }
        assertEquals(mapOf("pending" to 1), snap.pendingByStatus)
        assertNull(snap.taskEpisodeCount)
        assertEquals(listOf("episodeCount: boom"), snap.readErrors)
        assertTrue(snap.toReportText().contains("taskEpisodeCount=not observed"))
    }

    @Test
    fun collector_cancellationFromReviewRead_propagates() {
        val probe = CollectorProbe(reviews = { throw CancellationException("stop") })
        try {
            runBlocking { probe.collector.collect() }
            fail("expected CancellationException to propagate")
        } catch (expected: CancellationException) {
            assertEquals("stop", expected.message)
        }
    }

    @Test
    fun collector_schedulerAudit_capturedInSnapshot() {
        val probe = CollectorProbe(
            audit = audit(started = 2, completed = 2, outcome = ReviewPassAuditRecorder.OUTCOME_COMPLETED)
        )
        val snap = runBlocking { probe.collector.collect() }
        assertTrue(snap.schedulerConstructed == true)
        assertEquals(2L, snap.schedulerAudit?.passesStarted)
        val text = snap.toReportText()
        assertTrue(text.contains("schedulerPassCount=2"))
        assertTrue(text.contains("schedulerLastPassOutcome=completed"))
    }
}