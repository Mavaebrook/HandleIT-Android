package com.ai.assistance.operit.data.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM regression tests for Stage D (part 1): pure versioned skill-revision lifecycle and
 * constraint-preservation guard in [SkillRevisionLifecycle]. No Android or ObjectBox dependencies.
 */
class SkillRevisionLifecycleTest {

    // --- Criterion 1: preservesUserConstraints true when all authoritative constraints present ---

    @Test
    fun preservesUserConstraints_allPresent_caseAndWhitespaceInsensitive_returnsTrue() {
        // Criterion 1: all authoritative constraints present (case/whitespace-insensitive) -> true.
        val authoritative = listOf("Never force push", "  Preserve   signing  ")
        val candidate = revision(
            userConstraints = listOf("never force push", "preserve signing")
        )

        assertTrue(SkillRevisionLifecycle.preservesUserConstraints(authoritative, candidate))
    }

    @Test
    fun preservesUserConstraints_oneMissing_returnsFalse() {
        // Criterion 1: one authoritative constraint missing -> false (weakened/overridden).
        val authoritative = listOf("Never force push", "Preserve signing")
        val candidate = revision(
            userConstraints = listOf("never force push")
        )

        assertFalse(SkillRevisionLifecycle.preservesUserConstraints(authoritative, candidate))
    }

    // --- Criterion 2: no authoritative constraints -> true ---

    @Test
    fun preservesUserConstraints_noAuthoritativeConstraints_returnsTrue() {
        // Criterion 2: no authoritative constraints -> nothing to preserve -> true.
        val candidate = revision(userConstraints = emptyList())

        assertTrue(SkillRevisionLifecycle.preservesUserConstraints(emptyList(), candidate))
    }

    // --- Criterion 3: evaluate passes when constraints preserved + evidence present + no harm ---

    @Test
    fun evaluate_constraintsPreservedEvidencePresentNoHarm_passes() {
        // Criterion 3: constraints preserved + independent evidence + no harm -> passed.
        val authoritative = listOf("Never force push")
        val candidate = revision(id = 7L, userConstraints = listOf("never force push"))

        val evaluation = SkillRevisionLifecycle.evaluate(
            candidate = candidate,
            authoritativeConstraints = authoritative,
            independentEvidenceCount = 2,
            helpfulCount = 3,
            harmfulCount = 0
        )

        assertTrue(evaluation.passed)
        assertTrue(evaluation.preservesUserConstraints)
        assertEquals(7L, evaluation.revisionId)
        assertEquals(2, evaluation.independentEvidenceCount)
        assertEquals(3, evaluation.helpfulCount)
        assertEquals(0, evaluation.harmfulCount)
    }

    // --- Criterion 4: evaluate rejects when a user constraint is weakened/overridden ---

    @Test
    fun evaluate_userConstraintWeakened_rejects() {
        // Criterion 4: no reviewer may weaken/override user constraints -> rejected.
        val authoritative = listOf("Never force push", "Preserve signing")
        val candidate = revision(userConstraints = listOf("never force push"))

        val evaluation = SkillRevisionLifecycle.evaluate(
            candidate = candidate,
            authoritativeConstraints = authoritative,
            independentEvidenceCount = 5,
            helpfulCount = 10,
            harmfulCount = 0
        )

        assertFalse(evaluation.passed)
        assertFalse(evaluation.preservesUserConstraints)
        assertTrue(evaluation.reason.contains("user constraint"))
    }

    // --- Criterion 5: evaluate rejects when there is no independent evidence ---

    @Test
    fun evaluate_noIndependentEvidence_rejects() {
        // Criterion 5: no independent evidence -> rejected.
        val authoritative = listOf("Never force push")
        val candidate = revision(userConstraints = listOf("never force push"))

        val evaluation = SkillRevisionLifecycle.evaluate(
            candidate = candidate,
            authoritativeConstraints = authoritative,
            independentEvidenceCount = 0,
            helpfulCount = 0,
            harmfulCount = 0
        )

        assertFalse(evaluation.passed)
        assertTrue(evaluation.preservesUserConstraints)
        assertTrue(evaluation.reason.contains("no independent evidence"))
    }

    // --- Criterion 6: evaluate rejects when harmful outcomes are recorded ---

    @Test
    fun evaluate_harmfulOutcomesRecorded_rejects() {
        // Criterion 6: harmful outcomes recorded -> rejected.
        val authoritative = listOf("Never force push")
        val candidate = revision(userConstraints = listOf("never force push"))

        val evaluation = SkillRevisionLifecycle.evaluate(
            candidate = candidate,
            authoritativeConstraints = authoritative,
            independentEvidenceCount = 3,
            helpfulCount = 4,
            harmfulCount = 1
        )

        assertFalse(evaluation.passed)
        assertTrue(evaluation.preservesUserConstraints)
        assertTrue(evaluation.reason.contains("harmful"))
    }

    // --- Criterion 7: applyEvaluation sets PROMOTED on pass, REJECTED on fail ---

