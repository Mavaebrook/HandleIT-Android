package com.ai.assistance.operit.data.repository

import android.content.Context
import com.ai.assistance.operit.data.db.ObjectBoxManager
import com.ai.assistance.operit.data.learning.MigrationReport
import com.ai.assistance.operit.data.learning.RowMigrationPlanner
import com.ai.assistance.operit.data.learning.SkillRevision
import com.ai.assistance.operit.data.learning.StringListMigration
import com.ai.assistance.operit.data.model.SkillRevisionEntity
import com.ai.assistance.operit.data.model.SkillRevisionEntity_
import com.ai.assistance.operit.util.AppLogger
import io.objectbox.Box
import io.objectbox.kotlin.boxFor
import io.objectbox.kotlin.query

/**
 * Persistence for versioned skill revisions. Prior versions are retained so rollback is possible.
 */
class SkillRevisionRepository(
    context: Context,
    profileId: String
) {
    private val store = ObjectBoxManager.get(context, profileId)
    private val revisionBox: Box<SkillRevisionEntity> = store.boxFor()

    /**
     * Persist [revision].
     *
     * ## Unresolved list fields
     *
     * A revision read from a row whose list bytes could not be decoded carries `emptyList()` for
     * that field (see [SkillRevisionEntity.toRevision]) and records the fact in
     * [SkillRevision.listIntegrity]. Re-encoding such a field would write `{"v":1,"items":[]}` and
     * DESTROY the original bytes.
     *
     * Therefore, when re-saving a row that was read with unresolved integrity, pass [originalRaw]
     * (field name → the row's [SkillRevisionEntity.rawListField] value) so the original bytes are
     * preserved verbatim; otherwise the unresolved field would be re-encoded as an empty envelope.
     * Use [SkillRevisionEntity.hasUnresolvedFields] to detect such a row, and
     * [resavePreservingUnresolved] to do the round trip safely.
     *
     * ## Safe by default
     *
     * This method is SAFE BY DEFAULT: when [revision] has unresolved fields, [originalRaw] is empty
     * and the revision has a stored id, the existing row's raw list bytes are read from the store and
     * preserved automatically. A caller doing `save(toRevision())` therefore cannot erase the bytes
     * of an unresolved row. An explicit [originalRaw] still takes precedence over the automatic read.
     * If the row no longer exists there is nothing to preserve and the normal encode path runs.
     *
     * For a NEW revision (always healthy) the default empty [originalRaw] is correct.
     */
    fun save(revision: SkillRevision, originalRaw: Map<String, String> = emptyMap()): Long {
        val effectiveRaw = if (SkillRevisionEntity.hasUnresolvedFields(revision) && originalRaw.isEmpty() && revision.id != 0L) {
            val existing = revisionBox.get(revision.id)
            if (existing != null) {
                SkillRevisionEntity.LIST_FIELDS.associateWith { existing.rawListField(it) }
            } else emptyMap()
        } else originalRaw
        return revisionBox.put(SkillRevisionEntity.fromRevision(revision, effectiveRaw))
    }

    /**
     * Re-save a revision that was read from storage, preserving the original raw bytes of any
     * unresolved list field. Reads the current stored row's raw fields and passes them to save().
     * If the row no longer exists, falls back to saving with no originalRaw (a new row).
     */
    fun resavePreservingUnresolved(revision: SkillRevision): Long {
        val existing = if (revision.id != 0L) revisionBox.get(revision.id) else null
        val originalRaw = if (existing != null) {
            SkillRevisionEntity.LIST_FIELDS.associateWith { existing.rawListField(it) }
        } else emptyMap()
        return save(revision, originalRaw)
    }

    fun all(): List<SkillRevision> = revisionBox.query().build().find().map { it.toRevision() }

    fun bySkill(skillId: String): List<SkillRevision> =
        all().filter { it.skillId == skillId }.sortedBy { it.version }

    fun latestVersion(skillId: String): Int =
        bySkill(skillId).maxOfOrNull { it.version } ?: 0

    fun delete(revisionId: Long) {
        revisionBox.remove(revisionId)
    }

    /**
     * Rewrite the list columns of existing rows from the legacy newline-join encoding to the
     * [com.ai.assistance.operit.data.learning.StringListCodec] version envelope (D07).
     *
     * There is no schema change: the column names, the Kotlin field names and the ObjectBox
     * property ids are untouched. Only the string CONTENT of rows whose content actually changes is
     * rewritten.
     *
     * Behaviour, per row:
     *  - every list field already versioned → the row is not written, counted in
     *    [MigrationReport.alreadyVersioned];
     *  - at least one legacy field and no corrupt field → the row is rewritten with the migrated
     *    content, counted in [MigrationReport.migrated];
     *  - any corrupt, unsupported-version or ambiguous field → the row is left ENTIRELY untouched,
     *    both in memory and on disk: no field of it is mutated and it is not written, so a partially
     *    migrated row cannot exist. Counted in [MigrationReport.skippedCorrupt] and logged with the
     *    row id and field name.
     *
     * The per-row decision is made up front by the pure [RowMigrationPlanner], which returns either
     * the complete set of values to write or nothing at all; the entity is therefore only mutated
     * when the whole row is eligible.
     *
     * Idempotent: running it twice produces the same bytes and the second run reports every row as
     * already versioned.
     *
     * Rows are processed in bounded batches of [MIGRATION_BATCH_SIZE] inside one ObjectBox
     * transaction per batch, so a large table is never loaded or written in a single transaction.
     *
     * NOT invoked automatically on app start. Invoking it is a separate, device-verified step
     * (A01); this method only provides the explicit path.
     */
    fun migrateListFieldsToVersioned(): MigrationReport {
        var report = MigrationReport()
        var offset = 0L
        while (true) {
            val batch = revisionBox
                .query()
                .order(SkillRevisionEntity_.id)
                .build()
                .find(offset, MIGRATION_BATCH_SIZE.toLong())
            if (batch.isEmpty()) break
            var batchReport = MigrationReport()
            store.runInTx { batchReport = migrateBatch(batch) }
            report += batchReport
            offset += batch.size
        }
        AppLogger.i(
            TAG,
            "SkillRevisionEntity list migration: migrated=${report.migrated} " +
                "alreadyVersioned=${report.alreadyVersioned} skippedCorrupt=${report.skippedCorrupt}"
        )
        return report
    }

    /**
     * Migrate one bounded batch inside the caller's transaction. Returns the counts for the batch.
     *
     * Two-phase per row: the whole row is planned first ([RowMigrationPlanner.plan]) and the entity
     * is mutated only when the plan says the row is eligible. A row with any corrupt, unsupported or
     * ambiguous field is left entirely untouched, so it can never be half-migrated in memory.
     */
    private fun migrateBatch(batch: List<SkillRevisionEntity>): MigrationReport {
        var migrated = 0
        var alreadyVersioned = 0
        var skippedCorrupt = 0
        for (entity in batch) {
            val rawFields = LinkedHashMap<String, String>()
            for (fieldName in SkillRevisionEntity.LIST_FIELDS) {
                rawFields[fieldName] = entity.rawListField(fieldName)
            }
            val plan = RowMigrationPlanner.plan(rawFields)
            when (plan.outcome) {
                RowMigrationPlanner.RowOutcome.NEEDS_ATTENTION -> {
                    for (fieldName in plan.attentionFields) {
                        val detail = StringListMigration
                            .migrateValue(rawFields.getValue(fieldName))
                            .detail
                        AppLogger.e(
                            TAG,
                            "SkillRevisionEntity id=${entity.id} field '$fieldName' needs attention " +
                                "and was left untouched: $detail"
                        )
                    }
                    skippedCorrupt++
                }

                RowMigrationPlanner.RowOutcome.MIGRATED -> {
                    for ((fieldName, value) in plan.fieldValues) {
                        setListField(entity, fieldName, value)
                    }
                    revisionBox.put(entity)
                    migrated++
                }

                RowMigrationPlanner.RowOutcome.ALREADY_VERSIONED -> alreadyVersioned++
            }
        }
        return MigrationReport(
            migrated = migrated,
            alreadyVersioned = alreadyVersioned,
            skippedCorrupt = skippedCorrupt
        )
    }

    private fun setListField(entity: SkillRevisionEntity, fieldName: String, value: String) {
        when (fieldName) {
            SkillRevisionEntity.FIELD_PREREQUISITES -> entity.prerequisitesJoined = value
            SkillRevisionEntity.FIELD_STEPS -> entity.stepsJoined = value
            SkillRevisionEntity.FIELD_CHECKS -> entity.checksJoined = value
            SkillRevisionEntity.FIELD_PITFALLS -> entity.pitfallsJoined = value
            SkillRevisionEntity.FIELD_EVIDENCE_KEYS -> entity.evidenceKeysJoined = value
            SkillRevisionEntity.FIELD_USER_CONSTRAINTS -> entity.userConstraintsJoined = value
            else -> throw IllegalArgumentException("SkillRevisionEntity has no list field '$fieldName'")
        }
    }

    companion object {
        private const val TAG = "SkillRevisionRepository"

        /**
         * Rows per transaction for [migrateListFieldsToVersioned]. Bounded so a large table is never
         * loaded or written in one transaction.
         */
        const val MIGRATION_BATCH_SIZE = 100
    }
}
