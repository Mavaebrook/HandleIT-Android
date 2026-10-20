package com.ai.assistance.operit.data.model

import com.ai.assistance.operit.data.learning.ListFieldIntegrity
import com.ai.assistance.operit.data.learning.RowListIntegrity
import com.ai.assistance.operit.data.learning.SkillRevision
import com.ai.assistance.operit.data.learning.StringListCodec
import com.ai.assistance.operit.util.AppLogger
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import java.util.Date

/**
 * ObjectBox persistence for [SkillRevision].
 *
 * The list fields ([prerequisitesJoined], [stepsJoined], [checksJoined], [pitfallsJoined],
 * [evidenceKeysJoined], [userConstraintsJoined]) are stored in a single `String` column each,
 * encoded with [StringListCodec] (D07): new writes use the versioned JSON envelope
 * `{"v":1,"items":[...]}`, which is lossless for multiline items, empty items, Unicode, quotes,
 * backslashes, delimiters, order and duplicates. Rows written before D07 hold the old
 * newline-joined encoding and are still read with the exact old semantics.
 *
 * The column names, the Kotlin field names and the ObjectBox property ids are unchanged; only the
 * string CONTENT format of new writes changed. This is a NEW entity; it does not modify any
 * existing entity or ObjectBox id.
 */
