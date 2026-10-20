package com.ai.assistance.operit.data.model

import com.ai.assistance.operit.data.learning.ListFieldIntegrity
import com.ai.assistance.operit.data.learning.RowListIntegrity
import com.ai.assistance.operit.data.learning.StringListCodec
import com.ai.assistance.operit.util.AppLogger
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import java.util.Date

/**
 * ObjectBox persistence for [TaskEpisode].
 *
 * The list fields ([requiredPrerequisitesJoined], [applicableConstraintsJoined],
 * [evidenceKeysJoined]) are stored in a single `String` column each, encoded with
 * [StringListCodec] (D07): new writes use the versioned JSON envelope
 * `{"v":1,"items":[...]}`, which is lossless for multiline items, empty items, Unicode, quotes,
 * backslashes, delimiters, order and duplicates. Rows written before D07 hold the old
 * newline-joined encoding and are still read with the exact old semantics.
 *
 * The column names, the Kotlin field names and the ObjectBox property ids are unchanged; only the
 * string CONTENT format of new writes changed. This is a NEW entity; it does not modify any
 * existing entity or ObjectBox id.
 */
@Entity
data class TaskEpisodeEntity(
    @Id var id: Long = 0,
    @Index var taskSummary: String = "",
    var contextSummary: String = "",
    var outcome: String = TaskEpisode.OUTCOME_UNKNOWN,
    var failureSummary: String = "",
    var causeSummary: String = "",
    var causeIsHypothesis: Boolean = false,
    var resolutionSummary: String = "",
    var requiredPrerequisitesJoined: String = "",
    var applicableConstraintsJoined: String = "",
    var evidenceKeysJoined: String = "",
    var relatedSkillVersion: String = "",
    var createdAt: Date = Date()
) {
    /**
     * Read path. Decodes the three list columns with [StringListCodec].
     *
     * Read behaviour, stated explicitly:
     *  - a versioned value decodes to its stored items verbatim;
     *  - a legacy value decodes with the old newline-join semantics (trim + drop empty);
     *  - a corrupt, unsupported-version or ambiguous value is LOGGED at error level with the row id
     *    and the field name, and contributes an empty list to the returned domain object. It is
     *    never silently swallowed, and the stored row is never rewritten or deleted by a read, so
     *    the original bytes remain available for inspection and for
     *    `TaskEpisodeRepository.migrateListFieldsToVersioned()`.
     *
     * The returned [TaskEpisode.listIntegrity] records, per list field, whether the decode was
     * healthy or unresolved. That is what lets a consumer tell a genuine empty list apart from a
     * field whose bytes could not be resolved. The integrity metadata is NOT persisted.
     *
     * Use [decodeListField] to obtain the typed [StringListCodec.DecodedList] for a single field
     * without logging, and [rawListField] to inspect the stored string.
     */
    fun toEpisode(): TaskEpisode {
        val requiredPrerequisites = decodeField(FIELD_REQUIRED_PREREQUISITES, requiredPrerequisitesJoined)
        val applicableConstraints = decodeField(FIELD_APPLICABLE_CONSTRAINTS, applicableConstraintsJoined)
        val evidenceKeys = decodeField(FIELD_EVIDENCE_KEYS, evidenceKeysJoined)
        return TaskEpisode(
            id = id,
            taskSummary = taskSummary,
            contextSummary = contextSummary,
            outcome = outcome,
            failureSummary = failureSummary,
            causeSummary = causeSummary,
            causeIsHypothesis = causeIsHypothesis,
            resolutionSummary = resolutionSummary,
            requiredPrerequisites = requiredPrerequisites.first,
            applicableConstraints = applicableConstraints.first,
            evidenceKeys = evidenceKeys.first,
            relatedSkillVersion = relatedSkillVersion,
            createdAtMs = createdAt.time,
            listIntegrity = RowListIntegrity(
                mapOf(
                    FIELD_REQUIRED_PREREQUISITES to requiredPrerequisites.second,
                    FIELD_APPLICABLE_CONSTRAINTS to applicableConstraints.second,
                    FIELD_EVIDENCE_KEYS to evidenceKeys.second
                )
            )
        )
    }

    /**
     * The raw stored string of one list column, for diagnostics and migration. Never decoded, never
     * modified.
     */
    fun rawListField(fieldName: String): String = when (fieldName) {
        FIELD_REQUIRED_PREREQUISITES -> requiredPrerequisitesJoined
        FIELD_APPLICABLE_CONSTRAINTS -> applicableConstraintsJoined
        FIELD_EVIDENCE_KEYS -> evidenceKeysJoined
        else -> throw IllegalArgumentException("TaskEpisodeEntity has no list field '$fieldName'")
    }

    /**
     * Typed decode of one list column, without logging. The caller decides how to surface a
     * [StringListCodec.DecodedList.Corrupt], [StringListCodec.DecodedList.UnsupportedVersion] or
     * [StringListCodec.DecodedList.Ambiguous].
     */
    fun decodeListField(fieldName: String): StringListCodec.DecodedList =
        StringListCodec.decode(rawListField(fieldName))

    /**
     * Decode one list column for [toEpisode].
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
                    "Corrupt list field '$fieldName' on TaskEpisodeEntity id=$id " +
                        "(stored value left untouched): ${decoded.reason}"
                )
                emptyList()
            }
            is StringListCodec.DecodedList.UnsupportedVersion -> {
                AppLogger.e(
                    TAG,
                    "Unsupported list envelope version ${decoded.version} in field '$fieldName' " +
                        "on TaskEpisodeEntity id=$id (stored value left untouched)"
                )
                emptyList()
            }
            is StringListCodec.DecodedList.Ambiguous -> {
                AppLogger.e(
                    TAG,
                    "Ambiguous list field '$fieldName' on TaskEpisodeEntity id=$id " +
                        "(stored value left untouched): ${decoded.reason}"
                )
                emptyList()
            }
        }
        return items to integrity
    }

    companion object {
        private const val TAG = "TaskEpisodeEntity"

        /** Stored column name of [requiredPrerequisitesJoined]. */
        const val FIELD_REQUIRED_PREREQUISITES = "requiredPrerequisitesJoined"

        /** Stored column name of [applicableConstraintsJoined]. */
        const val FIELD_APPLICABLE_CONSTRAINTS = "applicableConstraintsJoined"

        /** Stored column name of [evidenceKeysJoined]. */
        const val FIELD_EVIDENCE_KEYS = "evidenceKeysJoined"

        /** Every list column of this entity, in declaration order. */
        val LIST_FIELDS: List<String> = listOf(
            FIELD_REQUIRED_PREREQUISITES,
            FIELD_APPLICABLE_CONSTRAINTS,
            FIELD_EVIDENCE_KEYS
        )

        /**
         * Write path. Encodes the three list columns with [StringListCodec.encode], which is
         * lossless: no trimming, no filtering, no de-duplication.
         *
         * ## Preservation of unresolved fields
         *
         * An episode read from a row with an unresolved list field carries `emptyList()` for that
         * field, because the bytes could not be decoded. Blindly re-encoding it would write
         * `{"v":1,"items":[]}` and DESTROY the original bytes. Therefore, for any field whose
         * [TaskEpisode.listIntegrity] entry is unresolved, the raw stored string captured at read
         * time in [ListFieldIntegrity.rawStored] is written back VERBATIM (no re-encode).
         *
         * This is an invariant of the write path, not a caller responsibility: no separately
         * supplied raw map exists, so a caller cannot accidentally (or deliberately) erase the
         * bytes of an unresolved field. Use [hasUnresolvedFields] to detect such a row.
         */
        fun fromEpisode(episode: TaskEpisode): TaskEpisodeEntity = TaskEpisodeEntity(
            id = episode.id,
            taskSummary = episode.taskSummary,
            contextSummary = episode.contextSummary,
            outcome = episode.outcome,
            failureSummary = episode.failureSummary,
            causeSummary = episode.causeSummary,
            causeIsHypothesis = episode.causeIsHypothesis,
            resolutionSummary = episode.resolutionSummary,
            requiredPrerequisitesJoined = encodeField(
                FIELD_REQUIRED_PREREQUISITES,
                episode.requiredPrerequisites,
                episode.listIntegrity
            ),
            applicableConstraintsJoined = encodeField(
                FIELD_APPLICABLE_CONSTRAINTS,
                episode.applicableConstraints,
                episode.listIntegrity
            ),
            evidenceKeysJoined = encodeField(
                FIELD_EVIDENCE_KEYS,
                episode.evidenceKeys,
                episode.listIntegrity
            ),
            relatedSkillVersion = episode.relatedSkillVersion,
            createdAt = Date(if (episode.createdAtMs > 0L) episode.createdAtMs else System.currentTimeMillis())
        )

        /**
         * Encode one list column for [fromEpisode].
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
                // An unresolved field must always carry the raw bytes captured at read time. If rawStored
                // is empty here the integrity metadata was hand-constructed without its bytes; writing an
                // empty column would silently erase the unresolved field. Fail loudly instead of erasing.
                require(fieldIntegrity.rawStored.isNotEmpty()) {
                    "unresolved field '$fieldName' has no captured raw bytes; refusing to write an empty column"
                }
                return fieldIntegrity.rawStored
            }
            return StringListCodec.encode(values)
        }

        /** True when [episode] carries any unresolved list field; such a row must not be blindly re-saved. */
        fun hasUnresolvedFields(episode: TaskEpisode): Boolean = episode.listIntegrity.hasUnresolved
    }
}
