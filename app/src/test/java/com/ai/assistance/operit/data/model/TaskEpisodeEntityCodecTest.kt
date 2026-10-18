package com.ai.assistance.operit.data.model

import com.ai.assistance.operit.data.learning.StringListCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM regression tests for D07 on [TaskEpisodeEntity]: the list columns survive a
 * `fromEpisode` → `toEpisode` round trip unchanged, including a multiline item.
 *
 * Pure: the entity is a plain data class, so no ObjectBox store is needed. Adapter-level ObjectBox
 * upgrade compatibility is NOT proven here; that remains part of A01 device verification (PENDING).
 */
class TaskEpisodeEntityCodecTest {

    private fun episode(
        requiredPrerequisites: List<String> = emptyList(),
        applicableConstraints: List<String> = emptyList(),
        evidenceKeys: List<String> = emptyList()
    ) = TaskEpisode(
        id = 7L,
        taskSummary = "task",
        contextSummary = "context",
        outcome = TaskEpisode.OUTCOME_SUCCESS,
        failureSummary = "",
        causeSummary = "",
        causeIsHypothesis = false,
        resolutionSummary = "resolved",
        requiredPrerequisites = requiredPrerequisites,
        applicableConstraints = applicableConstraints,
        evidenceKeys = evidenceKeys,
        relatedSkillVersion = "skill@2",
        createdAtMs = 1_700_000_000_000L
    )

    @Test
    fun fromEpisodeToEpisode_multilineItem_survivesUnchanged() {
        val source = episode(
            requiredPrerequisites = listOf("install the tool\nand restart"),
            applicableConstraints = listOf("never force push"),
            evidenceKeys = listOf("evidence:1")
        )

        val restored = TaskEpisodeEntity.fromEpisode(source).toEpisode()

        assertEquals(source.requiredPrerequisites, restored.requiredPrerequisites)
        assertEquals(source.applicableConstraints, restored.applicableConstraints)
        assertEquals(source.evidenceKeys, restored.evidenceKeys)
        assertEquals(source, restored)
    }

    @Test
    fun fromEpisodeToEpisode_emptyAndWhitespaceItems_surviveUnchanged() {
        val source = episode(
            requiredPrerequisites = listOf("", "  padded  ", "a,b|c;d"),
            applicableConstraints = listOf("中文约束", "emoji 🚀"),
            evidenceKeys = listOf("""quote " and backslash \""")
        )

        val restored = TaskEpisodeEntity.fromEpisode(source).toEpisode()

        assertEquals(source.requiredPrerequisites, restored.requiredPrerequisites)
        assertEquals(source.applicableConstraints, restored.applicableConstraints)
        assertEquals(source.evidenceKeys, restored.evidenceKeys)
    }

    @Test
    fun fromEpisode_writesTheVersionEnvelope() {
        val entity = TaskEpisodeEntity.fromEpisode(episode(requiredPrerequisites = listOf("a", "b")))

        assertEquals("""{"v":1,"items":["a","b"]}""", entity.requiredPrerequisitesJoined)
        assertEquals("""{"v":1,"items":[]}""", entity.applicableConstraintsJoined)
    }

    @Test
    fun toEpisode_legacyRow_decodesWithOldSemantics() {
        val entity = TaskEpisodeEntity(
            id = 3L,
            requiredPrerequisitesJoined = "  a  \n\n b ",
            applicableConstraintsJoined = "c",
            evidenceKeysJoined = ""
        )

        val restored = entity.toEpisode()

        assertEquals(listOf("a", "b"), restored.requiredPrerequisites)
        assertEquals(listOf("c"), restored.applicableConstraints)
        assertEquals(emptyList<String>(), restored.evidenceKeys)
    }

    @Test
    fun toEpisode_corruptRow_doesNotThrowAndLeavesStoredValueUntouched() {
        val corrupt = "{\"v\":1,\"items\":[\"a\""
        val entity = TaskEpisodeEntity(id = 4L, requiredPrerequisitesJoined = corrupt)

        val restored = entity.toEpisode()

        // The read boundary logs and yields an empty list for that field only; the row is untouched.
        assertEquals(emptyList<String>(), restored.requiredPrerequisites)
        assertEquals(corrupt, entity.requiredPrerequisitesJoined)
        assertTrue(entity.decodeListField(TaskEpisodeEntity.FIELD_REQUIRED_PREREQUISITES)
            is StringListCodec.DecodedList.Corrupt)
    }

    @Test
    fun rawListField_returnsStoredStringForEveryListField() {
        val entity = TaskEpisodeEntity(
            requiredPrerequisitesJoined = "r",
            applicableConstraintsJoined = "c",
            evidenceKeysJoined = "e"
        )

        assertEquals("r", entity.rawListField(TaskEpisodeEntity.FIELD_REQUIRED_PREREQUISITES))
        assertEquals("c", entity.rawListField(TaskEpisodeEntity.FIELD_APPLICABLE_CONSTRAINTS))
        assertEquals("e", entity.rawListField(TaskEpisodeEntity.FIELD_EVIDENCE_KEYS))
        assertEquals(3, TaskEpisodeEntity.LIST_FIELDS.size)
    }
}