    @Test
    fun applyEvaluation_passingEvaluation_setsPromoted() {
        // Criterion 7: passing evaluation -> status PROMOTED, counts updated.
        val candidate = revision(id = 1L, status = SkillRevision.STATUS_DRAFT)
        val evaluation = SkillRevisionEvaluation(
            revisionId = 1L,
            independentEvidenceCount = 2,
            helpfulCount = 5,
            harmfulCount = 0,
            preservesUserConstraints = true,
            passed = true,
            reason = "passed"
        )

        val applied = SkillRevisionLifecycle.applyEvaluation(candidate, evaluation)

        assertEquals(SkillRevision.STATUS_PROMOTED, applied.status)
        assertEquals(5, applied.helpfulCount)
        assertEquals(0, applied.harmfulCount)
    }

    @Test
    fun applyEvaluation_failingEvaluation_setsRejected() {
        // Criterion 7: failing evaluation -> status REJECTED, counts updated.
        val candidate = revision(id = 2L, status = SkillRevision.STATUS_DRAFT)
        val evaluation = SkillRevisionEvaluation(
            revisionId = 2L,
            independentEvidenceCount = 0,
            helpfulCount = 0,
            harmfulCount = 2,
            preservesUserConstraints = false,
            passed = false,
            reason = "rejected"
        )

        val applied = SkillRevisionLifecycle.applyEvaluation(candidate, evaluation)

        assertEquals(SkillRevision.STATUS_REJECTED, applied.status)
        assertEquals(0, applied.helpfulCount)
        assertEquals(2, applied.harmfulCount)
    }

    // --- Criterion 8: rollbackTarget returns parent when present, null otherwise ---

    @Test
    fun rollbackTarget_parentPresent_returnsParentRevision() {
        // Criterion 8: parent version present -> returns the parent revision.
        val parent = revision(id = 10L, skillId = "skill-a", version = 1)
        val child = revision(id = 11L, skillId = "skill-a", version = 2, parentVersion = 1)

        val target = SkillRevisionLifecycle.rollbackTarget(child, listOf(parent, child))

        assertEquals(parent, target)
    }

    @Test
    fun rollbackTarget_noParentVersion_returnsNull() {
        // Criterion 8: parentVersion <= 0 -> null (nothing to roll back to).
        val child = revision(id = 11L, skillId = "skill-a", version = 1, parentVersion = 0)

        assertNull(SkillRevisionLifecycle.rollbackTarget(child, listOf(child)))
    }

    @Test
    fun rollbackTarget_parentNotFound_returnsNull() {
        // Criterion 8: parent version not found in revisions -> null.
        val child = revision(id = 11L, skillId = "skill-a", version = 2, parentVersion = 1)
        val unrelated = revision(id = 99L, skillId = "skill-b", version = 1)

        assertNull(SkillRevisionLifecycle.rollbackTarget(child, listOf(child, unrelated)))
    }

    // --- Criterion 9: end-to-end promotion/rejection preserving constraints ---

    @Test
    fun endToEnd_candidateDroppingUserConstraint_rejectedDespiteEvidenceAndHelpfulOutcomes() {
        // Criterion 9: a candidate that drops a user constraint is rejected even with evidence
        // and helpful outcomes; a constraint-preserving candidate with evidence is promoted.
        val authoritative = listOf("Never force push", "Preserve signing certificate")

        val weakeningCandidate = revision(
            id = 20L,
            skillId = "skill-a",
            version = 2,
            parentVersion = 1,
            userConstraints = listOf("never force push")
        )
        val weakeningEvaluation = SkillRevisionLifecycle.evaluate(
            candidate = weakeningCandidate,
            authoritativeConstraints = authoritative,
            independentEvidenceCount = 4,
            helpfulCount = 9,
            harmfulCount = 0
        )
        val rejected = SkillRevisionLifecycle.applyEvaluation(weakeningCandidate, weakeningEvaluation)

        assertFalse(weakeningEvaluation.passed)
        assertEquals(SkillRevision.STATUS_REJECTED, rejected.status)

        val preservingCandidate = revision(
            id = 21L,
            skillId = "skill-a",
            version = 2,
            parentVersion = 1,
            userConstraints = listOf("never force push", "preserve signing certificate")
        )
        val preservingEvaluation = SkillRevisionLifecycle.evaluate(
            candidate = preservingCandidate,
            authoritativeConstraints = authoritative,
            independentEvidenceCount = 4,
            helpfulCount = 9,
            harmfulCount = 0
        )
        val promoted = SkillRevisionLifecycle.applyEvaluation(preservingCandidate, preservingEvaluation)

        assertTrue(preservingEvaluation.passed)
        assertEquals(SkillRevision.STATUS_PROMOTED, promoted.status)

        // Prior version is retained and rollback remains possible.
        val parent = revision(id = 19L, skillId = "skill-a", version = 1)
        assertEquals(parent, SkillRevisionLifecycle.rollbackTarget(promoted, listOf(parent, promoted)))
    }

    private fun revision(
        id: Long = 0L,
        skillId: String = "skill",
        version: Int = 1,
        parentVersion: Int = 0,
        userConstraints: List<String> = emptyList(),
        status: String = SkillRevision.STATUS_DRAFT
    ): SkillRevision = SkillRevision(
        id = id,
        skillId = skillId,
        version = version,
        parentVersion = parentVersion,
        userConstraints = userConstraints,
        status = status
    )
}
