package com.ai.assistance.operit.data.model

import com.ai.assistance.operit.data.learning.SkillRevision
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import java.util.Date

/**
 * ObjectBox persistence for [SkillRevision]. List fields are stored as newline-joined strings.
 * This is a NEW entity; it does not modify any existing entity or ObjectBox id.
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
    fun toRevision(): SkillRevision = SkillRevision(
        id = id,
        skillId = skillId,
        version = version,
        parentVersion = parentVersion,
        scope = scope,
        prerequisites = splitJoined(prerequisitesJoined),
        steps = splitJoined(stepsJoined),
        checks = splitJoined(checksJoined),
        pitfalls = splitJoined(pitfallsJoined),
        evidenceKeys = splitJoined(evidenceKeysJoined),
        userConstraints = splitJoined(userConstraintsJoined),
        status = status,
        helpfulCount = helpfulCount,
        harmfulCount = harmfulCount,
        createdAtMs = createdAt.time
    )

    companion object {
        fun fromRevision(revision: SkillRevision): SkillRevisionEntity = SkillRevisionEntity(
            id = revision.id,
            skillId = revision.skillId,
            version = revision.version,
            parentVersion = revision.parentVersion,
            scope = revision.scope,
            prerequisitesJoined = joinList(revision.prerequisites),
            stepsJoined = joinList(revision.steps),
            checksJoined = joinList(revision.checks),
            pitfallsJoined = joinList(revision.pitfalls),
            evidenceKeysJoined = joinList(revision.evidenceKeys),
            userConstraintsJoined = joinList(revision.userConstraints),
            status = revision.status,
            helpfulCount = revision.helpfulCount,
            harmfulCount = revision.harmfulCount,
            createdAt = Date(if (revision.createdAtMs > 0L) revision.createdAtMs else System.currentTimeMillis())
        )

        private fun joinList(values: List<String>): String =
            values.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")

        private fun splitJoined(value: String): List<String> =
            value.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    }
}
