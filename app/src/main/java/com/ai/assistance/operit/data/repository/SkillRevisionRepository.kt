package com.ai.assistance.operit.data.repository

import android.content.Context
import com.ai.assistance.operit.data.db.ObjectBoxManager
import com.ai.assistance.operit.data.learning.SkillRevision
import com.ai.assistance.operit.data.model.SkillRevisionEntity
import io.objectbox.Box
import io.objectbox.kotlin.boxFor

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
}
