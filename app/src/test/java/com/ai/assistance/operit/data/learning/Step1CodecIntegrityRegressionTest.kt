package com.ai.assistance.operit.data.learning

import com.ai.assistance.operit.data.model.SkillRevisionEntity
import com.ai.assistance.operit.data.model.TaskEpisode
import com.ai.assistance.operit.data.model.TaskEpisodeEntity
import com.ai.assistance.operit.util.AppLogger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Failing-first regression tests for the Step 1 codec & data integrity repair.
 *
 * These tests encode the CHOSEN provenance policy honestly and assert BEHAVIOR (decode outcome,
 * integrity status, lifecycle pass/fail, formatter output, raw-byte preservation), not merely that
 * an API exists. On the PRE-REPAIR code they would FAIL:
 *
 *  - `{"a":1}` decoded as Corrupt instead of Ambiguous;
 *  - the lifecycle passed a corrupt candidate;
 *  - the formatter had no fixed caution for an unresolved record;
 *  - `toEpisode()` lost the raw bytes of a corrupt field.
 *
 * Pure JVM: no Android, no ObjectBox, no Robolectric, no mockk. `AppLogger.enableSystemLog` is
 * disabled in [disableSystemLog] so the entity read boundary's error logging does not touch
 * `android.util.Log` (which throws "not mocked" on the JVM).
 */
class Step1CodecIntegrityRegressionTest {

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

    // ---------------------------------------------------------------------------------------------
    // Shared fixtures
    // ---------------------------------------------------------------------------------------------

    /** A truncated versioned envelope: brace-starting, JSON-object-shaped, carries a `v` key. */
    private val truncatedRequiredPrerequisites = "{\"v\":1,\"items\":[\"a\""

    /** A truncated versioned envelope for a SkillRevision userConstraints column. */
    private val truncatedUserConstraints = "{\"v\":1,\"items\":[\"x\""

    /** A truncated versioned envelope for a SkillRevision steps column. */
    private val truncatedSteps = "{\"v\":1,\"items\":[\"s\""

    private fun corruptEpisodeEntity(): TaskEpisodeEntity = TaskEpisodeEntity(
        id = 42L,
        taskSummary = "task",
        requiredPrerequisitesJoined = truncatedRequiredPrerequisites
    )

    private fun corruptUserConstraintsEntity(): SkillRevisionEntity = SkillRevisionEntity(
        id = 7L,
        skillId = "skill",
        userConstraintsJoined = truncatedUserConstraints
    )

    // ---------------------------------------------------------------------------------------------
    // Regression 1: corrupt requiredPrerequisitesJoined truncated payload
    // ---------------------------------------------------------------------------------------------

    @Test
    fun corruptRequiredPrerequisites_truncated_toEpisodePreservesRawAndIntegrity() {
        val entity = corruptEpisodeEntity()

        val ep = entity.toEpisode()

        // An unresolved field contributes an empty list to the domain object...
        assertEquals(emptyList<String>(), ep.requiredPrerequisites)
        // ...but the integrity metadata records that it is CORRUPT, not healthy-empty.
        val integrity = ep.listIntegrity.fields["requiredPrerequisitesJoined"]!!
        assertEquals(ListFieldIntegrity.Status.CORRUPT, integrity.status)
        // The raw stored bytes are preserved verbatim for controlled recovery.
        assertEquals(truncatedRequiredPrerequisites, integrity.rawStored)
        // The row as a whole is flagged as having an unresolved field.
        assertTrue(ep.listIntegrity.hasUnresolved)
    }

    @Test
    fun corruptRequiredPrerequisites_applicabilityIsIneligible() {
        val ep = corruptEpisodeEntity().toEpisode()

        assertEquals(
            EpisodeApplicability.INELIGIBLE_UNRESOLVED_INTEGRITY,
            TaskEpisodeRetrieval.episodeApplicability(ep)
        )
    }

