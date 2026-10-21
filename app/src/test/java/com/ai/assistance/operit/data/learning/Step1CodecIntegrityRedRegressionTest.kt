package com.ai.assistance.operit.data.learning

import com.ai.assistance.operit.data.model.SkillRevisionEntity
import com.ai.assistance.operit.data.model.TaskEpisodeEntity
import com.ai.assistance.operit.util.AppLogger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * RED (failing-first) regressions for the remaining Step 1 codec/data-integrity defects.
 *
 * These tests assert the CORRECT behavior and therefore FAIL on the pre-fix code:
 *
 *  - Defect 1: an unresolved list field is ERASED (re-encoded to the empty envelope) when the
 *    caller omits the original raw bytes, when the row is an id=0 copy, or when a partial/unrelated
 *    caller map is supplied. The captured [ListFieldIntegrity.rawStored] must be preserved instead.
 *  - Defect 2: an episode with unresolved prerequisite/constraint integrity still renders its
 *    resolution/action content as if applicable; only a caution line is appended.
 *  - Defect 4: the integrity `reason` is the raw kotlinx.serialization exception message, which
 *    embeds a verbatim prefix of the stored input (`JSON input: ...`), leaking untrusted bytes into
 *    logs and diagnostics.
 *
 * Pure JVM: no Android, no ObjectBox, no Robolectric, no mockk. AppLogger system logging is
 * disabled so the entity read boundary does not touch android.util.Log.
 */
class Step1CodecIntegrityRedRegressionTest {

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

    /** A truncated versioned envelope: brace-starting, envelope-prefixed, carries a `v` key. */
    private val truncatedRequiredPrerequisites = "{\"v\":1,\"items\":[\"a\""

    private val truncatedUserConstraints = "{\"v\":1,\"items\":[\"x\""

    private fun corruptEpisodeEntity(): TaskEpisodeEntity = TaskEpisodeEntity(
        id = 42L,
        taskSummary = "task",
        requiredPrerequisitesJoined = truncatedRequiredPrerequisites
    )

    // ---------------------------------------------------------------------------------------------
    // Defect 1: unresolved bytes must survive a re-save that omits the caller raw map.
    // ---------------------------------------------------------------------------------------------

    @Test
    fun fromEpisode_omittedRaw_preservesUnresolvedBytes() {
        val ep = corruptEpisodeEntity().toEpisode()

        // The domain object carries the captured raw bytes in listIntegrity.rawStored.
        assertEquals(
            truncatedRequiredPrerequisites,
            ep.listIntegrity.fields["requiredPrerequisitesJoined"]!!.rawStored
        )

        // Re-saving WITHOUT any caller-supplied raw map must preserve the captured bytes, not
        // re-encode the empty list. On the pre-fix code this erases them to {"v":1,"items":[]}.
        val resaved = TaskEpisodeEntity.fromEpisode(ep)

        assertEquals(truncatedRequiredPrerequisites, resaved.requiredPrerequisitesJoined)
    }

    @Test
    fun fromRevision_omittedRaw_preservesUnresolvedBytes() {
        val rev = SkillRevisionEntity(
            id = 7L,
            skillId = "skill",
            userConstraintsJoined = truncatedUserConstraints
        ).toRevision()

        val resaved = SkillRevisionEntity.fromRevision(rev)

        assertEquals(truncatedUserConstraints, resaved.userConstraintsJoined)
    }

    @Test
    fun fromEpisode_idZeroCopy_preservesUnresolvedBytes() {
        // A copy of an unresolved row (id reset to 0) still carries the captured raw bytes and must
        // not erase them on write.
        val copy = corruptEpisodeEntity().toEpisode().copy(id = 0L)

        val resaved = TaskEpisodeEntity.fromEpisode(copy)

        assertEquals(truncatedRequiredPrerequisites, resaved.requiredPrerequisitesJoined)
    }

    @Test
    fun fromEpisode_preservesUnresolvedBytesRegardlessOfCallerIntent() {
        val ep = corruptEpisodeEntity().toEpisode()

        // There is no caller-supplied raw map any more: preservation is an invariant of the write
        // path, so no caller intent can disable it for the unresolved field.
        val resaved = TaskEpisodeEntity.fromEpisode(ep)

        assertEquals(truncatedRequiredPrerequisites, resaved.requiredPrerequisitesJoined)
    }

    // ---------------------------------------------------------------------------------------------
    // Defect 2: an ineligible (unresolved-integrity) episode must not render actionable content.
    // ---------------------------------------------------------------------------------------------

    @Test
    fun ineligibleEpisode_doesNotRenderResolutionOrCause() {
        val entity = TaskEpisodeEntity(
            id = 42L,
            taskSummary = "task",
            resolutionSummary = "SECRET_RESOLUTION_ACTION",
            causeSummary = "SECRET_CAUSE_TEXT",
            requiredPrerequisitesJoined = truncatedRequiredPrerequisites
        )
        val ep = entity.toEpisode()

        val block = TaskPreflightFormatter.buildPreflightBlock(listOf(ep), "some task", true)

        // The fixed caution is still emitted...
        assertTrue("expected the fixed caution, got: $block", block.contains("needs verification"))
        // ...but the resolution/action and cause content must NOT be rendered as applicable advice.
        assertFalse(
            "resolution/action content must not be rendered for an ineligible record: $block",
            block.contains("SECRET_RESOLUTION_ACTION")
        )
        assertFalse(
            "cause content must not be rendered for an ineligible record: $block",
            block.contains("SECRET_CAUSE_TEXT")
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Defect 4: the integrity reason must be a fixed code, never a raw parser message.
    // ---------------------------------------------------------------------------------------------

    @Test
    fun corruptField_reasonDoesNotLeakRawInput() {
        val secret = "SECRET_TOKEN_ABC123"
        val entity = TaskEpisodeEntity(
            id = 42L,
            taskSummary = "task",
            requiredPrerequisitesJoined = "{\"v\":1,\"items\":[\"$secret\""
        )

        val ep = entity.toEpisode()
        val integrity = ep.listIntegrity.fields["requiredPrerequisitesJoined"]!!

        // The reason is what the entity read boundary logs. It must not embed the raw stored bytes.
        assertFalse(
            "integrity.reason leaked raw input: ${integrity.reason}",
            integrity.reason.contains(secret)
        )
        assertFalse(
            "integrity.reason leaked the envelope prefix: ${integrity.reason}",
            integrity.reason.contains("{\"v\"")
        )
        // The raw bytes are still retained ONLY in the controlled recovery field.
        assertEquals("{\"v\":1,\"items\":[\"$secret\"", integrity.rawStored)
    }
}
