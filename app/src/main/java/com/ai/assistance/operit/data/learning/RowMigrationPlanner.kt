package com.ai.assistance.operit.data.learning

/**
 * Pure, side-effect-free planner for the per-row decision of the list-field migration (D07).
 *
 * ## Why this exists
 *
 * The repositories (`TaskEpisodeRepository`, `SkillRevisionRepository`) migrate the list columns of
 * existing rows from the legacy newline-join encoding to the [StringListCodec] version envelope.
 * The invariant is that a row is migrated ATOMICALLY: either every list field of the row is
 * rewritten, or the row is left ENTIRELY untouched — both in memory and on disk. A row that has one
 * legacy field and one corrupt field must not end up with the legacy field rewritten and the corrupt
 * field original.
 *
 * The previous implementation violated that invariant: it mutated the live entity as soon as a field
 * was [StringListMigration.Outcome.MIGRATED] and only discovered a later
 * [StringListMigration.Outcome.NEEDS_ATTENTION] field afterwards. The database row was safe (no
 * `put` happened) but the in-memory entity inside the open transaction was left half-migrated.
 *
 * This planner makes the decision in ONE pass over the raw stored values, before anything is
 * mutated: it returns the complete set of values to write, or nothing at all. The repository then
 * applies the plan. Because the planner depends only on [StringListMigration] (itself pure), it is
 * unit-testable on the JVM without an ObjectBox store.
 *
 * ## Rule
 *
 *  - any field needs attention (corrupt or unsupported version) → [RowOutcome.NEEDS_ATTENTION] with
 *    an EMPTY [RowPlan.fieldValues], so the caller mutates nothing; the offending field names are
 *    reported in [RowPlan.attentionFields] for logging;
 *  - otherwise, at least one field was legacy → [RowOutcome.MIGRATED] with exactly the changed
 *    fields in [RowPlan.fieldValues];
 *  - otherwise every field was already versioned → [RowOutcome.ALREADY_VERSIONED] with an empty
 *    [RowPlan.fieldValues].
 *
 * Fields are visited in the insertion order of the input map, so the reported
 * [RowPlan.attentionFields] order is deterministic.
 */
object RowMigrationPlanner {

    /** What the migration should do with one whole row. */
    enum class RowOutcome {
        /** At least one field was legacy and no field needs attention: write the changed fields. */
        MIGRATED,

        /** Every field was already in the current versioned format: write nothing. */
        ALREADY_VERSIONED,

        /** At least one field is corrupt or of an unsupported version: leave the row untouched. */
        NEEDS_ATTENTION
    }

    /**
     * The decision for one row.
     *
     * @param outcome what to do with the row.
     * @param fieldValues field name → value to store. Only populated for [RowOutcome.MIGRATED], and
     *   then only with the fields whose content actually changed. Empty for every other outcome, so
     *   a caller that applies this map can never half-migrate a row.
     * @param attentionFields the fields that need attention, in input order. Only populated for
     *   [RowOutcome.NEEDS_ATTENTION].
     */
    data class RowPlan(
        val outcome: RowOutcome,
        val fieldValues: Map<String, String>,
        val attentionFields: List<String>
    )

    /**
     * Plan the migration of one row from its raw stored list-field values.
     *
     * Pure and idempotent: planning the already-migrated values of a [RowOutcome.MIGRATED] plan
     * yields [RowOutcome.ALREADY_VERSIONED].
     *
     * @param rawFields field name → raw stored string, in the entity's declaration order.
     */
    fun plan(rawFields: Map<String, String>): RowPlan {
        val migratedValues = LinkedHashMap<String, String>()
        val attentionFields = ArrayList<String>()
        for ((fieldName, stored) in rawFields) {
            val result = StringListMigration.migrateValue(stored)
            when (result.outcome) {
                StringListMigration.Outcome.MIGRATED -> migratedValues[fieldName] = result.value
                StringListMigration.Outcome.ALREADY_VERSIONED -> Unit
                StringListMigration.Outcome.NEEDS_ATTENTION -> attentionFields.add(fieldName)
            }
        }
        return when {
            attentionFields.isNotEmpty() -> RowPlan(
                outcome = RowOutcome.NEEDS_ATTENTION,
                fieldValues = emptyMap(),
                attentionFields = attentionFields
            )

            migratedValues.isNotEmpty() -> RowPlan(
                outcome = RowOutcome.MIGRATED,
                fieldValues = migratedValues,
                attentionFields = emptyList()
            )

            else -> RowPlan(
                outcome = RowOutcome.ALREADY_VERSIONED,
                fieldValues = emptyMap(),
                attentionFields = emptyList()
            )
        }
    }
}