    @Test
    fun corruptRequiredPrerequisites_formatterEmitsFixedCaution() {
        val ep = corruptEpisodeEntity().toEpisode()

        val block = TaskPreflightFormatter.buildPreflightBlock(listOf(ep), "some task", true)

        // The fixed caution is emitted...
        assertTrue(
            "expected the fixed 'needs verification' caution, got: $block",
            block.contains("needs verification")
        )
        // ...and the untrusted raw stored bytes are NEVER rendered into the prompt.
        assertFalse("raw payload substring 'items' leaked into block: $block", block.contains("items"))
        assertFalse("raw payload substring '{\"v\"' leaked into block: $block", block.contains("{\"v\""))
    }

    @Test
    fun corruptRequiredPrerequisites_saveCannotEraseRaw() {
        val entity = corruptEpisodeEntity()
        val ep = entity.toEpisode()

        // With the original raw bytes supplied, the write path preserves them verbatim.
        val resaved = TaskEpisodeEntity.fromEpisode(
            ep,
            mapOf("requiredPrerequisitesJoined" to entity.requiredPrerequisitesJoined)
        )
        assertEquals(entity.requiredPrerequisitesJoined, resaved.requiredPrerequisitesJoined)

        // WITHOUT originalRaw the write path would re-encode the empty list and DESTROY the bytes.
        // This demonstrates why originalRaw is required for an unresolved row.
        val unsafe = TaskEpisodeEntity.fromEpisode(ep)
        assertEquals(StringListCodec.encode(emptyList()), unsafe.requiredPrerequisitesJoined)
    }

    // ---------------------------------------------------------------------------------------------
    // Regression 2: corrupt userConstraintsJoined OR stepsJoined
    // ---------------------------------------------------------------------------------------------

    @Test
    fun corruptUserConstraints_toRevisionPreservesRawAndIntegrity() {
        val entity = corruptUserConstraintsEntity()

        val rev = entity.toRevision()

        assertEquals(emptyList<String>(), rev.userConstraints)
        val integrity = rev.listIntegrity.fields["userConstraintsJoined"]!!
        assertEquals(ListFieldIntegrity.Status.CORRUPT, integrity.status)
        assertEquals(truncatedUserConstraints, integrity.rawStored)
    }

    @Test
    fun corruptUserConstraints_lifecycleRejects() {
        val rev = corruptUserConstraintsEntity().toRevision()

        val eval = SkillRevisionLifecycle.evaluate(
            rev,
            emptyList(),
            independentEvidenceCount = 5,
            helpfulCount = 5,
            harmfulCount = 0
        )

        assertFalse(eval.passed)
        assertFalse(eval.preservesUserConstraints)
        assertTrue(
            "expected reason to mention 'unresolved', got: ${eval.reason}",
            eval.reason.contains("unresolved")
        )
    }

    @Test
    fun corruptSteps_lifecycleRejects() {
        val rev2 = SkillRevisionEntity(
            id = 8L,
            skillId = "skill",
            stepsJoined = truncatedSteps
        ).toRevision()

        val eval = SkillRevisionLifecycle.evaluate(rev2, emptyList(), 5, 5, 0)

        assertFalse(eval.passed)
        assertTrue(
            "expected reason to mention 'unresolved', got: ${eval.reason}",
            eval.reason.contains("unresolved")
        )
    }

    @Test
    fun corruptUserConstraints_roundTripWriteRetainsOriginal() {
        val entity = corruptUserConstraintsEntity()
        val rev = entity.toRevision()

        val resaved = SkillRevisionEntity.fromRevision(
            rev,
            mapOf("userConstraintsJoined" to entity.userConstraintsJoined)
        )

        assertEquals(entity.userConstraintsJoined, resaved.userConstraintsJoined)
    }

