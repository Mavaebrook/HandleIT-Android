package com.ai.assistance.operit.data.repository

import com.ai.assistance.operit.data.learning.RowMigrationPlanner
import com.ai.assistance.operit.data.learning.StringListCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM regression tests for the ATOMICITY of the list-field migration (D07).
 *
 * The repositories (`TaskEpisodeRepository`, `SkillRevisionRepository`) need an ObjectBox store,
 * which is not available in JVM tests (no Robolectric, no mockk in this project). The per-row
 * decision they make is therefore extracted into the pure [RowMigrationPlanner], and these tests
 * pin the invariant that matters:
 *
 *  - a row with ANY corrupt or unsupported field is left ENTIRELY untouched: the plan carries an
 *    EMPTY `fieldValues` map, so the repository has nothing to `setListField` and nothing to `put`;
 *  - a row with only legacy fields is migrated as a whole;
 *  - a row that is already fully versioned is not written;
 *  - re-running the migration on its own output is a no-op (idempotent).
 *
 * Adapter-level ObjectBox behaviour (an existing install whose rows were written by the old join,
 * read back through the new codec after an app update) is NOT proven here; that remains part of A01
 * device verification and is PENDING.
 */
class MigrationAtomicityTest {

    private val legacyA = "x\ny"
    private val legacyB = "p\nq"

    /** A truncated envelope: envelope-shaped, so it is corruption, never legacy. */
    private val corrupt = "{\"v\":1,\"items\":[\"z\""

    @Test
    fun plan_legacyThenCorrupt_laterFieldFailure_leavesRowUntouched() {
        val rawFields = linkedMapOf(
            "a" to legacyA,
            "b" to corrupt
        )

        val plan = RowMigrationPlanner.plan(rawFields)

        assertEquals(RowMigrationPlanner.RowOutcome.NEEDS_ATTENTION, plan.outcome)
        // The whole point: nothing to set, so the entity cannot be half-migrated in memory.
        assertTrue(plan.fieldValues.isEmpty())
        assertEquals(listOf("b"), plan.attentionFields)
    }

    @Test
    fun plan_allLegacy_migratesAllFields() {
        val rawFields = linkedMapOf(
            "a" to legacyA,
            "b" to legacyB
        )

        val plan = RowMigrationPlanner.plan(rawFields)

        assertEquals(RowMigrationPlanner.RowOutcome.MIGRATED, plan.outcome)
        assertEquals(
            mapOf(
                "a" to StringListCodec.encode(listOf("x", "y")),
                "b" to StringListCodec.encode(listOf("p", "q"))
            ),
            plan.fieldValues
        )
        assertTrue(plan.attentionFields.isEmpty())
    }

    @Test
    fun plan_allVersioned_alreadyVersioned() {
        val rawFields = linkedMapOf(
            "a" to StringListCodec.encode(listOf("x", "y")),
            "b" to StringListCodec.encode(listOf("p", "q"))
        )

        val plan = RowMigrationPlanner.plan(rawFields)

        assertEquals(RowMigrationPlanner.RowOutcome.ALREADY_VERSIONED, plan.outcome)
        assertTrue(plan.fieldValues.isEmpty())
        assertTrue(plan.attentionFields.isEmpty())
    }

    @Test
    fun plan_mixedLegacyAndVersioned_migratesOnlyLegacy() {
        val versionedB = StringListCodec.encode(listOf("p", "q"))
        val rawFields = linkedMapOf(
            "a" to legacyA,
            "b" to versionedB
        )

        val plan = RowMigrationPlanner.plan(rawFields)

        assertEquals(RowMigrationPlanner.RowOutcome.MIGRATED, plan.outcome)
        assertEquals(
            mapOf("a" to StringListCodec.encode(listOf("x", "y"))),
            plan.fieldValues
        )
        assertTrue(plan.attentionFields.isEmpty())
    }

    @Test
    fun plan_corruptFirstField_stillNeedsAttention() {
        val rawFields = linkedMapOf(
            "a" to corrupt,
            "b" to legacyB
        )

        val plan = RowMigrationPlanner.plan(rawFields)

        assertEquals(RowMigrationPlanner.RowOutcome.NEEDS_ATTENTION, plan.outcome)
        assertTrue(plan.fieldValues.isEmpty())
        assertEquals(listOf("a"), plan.attentionFields)
    }

    @Test
    fun plan_idempotentReRun() {
        val firstPlan = RowMigrationPlanner.plan(
            linkedMapOf(
                "a" to legacyA,
                "b" to legacyB
            )
        )
        assertEquals(RowMigrationPlanner.RowOutcome.MIGRATED, firstPlan.outcome)

        // Apply the plan exactly as the repository does, then plan the resulting row again.
        val migratedRawFields = linkedMapOf(
            "a" to firstPlan.fieldValues.getValue("a"),
            "b" to firstPlan.fieldValues.getValue("b")
        )
        val secondPlan = RowMigrationPlanner.plan(migratedRawFields)

        assertEquals(RowMigrationPlanner.RowOutcome.ALREADY_VERSIONED, secondPlan.outcome)
        assertTrue(secondPlan.fieldValues.isEmpty())
    }

    @Test
    fun plan_unsupportedVersion_needsAttention() {
        val rawFields = linkedMapOf(
            "a" to "{\"v\":999,\"items\":[]}"
        )

        val plan = RowMigrationPlanner.plan(rawFields)

        assertEquals(RowMigrationPlanner.RowOutcome.NEEDS_ATTENTION, plan.outcome)
        assertTrue(plan.fieldValues.isEmpty())
        assertEquals(listOf("a"), plan.attentionFields)
    }
}
