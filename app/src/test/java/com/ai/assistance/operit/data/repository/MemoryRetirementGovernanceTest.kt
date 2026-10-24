package com.ai.assistance.operit.data.repository

import com.ai.assistance.operit.data.repository.MemoryRetirementGovernance.RestoreDecision
import com.ai.assistance.operit.data.repository.MemoryRetirementGovernance.RestoreRefusalReason
import com.ai.assistance.operit.data.repository.MemoryRetirementGovernance.RetirementCandidate
import com.ai.assistance.operit.data.repository.MemoryRetirementGovernance.RetirementDecision
import com.ai.assistance.operit.data.repository.MemoryRetirementGovernance.RetirementReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM regression tests for [MemoryRetirementGovernance].
 *
 * These cover the retirement governance gate: model output cannot grant permission, the policy
 * only ever reduces the set of retirements, and insufficient evidence yields a pending decision
 * rather than a default retirement.
 */
class MemoryRetirementGovernanceTest {

    private fun candidate(
        sourceId: Long = 1L,
        sourceTitle: String = "New fact",
        sourceCreatedAtMs: Long = 2000L,
        targetId: Long = 2L,
        targetTitle: String = "Old fact",
        targetCreatedAtMs: Long = 1000L,
        targetStatus: String = "ACTIVE",
        linkType: String = "CORRECTS",
        targetTitleMatchCount: Int = 1,
        targetProtectedByActiveUserConstraint: Boolean = false,
        correctionApplicable: Boolean = true
    ): RetirementCandidate {
        return RetirementCandidate(
            sourceId = sourceId,
            sourceTitle = sourceTitle,
            sourceCreatedAtMs = sourceCreatedAtMs,
            targetId = targetId,
            targetTitle = targetTitle,
            targetCreatedAtMs = targetCreatedAtMs,
            targetStatus = targetStatus,
            linkType = linkType,
            targetTitleMatchCount = targetTitleMatchCount,
            targetProtectedByActiveUserConstraint = targetProtectedByActiveUserConstraint,
            correctionApplicable = correctionApplicable
        )
    }

    // Covers: a justified CORRECTS link (older target, unique title, explicit evidence) retires.
    @Test
    fun justifiedCorrection_retires() {
        val decision = MemoryRetirementGovernance.decide(candidate())

        assertEquals(RetirementDecision.Retire(RetirementReason.ACCEPTED), decision)
        assertTrue(MemoryRetirementGovernance.isRetirement(decision))
    }

    // Covers: a target newer than the source means the direction is reversed; do not retire.
    @Test
    fun reversedLink_doesNotRetire() {
        val decision = MemoryRetirementGovernance.decide(candidate(targetCreatedAtMs = 3000L))

        assertEquals(
            RetirementDecision.LeaveActive(RetirementReason.TARGET_NEWER_THAN_SOURCE),
            decision
        )
        assertFalse(MemoryRetirementGovernance.isRetirement(decision))
    }

    // Covers: equal timestamps give no ordering evidence, so direction cannot be established.
    @Test
    fun equalTimestamps_doesNotRetire() {
        val decision = MemoryRetirementGovernance.decide(candidate(targetCreatedAtMs = 2000L))

        assertEquals(RetirementDecision.LeaveActive(RetirementReason.REVERSED_DIRECTION), decision)
        assertFalse(MemoryRetirementGovernance.isRetirement(decision))
    }

    // Covers: more than one memory shares the target title, so the target is ambiguous.
    @Test
    fun ambiguousTarget_doesNotRetire() {
        val decision = MemoryRetirementGovernance.decide(candidate(targetTitleMatchCount = 2))

        assertEquals(
            RetirementDecision.PendingDecision(RetirementReason.AMBIGUOUS_TARGET),
            decision
        )
        assertFalse(MemoryRetirementGovernance.isRetirement(decision))
    }

    // Covers: no memory matches the target title, so the target cannot be identified at all.
    @Test
    fun missingTarget_doesNotRetire() {
        val decision = MemoryRetirementGovernance.decide(candidate(targetTitleMatchCount = 0))

        assertEquals(
            RetirementDecision.PendingDecision(RetirementReason.AMBIGUOUS_TARGET),
            decision
        )
        assertFalse(MemoryRetirementGovernance.isRetirement(decision))
    }

    // Covers: a memory can never retire itself (same id).
    @Test
    fun selfLink_doesNotRetire() {
        val decision = MemoryRetirementGovernance.decide(candidate(sourceId = 1L, targetId = 1L))

        assertEquals(RetirementDecision.LeaveActive(RetirementReason.SELF_LINK), decision)
        assertFalse(MemoryRetirementGovernance.isRetirement(decision))
    }