    @Test
    fun unresolvedBaselineRevision_rejectsEvenHealthyCandidate() {
        // A healthy candidate that would otherwise pass every check.
        val candidate = SkillRevision(id = 2L, userConstraints = listOf("keep"))
        // The baseline was read from a corrupt row: its userConstraints are empty for a reason
        // unrelated to the task, so constraint preservation cannot be verified.
        val rev = corruptUserConstraintsEntity().toRevision()

        val eval = SkillRevisionLifecycle.evaluate(candidate, rev, 5, 5, 0)

        assertFalse(eval.passed)
        assertTrue(
            "expected reason to mention 'authoritative revision has unresolved', got: ${eval.reason}",
            eval.reason.contains("authoritative revision has unresolved")
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Regression 3: provenance policy cases
    // ---------------------------------------------------------------------------------------------

    @Test
    fun legacyBraceStartingMultiline_isAmbiguousNotLegacy() {
        val stored = "{placeholder}\nsecond_item"

        val decoded = StringListCodec.decode(stored)

        assertTrue(
            "expected Ambiguous, got: $decoded",
            decoded is StringListCodec.DecodedList.Ambiguous
        )
        assertEquals(stored, (decoded as StringListCodec.DecodedList.Ambiguous).stored)
    }

    @Test
    fun legacyJsonShapedSingleLine_isAmbiguous() {
        val decoded = StringListCodec.decode("{\"a\":1}")

        assertTrue(
            "expected Ambiguous, got: $decoded",
            decoded is StringListCodec.DecodedList.Ambiguous
        )
    }

    @Test
    fun rawNewlineInsideStringItem_roundTripsLosslesslyAsVersioned() {
        // A raw newline INSIDE a JSON string literal is accepted by kotlinx.serialization's Json
        // even with isLenient=false, so this payload parses successfully as Versioned(1, ["a\nb"]).
        // That is the CORRECT, lossless behavior the codec is designed for: the newline is preserved
        // inside the single item and is NOT split into two items. (The old newline-join encoding
        // would have split "a\nb" into ["a", "b"], losing the fact that it was one item.)
        val decoded = StringListCodec.decode("{\"v\":1,\"items\":[\"a\nb\"]}")

        assertTrue(
            "expected Versioned, got: $decoded",
            decoded is StringListCodec.DecodedList.Versioned
        )
        val versioned = decoded as StringListCodec.DecodedList.Versioned
        assertEquals(1, versioned.version)
        assertEquals(
            "the raw newline must survive inside the single item, not be split into two items",
            listOf("a\nb"),
            versioned.items
        )
    }

    @Test
    fun malformedVersionedPayload_isCorruptNotLegacy() {
        // A genuinely malformed versioned payload: truncated JSON that still starts with the
        // envelope-shaped prefix `{"v":`. The strict parse fails, and the parse-independent prefix
        // probe sees `{"v":`, so this is Corrupt (not Ambiguous, not Legacy). This preserves the
        // original intent: a malformed versioned payload is Corrupt, never reinterpreted as Legacy.
        val decoded = StringListCodec.decode("{\"v\":1,\"items\":[\"a\"")

        assertTrue(
            "expected Corrupt, got: $decoded",
            decoded is StringListCodec.DecodedList.Corrupt
        )
    }

    @Test
    fun unknownVersion_isUnsupported() {
        val decoded = StringListCodec.decode("{\"v\":999,\"items\":[]}")

        assertTrue(
            "expected UnsupportedVersion, got: $decoded",
            decoded is StringListCodec.DecodedList.UnsupportedVersion
        )
        assertEquals(999, (decoded as StringListCodec.DecodedList.UnsupportedVersion).version)
    }

    @Test
    fun trailingJunk_isCorrupt() {
        val decoded = StringListCodec.decode("{\"v\":1,\"items\":[\"a\"]}garbage")

        assertTrue(
            "expected Corrupt, got: $decoded",
            decoded is StringListCodec.DecodedList.Corrupt
        )
    }

    @Test
    fun nonBraceLegacy_stillLegacy() {
        val decoded = StringListCodec.decode("a\nb\nc")

        assertTrue(
            "expected Legacy, got: $decoded",
            decoded is StringListCodec.DecodedList.Legacy
        )
        assertEquals(
            listOf("a", "b", "c"),
            (decoded as StringListCodec.DecodedList.Legacy).items
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Regression 4: round-trip and idempotent migration
    // ---------------------------------------------------------------------------------------------

    @Test
    fun knownFormat_roundTrip_bracesQuotesNewlines() {
        val values = listOf("a{b}", "c\"d", "e\nf", "", "  spaced  ", "unicode: 你好", "{\"nested\":1}")

        val encoded = StringListCodec.encode(values)
        val decoded = StringListCodec.decode(encoded)

        assertTrue(
            "expected Versioned, got: $decoded",
            decoded is StringListCodec.DecodedList.Versioned
        )
        assertEquals(values, (decoded as StringListCodec.DecodedList.Versioned).items)
    }

    @Test
    fun repeatMigration_isIdempotent() {
        val legacy = "a\nb"

        val m1 = StringListMigration.migrateValue(legacy)
        assertEquals(StringListMigration.Outcome.MIGRATED, m1.outcome)

        val m2 = StringListMigration.migrateValue(m1.value)
        assertEquals(StringListMigration.Outcome.ALREADY_VERSIONED, m2.outcome)
        assertEquals(m1.value, m2.value)
    }

    @Test
    fun healthyEmptyList_remainsHealthy() {
        val encoded = StringListCodec.encode(emptyList())
        val decoded = StringListCodec.decode(encoded)

        assertTrue(
            "expected Versioned, got: $decoded",
            decoded is StringListCodec.DecodedList.Versioned
        )
        assertTrue((decoded as StringListCodec.DecodedList.Versioned).items.isEmpty())

        // A row whose three list columns are all the healthy empty envelope is NOT unresolved.
        val entity = TaskEpisodeEntity(
            id = 1L,
            taskSummary = "task",
            requiredPrerequisitesJoined = encoded,
            applicableConstraintsJoined = encoded,
            evidenceKeysJoined = encoded
        )
        assertFalse(entity.toEpisode().listIntegrity.hasUnresolved)
    }

    // ---------------------------------------------------------------------------------------------
    // Regression 5: later-field corruption leaves row unmutated; transformations preserve integrity
    // ---------------------------------------------------------------------------------------------

    @Test
    fun laterFieldCorruption_rowMigrationLeavesRowUntouched() {
        val raw = linkedMapOf(
            "requiredPrerequisitesJoined" to "a\nb",
            "applicableConstraintsJoined" to "{\"v\":1,\"items\":[\"x\"",
            "evidenceKeysJoined" to "c\nd"
        )

        val plan = RowMigrationPlanner.plan(raw)

        assertEquals(RowMigrationPlanner.RowOutcome.NEEDS_ATTENTION, plan.outcome)
        // Nothing to write: the row must not be half-migrated.
        assertTrue(plan.fieldValues.isEmpty())
        assertEquals(listOf("applicableConstraintsJoined"), plan.attentionFields)
    }

    @Test
    fun copyPreservesIntegrity() {
        val ep = corruptEpisodeEntity().toEpisode()

        val copied = ep.copy(taskSummary = "changed")

        assertTrue(copied.listIntegrity.hasUnresolved)
        assertEquals(
            ListFieldIntegrity.Status.CORRUPT,
            copied.listIntegrity.fields["requiredPrerequisitesJoined"]!!.status
        )
    }

    @Test
    fun ambiguousField_isUnresolvedInIntegrity() {
        val entity = TaskEpisodeEntity(
            id = 3L,
            taskSummary = "task",
            applicableConstraintsJoined = "{\"a\":1}"
        )

        val ep = entity.toEpisode()

        assertEquals(
            ListFieldIntegrity.Status.AMBIGUOUS,
            ep.listIntegrity.fields["applicableConstraintsJoined"]!!.status
        )
        assertTrue(ep.listIntegrity.hasUnresolved)
    }
    // ---------------------------------------------------------------------------------------------
    // Regression 6: safe-by-default re-save and additional integrity statuses
    // ---------------------------------------------------------------------------------------------

    @Test
    fun corruptApplicableConstraints_applicabilityIsIneligible() {
        val entity = TaskEpisodeEntity(
            id = 11L,
            taskSummary = "task",
            applicableConstraintsJoined = "{\"v\":1,\"items\":[\"x\""
        )

        val ep = entity.toEpisode()

        assertEquals(
            EpisodeApplicability.INELIGIBLE_UNRESOLVED_INTEGRITY,
            TaskEpisodeRetrieval.episodeApplicability(ep)
        )
    }

    @Test
    fun unsupportedVersionField_hasUnsupportedVersionStatus() {
        val entity = TaskEpisodeEntity(
            id = 12L,
            taskSummary = "task",
            requiredPrerequisitesJoined = "{\"v\":999,\"items\":[]}"
        )

        val ep = entity.toEpisode()

        val integrity = ep.listIntegrity.fields["requiredPrerequisitesJoined"]!!
        assertEquals(ListFieldIntegrity.Status.UNSUPPORTED_VERSION, integrity.status)
        assertEquals(999, integrity.version)
    }

    @Test
    fun fromEpisodeWithOriginalRaw_retainsOriginalBytes() {
        val entity = TaskEpisodeEntity(
            id = 5L,
            taskSummary = "task",
            requiredPrerequisitesJoined = "{\"v\":1,\"items\":[\"a\""
        )

        val ep = entity.toEpisode()

        // This row carries unresolved integrity, so the entity-level originalRaw mechanism applies.
        assertTrue(TaskEpisodeEntity.hasUnresolvedFields(ep))

        // Entity-level originalRaw mechanism: the stored raw bytes are supplied back verbatim to
        // fromEpisode. (The repository-level decision of WHEN to read those bytes is covered by
        // unresolvedWriteGuard_preservesStoredRawOnlyWhenUnresolvedAndNoCallerRawAndPersisted.)
        val resaved = TaskEpisodeEntity.fromEpisode(
            ep,
            mapOf("requiredPrerequisitesJoined" to entity.requiredPrerequisitesJoined)
        )

        // The original bytes are retained, NOT re-encoded to an empty envelope.
        assertEquals(entity.requiredPrerequisitesJoined, resaved.requiredPrerequisitesJoined)
    }

    @Test
    fun unresolvedWriteGuard_preservesStoredRawOnlyWhenUnresolvedAndNoCallerRawAndPersisted() {
        // The safe-by-default decision: preserve stored raw bytes only when the row is unresolved,
        // the caller supplied no originalRaw, and the row is persisted (id != 0).
        assertTrue(
            UnresolvedWriteGuard.mustPreserveStoredRaw(
                hasUnresolved = true, callerSuppliedRaw = emptyMap(), persistedId = 5L
            )
        )
        // A healthy row needs no preservation.
        assertFalse(
            UnresolvedWriteGuard.mustPreserveStoredRaw(
                hasUnresolved = false, callerSuppliedRaw = emptyMap(), persistedId = 5L
            )
        )
        // An explicit caller map takes precedence (no auto-read).
        assertFalse(
            UnresolvedWriteGuard.mustPreserveStoredRaw(
                hasUnresolved = true, callerSuppliedRaw = mapOf("requiredPrerequisitesJoined" to "x"), persistedId = 5L
            )
        )
        // A new row (id == 0) has no stored bytes to preserve.
        assertFalse(
            UnresolvedWriteGuard.mustPreserveStoredRaw(
                hasUnresolved = true, callerSuppliedRaw = emptyMap(), persistedId = 0L
            )
        )
    }
}
