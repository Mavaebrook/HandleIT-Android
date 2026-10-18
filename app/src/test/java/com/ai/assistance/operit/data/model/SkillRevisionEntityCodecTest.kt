package com.ai.assistance.operit.data.model

import com.ai.assistance.operit.data.learning.SkillRevision
import com.ai.assistance.operit.data.learning.StringListCodec
import com.ai.assistance.operit.util.AppLogger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM regression tests for D07 on [SkillRevisionEntity]: the list columns survive a
 * `fromRevision` → `toRevision` round trip unchanged, including a multiline step.
 *
 * Pure: the entity is a plain data class, so no ObjectBox store is needed. Adapter-level ObjectBox
 * upgrade compatibility is NOT proven here; that remains part of A01 device verification (PENDING).
 */
class SkillRevisionEntityCodecTest {

    private var previousSystemLog = true

    @Before
    fun disableSystemLog() {
        previousSystemLog = AppLogger.enableSystemLog
        AppLogger.enableSystemLog = false
    }

    @After
    fun restoreSystemLog() {
        AppLogger.enableSystemLog = previousSystemLog
    }

    private fun revision(
        prerequisites: List<String> = emptyList(),
        steps: List<String> = emptyList(),
        checks: List<String> = emptyList(),
        pitfalls: List<String> = emptyList(),
        evidenceKeys: List<String> = emptyList(),
        userConstraints: List<String> = emptyList()
    ) = SkillRevision(
        id = 11L,
        skillId = "skill",
        version = 2,
        parentVersion = 1,
        scope = "scope",
        prerequisites = prerequisites,
        steps = steps,
        checks = checks,
        pitfalls = pitfalls,
        evidenceKeys = evidenceKeys,
        userConstraints = userConstraints,
        status = SkillRevision.STATUS_EVALUATED,
        helpfulCount = 3,
        harmfulCount = 1,
        createdAtMs = 1_700_000_000_000L
    )

    @Test
    fun fromRevisionToRevision_multilineStep_survivesUnchanged() {
        val source = revision(
            prerequisites = listOf("have a token"),
            steps = listOf("open the settings\nenable the option\nrestart"),
            checks = listOf("option is enabled"),
            pitfalls = listOf("do not skip the restart"),
            evidenceKeys = listOf("evidence:2"),
            userConstraints = listOf("never force push")
        )

        val restored = SkillRevisionEntity.fromRevision(source).toRevision()

        assertEquals(source.steps, restored.steps)
        assertEquals(source, restored)
    }

    @Test
    fun fromRevisionToRevision_emptyAndWhitespaceItems_surviveUnchanged() {
        val source = revision(
            prerequisites = listOf("", "  padded  "),
            steps = listOf("a,b|c;d", "中文步骤", "emoji 🚀"),
            checks = listOf("""quote " and backslash \"""),
            pitfalls = listOf("line1\nline2"),
            evidenceKeys = listOf("dup", "dup"),
            userConstraints = listOf("  keep  ")
        )

        val restored = SkillRevisionEntity.fromRevision(source).toRevision()

        assertEquals(source.prerequisites, restored.prerequisites)
        assertEquals(source.steps, restored.steps)
        assertEquals(source.checks, restored.checks)
        assertEquals(source.pitfalls, restored.pitfalls)
        assertEquals(source.evidenceKeys, restored.evidenceKeys)
        assertEquals(source.userConstraints, restored.userConstraints)
    }

    @Test
    fun fromRevision_writesTheVersionEnvelope() {
        val entity = SkillRevisionEntity.fromRevision(revision(steps = listOf("a", "b")))

        assertEquals("""{"v":1,"items":["a","b"]}""", entity.stepsJoined)
        assertEquals("""{"v":1,"items":[]}""", entity.prerequisitesJoined)
    }

    @Test
    fun toRevision_legacyRow_decodesWithOldSemantics() {
        val entity = SkillRevisionEntity(
            id = 5L,
            prerequisitesJoined = "  a  \n\n b ",
            stepsJoined = "c",
            checksJoined = "",
            pitfallsJoined = "d\ne",
            evidenceKeysJoined = "",
            userConstraintsJoined = ""
        )

        val restored = entity.toRevision()

        assertEquals(listOf("a", "b"), restored.prerequisites)
        assertEquals(listOf("c"), restored.steps)
        assertEquals(emptyList<String>(), restored.checks)
        assertEquals(listOf("d", "e"), restored.pitfalls)
    }

    @Test
    fun toRevision_corruptRow_doesNotThrowAndLeavesStoredValueUntouched() {
        val corrupt = "{\"v\":1,\"items\":[\"a\""
        val entity = SkillRevisionEntity(id = 6L, stepsJoined = corrupt)

        val restored = entity.toRevision()

        // The read boundary logs and yields an empty list for that field only; the row is untouched.
        assertEquals(emptyList<String>(), restored.steps)
        assertEquals(corrupt, entity.stepsJoined)
        assertTrue(entity.decodeListField(SkillRevisionEntity.FIELD_STEPS)
            is StringListCodec.DecodedList.Corrupt)
    }

    @Test
    fun rawListField_returnsStoredStringForEveryListField() {
        val entity = SkillRevisionEntity(
            prerequisitesJoined = "p",
            stepsJoined = "s",
            checksJoined = "c",
            pitfallsJoined = "f",
            evidenceKeysJoined = "e",
            userConstraintsJoined = "u"
        )

        assertEquals("p", entity.rawListField(SkillRevisionEntity.FIELD_PREREQUISITES))
        assertEquals("s", entity.rawListField(SkillRevisionEntity.FIELD_STEPS))
        assertEquals("c", entity.rawListField(SkillRevisionEntity.FIELD_CHECKS))
        assertEquals("f", entity.rawListField(SkillRevisionEntity.FIELD_PITFALLS))
        assertEquals("e", entity.rawListField(SkillRevisionEntity.FIELD_EVIDENCE_KEYS))
        assertEquals("u", entity.rawListField(SkillRevisionEntity.FIELD_USER_CONSTRAINTS))
        assertEquals(6, SkillRevisionEntity.LIST_FIELDS.size)
    }
}
