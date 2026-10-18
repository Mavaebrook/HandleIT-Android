package com.ai.assistance.operit.data.model

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
     *  - a corrupt or unsupported-version value is LOGGED at error level with the row id and the
     *    field name, and contributes an empty list to the returned domain object. It is never
     *    silently swallowed, and the stored row is never rewritten or deleted by a read, so the
     *    original bytes remain available for inspection and for
     *    `SkillRevisionRepository.migrateListFieldsToVersioned()`.
     *
     * Use [decodeListField] to obtain the typed [StringListCodec.DecodedList] for a single field
     * without logging, and [rawListField] to inspect the stored string.
     */
    fun toRevision(): SkillRevision = SkillRevision(
        id = id,
        skillId = skillId,
        version = version,
        parentVersion = parentVersion,
        scope = scope,
        prerequisites = readListField(FIELD_PREREQUISITES, prerequisitesJoined),
        steps = readListField(FIELD_STEPS, stepsJoined),
        checks = readListField(FIELD_CHECKS, checksJoined),
        pitfalls = readListField(FIELD_PITFALLS, pitfallsJoined),
        evidenceKeys = readListField(FIELD_EVIDENCE_KEYS, evidenceKeysJoined),
        userConstraints = readListField(FIELD_USER_CONSTRAINTS, userConstraintsJoined),
        status = status,
        helpfulCount = helpfulCount,
        harmfulCount = harmfulCount,
        createdAtMs = createdAt.time
    )

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
     * [StringListCodec.DecodedList.Corrupt] or [StringListCodec.DecodedList.UnsupportedVersion].
     */
    fun decodeListField(fieldName: String): StringListCodec.DecodedList =
        StringListCodec.decode(rawListField(fieldName))

    /**
     * Decode one list column for [toRevision]. Corruption is logged here, at the entity read
     * boundary, and only then does the field contribute an empty list.
     */
    private fun readListField(fieldName: String, stored: String): List<String> =
        when (val decoded = StringListCodec.decode(stored)) {
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
         */
        fun fromRevision(revision: SkillRevision): SkillRevisionEntity = SkillRevisionEntity(
            id = revision.id,
            skillId = revision.skillId,
            version = revision.version,
            parentVersion = revision.parentVersion,
            scope = revision.scope,
            prerequisitesJoined = StringListCodec.encode(revision.prerequisites),
            stepsJoined = StringListCodec.encode(revision.steps),
            checksJoined = StringListCodec.encode(revision.checks),
            pitfallsJoined = StringListCodec.encode(revision.pitfalls),
            evidenceKeysJoined = StringListCodec.encode(revision.evidenceKeys),
            userConstraintsJoined = StringListCodec.encode(revision.userConstraints),
            status = revision.status,
            helpfulCount = revision.helpfulCount,
            harmfulCount = revision.harmfulCount,
            createdAt = Date(if (revision.createdAtMs > 0L) revision.createdAtMs else System.currentTimeMillis())
        )
    }
}
