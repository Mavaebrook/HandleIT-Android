package com.ai.assistance.operit.data.model

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
     *  - a corrupt or unsupported-version value is LOGGED at error level with the row id and the
     *    field name, and contributes an empty list to the returned domain object. It is never
     *    silently swallowed, and the stored row is never rewritten or deleted by a read, so the
     *    original bytes remain available for inspection and for
     *    `TaskEpisodeRepository.migrateListFieldsToVersioned()`.
     *
     * Use [decodeListField] to obtain the typed [StringListCodec.DecodedList] for a single field
     * without logging, and [rawListField] to inspect the stored string.
     */
    fun toEpisode(): TaskEpisode = TaskEpisode(
        id = id,
        taskSummary = taskSummary,
        contextSummary = contextSummary,
        outcome = outcome,
        failureSummary = failureSummary,
        causeSummary = causeSummary,
        causeIsHypothesis = causeIsHypothesis,
        resolutionSummary = resolutionSummary,
        requiredPrerequisites = readListField(FIELD_REQUIRED_PREREQUISITES, requiredPrerequisitesJoined),
        applicableConstraints = readListField(FIELD_APPLICABLE_CONSTRAINTS, applicableConstraintsJoined),
        evidenceKeys = readListField(FIELD_EVIDENCE_KEYS, evidenceKeysJoined),
        relatedSkillVersion = relatedSkillVersion,
        createdAtMs = createdAt.time
    )

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
     * [StringListCodec.DecodedList.Corrupt] or [StringListCodec.DecodedList.UnsupportedVersion].
     */
    fun decodeListField(fieldName: String): StringListCodec.DecodedList =
        StringListCodec.decode(rawListField(fieldName))

    /**
     * Decode one list column for [toEpisode]. Corruption is logged here, at the entity read
     * boundary, and only then does the field contribute an empty list.
     */
    private fun readListField(fieldName: String, stored: String): List<String> =
        when (val decoded = StringListCodec.decode(stored)) {
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
            requiredPrerequisitesJoined = StringListCodec.encode(episode.requiredPrerequisites),
            applicableConstraintsJoined = StringListCodec.encode(episode.applicableConstraints),
            evidenceKeysJoined = StringListCodec.encode(episode.evidenceKeys),
            relatedSkillVersion = episode.relatedSkillVersion,
            createdAt = Date(if (episode.createdAtMs > 0L) episode.createdAtMs else System.currentTimeMillis())
        )
    }
}