@Entity
data class SkillRevisionEntity(
    @Id var id: Long = 0,
    @Index var skillId: String = "",
    var version: Int = 1,
    var parentVersion: Int = 0,
    var scope: String = "",
    var prerequisitesJoined: String = "",
    var stepsJoined: String = "",
    var checksJoined: String = "",
    var pitfallsJoined: String = "",
    var evidenceKeysJoined: String = "",
    var userConstraintsJoined: String = "",
    var status: String = SkillRevision.STATUS_DRAFT,
    var helpfulCount: Int = 0,
    var harmfulCount: Int = 0,
    var createdAt: Date = Date()
) {
    /**
     * Read path. Decodes the six list columns with [StringListCodec].
     *
     * Read behaviour, stated explicitly:
     *  - a versioned value decodes to its stored items verbatim;
     *  - a legacy value decodes with the old newline-join semantics (trim + drop empty);
     *  - a corrupt, unsupported-version or ambiguous value is LOGGED at error level with the row id
     *    and the field name, and contributes an empty list to the returned domain object. It is
     *    never silently swallowed, and the stored row is never rewritten or deleted by a read, so
     *    the original bytes remain available for inspection and for
     *    `SkillRevisionRepository.migrateListFieldsToVersioned()`.
     *
     * The returned [SkillRevision.listIntegrity] records, per list field, whether the decode was
     * healthy or unresolved. That is what lets a consumer tell a genuine empty list apart from a
     * field whose bytes could not be resolved. The integrity metadata is NOT persisted.
     *
     * Use [decodeListField] to obtain the typed [StringListCodec.DecodedList] for a single field
     * without logging, and [rawListField] to inspect the stored string.
     */
    fun toRevision(): SkillRevision {
        val prerequisites = decodeField(FIELD_PREREQUISITES, prerequisitesJoined)
        val steps = decodeField(FIELD_STEPS, stepsJoined)
        val checks = decodeField(FIELD_CHECKS, checksJoined)
        val pitfalls = decodeField(FIELD_PITFALLS, pitfallsJoined)
        val evidenceKeys = decodeField(FIELD_EVIDENCE_KEYS, evidenceKeysJoined)
        val userConstraints = decodeField(FIELD_USER_CONSTRAINTS, userConstraintsJoined)
        return SkillRevision(
            id = id,
            skillId = skillId,
            version = version,
            parentVersion = parentVersion,
            scope = scope,
            prerequisites = prerequisites.first,
            steps = steps.first,
            checks = checks.first,
            pitfalls = pitfalls.first,
            evidenceKeys = evidenceKeys.first,
            userConstraints = userConstraints.first,
            status = status,
            helpfulCount = helpfulCount,
            harmfulCount = harmfulCount,
            createdAtMs = createdAt.time,
            listIntegrity = RowListIntegrity(
                mapOf(
                    FIELD_PREREQUISITES to prerequisites.second,
                    FIELD_STEPS to steps.second,
                    FIELD_CHECKS to checks.second,
                    FIELD_PITFALLS to pitfalls.second,
                    FIELD_EVIDENCE_KEYS to evidenceKeys.second,
                    FIELD_USER_CONSTRAINTS to userConstraints.second
                )
            )
        )
    }

    /**
     * The raw stored string of one list column, for diagnostics and migration. Never decoded, never
     * modified.
     */
    fun rawListField(fieldName: String): String = when (fieldName) {
        FIELD_PREREQUISITES -> prerequisitesJoined
        FIELD_STEPS -> stepsJoined
        FIELD_CHECKS -> checksJoined
        FIELD_PITFALLS -> pitfallsJoined
        FIELD_EVIDENCE_KEYS -> evidenceKeysJoined
        FIELD_USER_CONSTRAINTS -> userConstraintsJoined
        else -> throw IllegalArgumentException("SkillRevisionEntity has no list field '$fieldName'")
    }

    /**
     * Typed decode of one list column, without logging. The caller decides how to surface a
     * [StringListCodec.DecodedList.Corrupt], [StringListCodec.DecodedList.UnsupportedVersion] or
     * [StringListCodec.DecodedList.Ambiguous].
     */
    fun decodeListField(fieldName: String): StringListCodec.DecodedList =
        StringListCodec.decode(rawListField(fieldName))

    /**
     * Decode one list column for [toRevision].
     *
     * Returns the decoded items (empty for an unresolved field) paired with the field's
     * [ListFieldIntegrity]. Corruption is logged here, at the entity read boundary, and only then
     * does the field contribute an empty list. The log line carries the field name, the row id and
     * the short non-sensitive reason — NEVER the raw stored value, which is untrusted.
     */
    private fun decodeField(
        fieldName: String,
        stored: String
    ): Pair<List<String>, ListFieldIntegrity> {
        val decoded = StringListCodec.decode(stored)
        val integrity = ListFieldIntegrity.fromDecoded(fieldName, decoded)
        val items = when (decoded) {
            is StringListCodec.DecodedList.Versioned -> decoded.items
            is StringListCodec.DecodedList.Legacy -> decoded.items
            is StringListCodec.DecodedList.Corrupt -> {
                AppLogger.e(
                    TAG,
                    "Corrupt list field '$fieldName' on SkillRevisionEntity id=$id " +
                        "(stored value left untouched): ${decoded.reason}"
                )
                emptyList()
            }
            is StringListCodec.DecodedList.UnsupportedVersion -> {
                AppLogger.e(
                    TAG,
                    "Unsupported list envelope version ${decoded.version} in field '$fieldName' " +
                        "on SkillRevisionEntity id=$id (stored value left untouched)"
                )
                emptyList()
            }
            is StringListCodec.DecodedList.Ambiguous -> {
                AppLogger.e(
                    TAG,
                    "Ambiguous list field '$fieldName' on SkillRevisionEntity id=$id " +
                        "(stored value left untouched): ${decoded.reason}"
                )
                emptyList()
            }
        }
        return items to integrity
    }

    companion object {
        private const val TAG = "SkillRevisionEntity"

        /** Stored column name of [prerequisitesJoined]. */
        const val FIELD_PREREQUISITES = "prerequisitesJoined"

        /** Stored column name of [stepsJoined]. */
        const val FIELD_STEPS = "stepsJoined"

        /** Stored column name of [checksJoined]. */
        const val FIELD_CHECKS = "checksJoined"

        /** Stored column name of [pitfallsJoined]. */
        const val FIELD_PITFALLS = "pitfallsJoined"

        /** Stored column name of [evidenceKeysJoined]. */
        const val FIELD_EVIDENCE_KEYS = "evidenceKeysJoined"

        /** Stored column name of [userConstraintsJoined]. */
        const val FIELD_USER_CONSTRAINTS = "userConstraintsJoined"

        /** Every list column of this entity, in declaration order. */
        val LIST_FIELDS: List<String> = listOf(
            FIELD_PREREQUISITES,
            FIELD_STEPS,
            FIELD_CHECKS,
            FIELD_PITFALLS,
            FIELD_EVIDENCE_KEYS,
            FIELD_USER_CONSTRAINTS
        )

        /**
         * Write path. Encodes the six list columns with [StringListCodec.encode], which is
         * lossless: no trimming, no filtering, no de-duplication.
         *
         * ## Preservation of unresolved fields
         *
         * A revision read from a row with an unresolved list field carries `emptyList()` for that
         * field, because the bytes could not be decoded. Blindly re-encoding it would write
         * `{"v":1,"items":[]}` and DESTROY the original bytes. Therefore, for any field whose
         * [SkillRevision.listIntegrity] entry is unresolved, the raw stored string captured at read
         * time in [ListFieldIntegrity.rawStored] is written back VERBATIM (no re-encode).
         *
         * This is an invariant of the write path, not a caller responsibility: no separately
         * supplied raw map exists, so a caller cannot accidentally (or deliberately) erase the
         * bytes of an unresolved field. Use [hasUnresolvedFields] to detect such a row.
         */
        fun fromRevision(revision: SkillRevision): SkillRevisionEntity = SkillRevisionEntity(
            id = revision.id,
            skillId = revision.skillId,
            version = revision.version,
            parentVersion = revision.parentVersion,
            scope = revision.scope,
            prerequisitesJoined = encodeField(
                FIELD_PREREQUISITES,
                revision.prerequisites,
                revision.listIntegrity
            ),
            stepsJoined = encodeField(
                FIELD_STEPS,
                revision.steps,
                revision.listIntegrity
            ),
            checksJoined = encodeField(
                FIELD_CHECKS,
                revision.checks,
                revision.listIntegrity
            ),
            pitfallsJoined = encodeField(
                FIELD_PITFALLS,
                revision.pitfalls,
                revision.listIntegrity
            ),
            evidenceKeysJoined = encodeField(
                FIELD_EVIDENCE_KEYS,
                revision.evidenceKeys,
                revision.listIntegrity
            ),
            userConstraintsJoined = encodeField(
                FIELD_USER_CONSTRAINTS,
                revision.userConstraints,
                revision.listIntegrity
            ),
            status = revision.status,
            helpfulCount = revision.helpfulCount,
            harmfulCount = revision.harmfulCount,
            createdAt = Date(if (revision.createdAtMs > 0L) revision.createdAtMs else System.currentTimeMillis())
        )

        /**
         * Encode one list column for [fromRevision].
         *
         * DELIBERATE INVARIANT: an unresolved field (corrupt, unsupported version, or ambiguous
         * provenance) is written back with the raw bytes captured at read time in
         * [ListFieldIntegrity.rawStored]. The decoded list for such a field is empty for a reason
         * unrelated to the data, so re-encoding it would silently erase the original bytes. A caller
         * cannot override this with a separately supplied map: explicit resolution requires a
         * resolved (healthy) integrity state, not a raw override.
         */
        private fun encodeField(
            fieldName: String,
            values: List<String>,
            integrity: RowListIntegrity
        ): String {
            val fieldIntegrity = integrity.fields[fieldName]
            if (fieldIntegrity != null && fieldIntegrity.isUnresolved) {
                return fieldIntegrity.rawStored
            }
            return StringListCodec.encode(values)
        }

        /** True when [revision] carries any unresolved list field; such a row must not be blindly re-saved. */
        fun hasUnresolvedFields(revision: SkillRevision): Boolean = revision.listIntegrity.hasUnresolved
    }
}
