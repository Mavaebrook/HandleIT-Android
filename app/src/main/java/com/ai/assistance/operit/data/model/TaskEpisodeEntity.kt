package com.ai.assistance.operit.data.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import java.util.Date

/**
 * ObjectBox persistence for [TaskEpisode]. List fields are stored as newline-joined strings.
 * This is a NEW entity; it does not modify any existing entity or ObjectBox id.
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
    fun toEpisode(): TaskEpisode = TaskEpisode(
        id = id,
        taskSummary = taskSummary,
        contextSummary = contextSummary,
        outcome = outcome,
        failureSummary = failureSummary,
        causeSummary = causeSummary,
        causeIsHypothesis = causeIsHypothesis,
        resolutionSummary = resolutionSummary,
        requiredPrerequisites = splitJoined(requiredPrerequisitesJoined),
        applicableConstraints = splitJoined(applicableConstraintsJoined),
        evidenceKeys = splitJoined(evidenceKeysJoined),
        relatedSkillVersion = relatedSkillVersion,
        createdAtMs = createdAt.time
    )

    companion object {
        fun fromEpisode(episode: TaskEpisode): TaskEpisodeEntity = TaskEpisodeEntity(
            id = episode.id,
            taskSummary = episode.taskSummary,
            contextSummary = episode.contextSummary,
            outcome = episode.outcome,
            failureSummary = episode.failureSummary,
            causeSummary = episode.causeSummary,
            causeIsHypothesis = episode.causeIsHypothesis,
            resolutionSummary = episode.resolutionSummary,
            requiredPrerequisitesJoined = joinList(episode.requiredPrerequisites),
            applicableConstraintsJoined = joinList(episode.applicableConstraints),
            evidenceKeysJoined = joinList(episode.evidenceKeys),
            relatedSkillVersion = episode.relatedSkillVersion,
            createdAt = Date(if (episode.createdAtMs > 0L) episode.createdAtMs else System.currentTimeMillis())
        )

        private fun joinList(values: List<String>): String =
            values.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")

        private fun splitJoined(value: String): List<String> =
            value.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    }
}
