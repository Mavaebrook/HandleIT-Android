package com.ai.assistance.operit.data.repository

import android.content.Context
import com.ai.assistance.operit.data.db.ObjectBoxManager
import com.ai.assistance.operit.data.learning.TaskEpisodeRetrieval
import com.ai.assistance.operit.data.model.TaskEpisode
import com.ai.assistance.operit.data.model.TaskEpisodeEntity
import io.objectbox.Box
import io.objectbox.kotlin.boxFor

/**
 * Persistence for structured task episodes. Bounded retrieval is delegated to the pure
 * [TaskEpisodeRetrieval] core so ranking/dedup/applicability stay unit-testable.
 */
class TaskEpisodeRepository(
    context: Context,
    profileId: String
) {
    private val store = ObjectBoxManager.get(context, profileId)
    private val episodeBox: Box<TaskEpisodeEntity> = store.boxFor()

    fun save(episode: TaskEpisode): Long {
        return episodeBox.put(TaskEpisodeEntity.fromEpisode(episode))
    }

    fun all(): List<TaskEpisode> {
        return episodeBox.query().build().find().map { it.toEpisode() }
    }

    fun count(): Long = episodeBox.count()

    /** Bounded similar-task retrieval: at most clampTopK(topK) episodes. */
    fun retrieveRelevant(query: String, topK: Int = TaskEpisodeRetrieval.DEFAULT_TOP_K): List<TaskEpisode> {
        return TaskEpisodeRetrieval.rankRelevantEpisodes(query, all(), topK)
    }

    fun delete(episodeId: Long) {
        episodeBox.remove(episodeId)
    }
}
