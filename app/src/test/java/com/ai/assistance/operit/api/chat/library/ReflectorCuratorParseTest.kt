package com.ai.assistance.operit.api.chat.library

import com.ai.assistance.operit.data.learning.SkillRevision
import com.ai.assistance.operit.data.model.TaskEpisode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the defensive parsing of Reflector/Curator model output.
 *
 * These exercise the pure [ReflectorCurator.parseEpisode] / [ReflectorCurator.parseRevision]
 * helpers only; no model call is made.
 */
class ReflectorCuratorParseTest {

    @Test
    fun parseEpisode_validJson_populatesFieldsAndDeduplicatesEvidence() {
        val raw =
            """
            {
              "task_summary": "Rebuild APK in place",
              "context_summary": "Local rebuild of an installed app",
              "outcome": "success",
              "failure_summary": "Signature mismatch",
              "cause_summary": "Debug key used instead of release key",
              "cause_is_hypothesis": false,
              "resolution_summary": "Reused the release keystore",
              "required_prerequisites": ["release keystore", "signing config"],
              "applicable_constraints": ["preserve package identity"],
              "evidence_keys": ["incident-1", "incident-2"]
            }
            """.trimIndent()

        val episode = ReflectorCurator.parseEpisode(raw)

        assertEquals("Rebuild APK in place", episode?.taskSummary)
        assertEquals("Local rebuild of an installed app", episode?.contextSummary)
        assertEquals(TaskEpisode.OUTCOME_SUCCESS, episode?.outcome)
        assertEquals("Signature mismatch", episode?.failureSummary)
        assertEquals("Debug key used instead of release key", episode?.causeSummary)
        assertFalse(episode!!.causeIsHypothesis)
        assertEquals("Reused the release keystore", episode.resolutionSummary)
        assertEquals(listOf("release keystore", "signing config"), episode.requiredPrerequisites)
        assertEquals(listOf("preserve package identity"), episode.applicableConstraints)
        assertEquals(listOf("incident-1", "incident-2"), episode.evidenceKeys)
    }

    @Test
    fun parseEpisode_duplicateEvidenceKeys_deduplicatedToOne() {
        val raw =
            """
            {
              "task_summary": "t",
              "outcome": "failure",
              "evidence_keys": ["Incident-1", "incident-1", "  incident-1  "]
            }
            """.trimIndent()

        val episode = ReflectorCurator.parseEpisode(raw)

        assertEquals(listOf("incident-1"), episode?.evidenceKeys)
    }

    @Test
    fun parseEpisode_malformedInput_returnsNull() {
        assertNull(ReflectorCurator.parseEpisode("not json at all"))
        assertNull(ReflectorCurator.parseEpisode(""))
        assertNull(ReflectorCurator.parseEpisode("{ broken json "))
    }

    @Test
    fun parseRevision_validJson_statusDraftAndConstraintsPreserved() {
        val raw =
            """
            {
              "skill_id": "apk-rebuild",
              "scope": "in-place APK update",
              "prerequisites": ["release keystore"],
              "steps": ["build", "sign"],
              "checks": ["verify signature"],
              "pitfalls": ["debug key"],
              "evidence_keys": ["incident-1"],
              "user_constraints": ["never change package name"]
            }
            """.trimIndent()

        val revision = ReflectorCurator.parseRevision(raw, "fallback-id")

        assertEquals("apk-rebuild", revision?.skillId)
        assertEquals("in-place APK update", revision?.scope)
        assertEquals(listOf("release keystore"), revision?.prerequisites)
        assertEquals(listOf("build", "sign"), revision?.steps)
        assertEquals(listOf("verify signature"), revision?.checks)
        assertEquals(listOf("debug key"), revision?.pitfalls)
        assertEquals(listOf("incident-1"), revision?.evidenceKeys)
        assertEquals(SkillRevision.STATUS_DRAFT, revision?.status)
        assertEquals(listOf("never change package name"), revision?.userConstraints)
    }

    @Test
    fun parseRevision_missingSkillId_fallsBackToProvidedSkillId() {
        val raw =
            """
            {
              "scope": "some scope",
              "steps": ["a"]
            }
            """.trimIndent()

        val revision = ReflectorCurator.parseRevision(raw, "provided-skill-id")

        assertEquals("provided-skill-id", revision?.skillId)
    }

    @Test
    fun parseRevision_userConstraint_preservedVerbatim() {
        val constraint = "Preserve package identity and signing certificate"
        val raw =
            """
            {
              "skill_id": "s",
              "user_constraints": ["$constraint"]
            }
            """.trimIndent()

        val revision = ReflectorCurator.parseRevision(raw, "s")

        assertTrue(revision!!.userConstraints.contains(constraint))
        assertEquals(constraint, revision.userConstraints.first())
    }
}