    // Covers: a live user constraint outranks any model-proposed retirement.
    @Test
    fun protectedUserConstraint_doesNotRetire() {
        val decision =
            MemoryRetirementGovernance.decide(
                candidate(targetProtectedByActiveUserConstraint = true)
            )

        assertEquals(
            RetirementDecision.LeaveActive(RetirementReason.PROTECTED_USER_CONSTRAINT),
            decision
        )
        assertFalse(MemoryRetirementGovernance.isRetirement(decision))
    }

    // Covers: only CORRECTS/SUPERSEDES can justify a retirement; other link types cannot.
    @Test
    fun unsupportedLinkType_doesNotRetire() {
        val decision = MemoryRetirementGovernance.decide(candidate(linkType = "RELATES_TO"))

        assertEquals(
            RetirementDecision.LeaveActive(RetirementReason.NO_APPLICABLE_CORRECTION),
            decision
        )
        assertFalse(MemoryRetirementGovernance.isRetirement(decision))
    }

    // Covers: a timestamp alone is not proof of contradiction; without explicit evidence the
    // decision is deferred rather than defaulting to retirement.
    @Test
    fun missingEvidence_returnsPendingDecision() {
        val decision = MemoryRetirementGovernance.decide(candidate(correctionApplicable = false))

        assertEquals(
            RetirementDecision.PendingDecision(RetirementReason.INSUFFICIENT_EVIDENCE),
            decision
        )
        assertFalse(MemoryRetirementGovernance.isRetirement(decision))
    }

    // Covers: SUPERSEDES is a supported link type and retires when otherwise justified.
    @Test
    fun supersedesLink_retiresWhenJustified() {
        val decision = MemoryRetirementGovernance.decide(candidate(linkType = "SUPERSEDES"))

        assertEquals(RetirementDecision.Retire(RetirementReason.ACCEPTED), decision)
        assertTrue(MemoryRetirementGovernance.isRetirement(decision))
    }

    // Covers: the core invariant — every non-accepted reason leaves the memory active or pending,
    // and ACCEPTED is the only reason that ever produces a retirement.
    @Test
    fun onlyAcceptedReason_retires() {
        val nonAcceptedCandidates =
            listOf(
                candidate(sourceId = 1L, targetId = 1L), // SELF_LINK
                candidate(targetStatus = "INACTIVE"), // ALREADY_INACTIVE
                candidate(targetTitleMatchCount = 2), // AMBIGUOUS_TARGET
                candidate(targetProtectedByActiveUserConstraint = true), // PROTECTED_USER_CONSTRAINT
                candidate(targetCreatedAtMs = 3000L), // TARGET_NEWER_THAN_SOURCE
                candidate(targetCreatedAtMs = 2000L), // REVERSED_DIRECTION
                candidate(linkType = "RELATES_TO"), // NO_APPLICABLE_CORRECTION
                candidate(correctionApplicable = false) // INSUFFICIENT_EVIDENCE
            )

        nonAcceptedCandidates.forEach { candidate ->
            val decision = MemoryRetirementGovernance.decide(candidate)
            assertFalse(
                "Only ACCEPTED may retire, but got $decision for $candidate",
                MemoryRetirementGovernance.isRetirement(decision)
            )
        }

        val accepted = MemoryRetirementGovernance.decide(candidate())
        assertEquals(RetirementDecision.Retire(RetirementReason.ACCEPTED), accepted)
        assertTrue(MemoryRetirementGovernance.isRetirement(accepted))
    }

    // Covers: reasonCode is a stable, storage-safe string derived from the enum name.
    @Test
    fun reasonCode_isStable() {
        assertEquals(
            "ACCEPTED",
            MemoryRetirementGovernance.reasonCode(
                RetirementDecision.Retire(RetirementReason.ACCEPTED)
            )
        )
        assertEquals(
            "AMBIGUOUS_TARGET",
            MemoryRetirementGovernance.reasonCode(
                RetirementDecision.PendingDecision(RetirementReason.AMBIGUOUS_TARGET)
            )
        )
    }

    // Covers: an already-inactive target must not be retired again, because re-retiring would
    // overwrite the recorded prior status and corrupt the audit chain.
    @Test
    fun alreadyInactiveTarget_doesNotRetire() {
        val decision = MemoryRetirementGovernance.decide(candidate(targetStatus = "INACTIVE"))

        assertEquals(
            RetirementDecision.LeaveActive(RetirementReason.ALREADY_INACTIVE),
            decision
        )
        assertFalse(MemoryRetirementGovernance.isRetirement(decision))
    }

