package com.ai.assistance.operit.data.repository

import android.content.Context
import com.ai.assistance.operit.data.db.ObjectBoxManager
import com.ai.assistance.operit.data.learning.MigrationReport
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

    fun save(revision: SkillRevision): Long {
        return revisionBox.put(SkillRevisionEntity.fromRevision(revision))
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
     *  - any corrupt or unsupported-version field → the row is NOT written at all (so a partially
     *    migrated row cannot exist), counted in [MigrationReport.skippedCorrupt] and logged with the
     *    row id and field name.
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
            report += store.runInTx { migrateBatch(batch) }
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
     */
    private fun migrateBatch(batch: List<SkillRevisionEntity>): MigrationReport {
        var migrated = 0
        var alreadyVersioned = 0
        var skippedCorrupt = 0
        for (entity in batch) {
            var changed = false
            var needsAttention = false
            for (fieldName in SkillRevisionEntity.LIST_FIELDS) {
                val result = StringListMigration.migrateValue(entity.rawListField(fieldName))
                when (result.outcome) {
                    StringListMigration.Outcome.MIGRATED -> {
                        setListField(entity, fieldName, result.value)
                        changed = true
                    }

                    StringListMigration.Outcome.ALREADY_VERSIONED -> Unit

                    StringListMigration.Outcome.NEEDS_ATTENTION -> {
                        needsAttention = true
                        AppLogger.e(
                            TAG,
                            "SkillRevisionEntity id=${entity.id} field '$fieldName' needs attention " +
                                "and was left untouched: ${result.detail}"
                        )
                    }
                }
            }
            when {
                needsAttention -> skippedCorrupt++
                changed -> {
                    revisionBox.put(entity)
                    migrated++
                }

                else -> alreadyVersioned++
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