    // Covers: the already-inactive guard takes precedence over the ambiguity check, so an
    // inactive target is reported as ALREADY_INACTIVE rather than AMBIGUOUS_TARGET.
    @Test
    fun alreadyInactiveTarget_precedesAmbiguityCheck() {
        val decision =
            MemoryRetirementGovernance.decide(
                candidate(targetStatus = "INACTIVE", targetTitleMatchCount = 2)
            )

        assertEquals(
            RetirementDecision.LeaveActive(RetirementReason.ALREADY_INACTIVE),
            decision
        )
    }

    // ---------------------------------------------------------------------------------------
    // Restore eligibility (evaluateRestore)
    //
    // Pure decision coverage. MemoryMetadataStoreTest separately covers native ObjectBox
    // persistence, same-status invalidation and transaction rollback.
    // ---------------------------------------------------------------------------------------

    // Covers: nothing was ever retired, so there is nothing to restore.
    @Test
    fun restore_noRecordedPrevStatus_refusesNotRetired() {
        assertEquals(
            RestoreDecision.Refuse(RestoreRefusalReason.NOT_RETIRED),
            MemoryRetirementGovernance.evaluateRestore(
                currentStatus = "INACTIVE",
                recordedPrevStatus = null,
                recordedRetiredAt = "1000",
                recordedReason = "ACCEPTED",
                recordedLinkType = "CORRECTS",
                recordedSourceId = "1"
            )
        )
        assertEquals(
            RestoreDecision.Refuse(RestoreRefusalReason.NOT_RETIRED),
            MemoryRetirementGovernance.evaluateRestore(
                currentStatus = "INACTIVE",
                recordedPrevStatus = "   ",
                recordedRetiredAt = "1000",
                recordedReason = "ACCEPTED",
                recordedLinkType = "CORRECTS",
                recordedSourceId = "1"
            )
        )
    }

    // Covers: the "never silently reactivate" guard — another operation changed the status away
    // from INACTIVE, so restoring would clobber that deliberate change.
    @Test
    fun restore_statusChangedByAnotherOperation_refusesNotInactive() {
        assertEquals(
            RestoreDecision.Refuse(RestoreRefusalReason.NOT_INACTIVE),
            MemoryRetirementGovernance.evaluateRestore(
                currentStatus = "ACTIVE",
                recordedPrevStatus = "ACTIVE",
                recordedRetiredAt = "1000",
                recordedReason = "ACCEPTED",
                recordedLinkType = "CORRECTS",
                recordedSourceId = "1"
            )
        )
    }

    // Covers: a prior status was recorded but the retirement timestamp is missing, so the audit is
    // incomplete and we cannot prove the retirement actually happened.
    @Test
    fun restore_missingRetiredAt_refusesMissingProvenance() {
        assertEquals(
            RestoreDecision.Refuse(RestoreRefusalReason.MISSING_PROVENANCE),
            MemoryRetirementGovernance.evaluateRestore(
                currentStatus = "INACTIVE",
                recordedPrevStatus = "ACTIVE",
                recordedRetiredAt = null,
                recordedReason = "ACCEPTED",
                recordedLinkType = "CORRECTS",
                recordedSourceId = "1"
            )
        )
        assertEquals(
            RestoreDecision.Refuse(RestoreRefusalReason.MISSING_PROVENANCE),
            MemoryRetirementGovernance.evaluateRestore(
                currentStatus = "INACTIVE",
                recordedPrevStatus = "ACTIVE",
                recordedRetiredAt = "  ",
                recordedReason = "ACCEPTED",
                recordedLinkType = "CORRECTS",
                recordedSourceId = "1"
            )
        )
    }

    // Covers: the happy path — still INACTIVE with a complete audit restores the recorded status.
    @Test
    fun restore_completeAudit_restoresRecordedStatus() {
        assertEquals(
            RestoreDecision.Restore("ACTIVE"),
            MemoryRetirementGovernance.evaluateRestore(
                currentStatus = "INACTIVE",
                recordedPrevStatus = "ACTIVE",
                recordedRetiredAt = "1000",
                recordedReason = "ACCEPTED",
                recordedLinkType = "CORRECTS",
                recordedSourceId = "1"
            )
        )
    }

    // Covers: the recorded prior status is returned verbatim, never normalised or defaulted.
    @Test
    fun restore_nonActivePriorStatus_returnsExactRecordedValue() {
        assertEquals(
            RestoreDecision.Restore("INACTIVE"),
            MemoryRetirementGovernance.evaluateRestore(
                currentStatus = "INACTIVE",
                recordedPrevStatus = "INACTIVE",
                recordedRetiredAt = "1000",
                recordedReason = "ACCEPTED",
                recordedLinkType = "CORRECTS",
                recordedSourceId = "1"
            )
        )
    }
}
