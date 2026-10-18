package com.ai.assistance.operit.data.repository

import android.content.Context
import com.ai.assistance.operit.data.db.ObjectBoxManager
import com.ai.assistance.operit.data.learning.TaskEpisodeRetrieval
import com.ai.assistance.operit.data.model.TaskEpisode
import com.ai.assistance.operit.data.model.TaskEpisodeEntity
import com.ai.assistance.operit.data.model.TaskEpisodeEntity_
import io.objectbox.Box
import io.objectbox.kotlin.boxFor
import io.objectbox.kotlin.query
import io.objectbox.query.QueryBuilder

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

    /**
     * Every stored episode, unordered and unbounded.
     *
     * NOT used by retrieval: [retrieveRelevant] reads only the bounded recency window
     * ([recentCandidates]) so the whole table is never loaded for ranking. This method exists for
     * diagnostics and tests only.
     */
    fun all(): List<TaskEpisode> {
        return episodeBox.query().build().find().map { it.toEpisode() }
    }

    fun count(): Long = episodeBox.count()

    /**
     * Bounded similar-task retrieval: at most clampTopK(topK) episodes.
     *
     * Candidates are the most recent [MAX_CANDIDATES_EXAMINED] episodes by createdAt descending,
     * fetched with a bounded ObjectBox query. The whole table is never loaded.
     */
    fun retrieveRelevant(query: String, topK: Int = TaskEpisodeRetrieval.DEFAULT_TOP_K): List<TaskEpisode> {
        return TaskEpisodeRetrieval.rankRelevantEpisodes(query, recentCandidates(), topK)
    }

    /**
     * The bounded candidate window: at most [MAX_CANDIDATES_EXAMINED] most recent episodes.
     */
    private fun recentCandidates(): List<TaskEpisode> {
        return episodeBox
            .query()
            .order(TaskEpisodeEntity_.createdAt, QueryBuilder.DESCENDING)
            .build()
            .find(0, MAX_CANDIDATES_EXAMINED.toLong())
            .map { it.toEpisode() }
    }

    fun delete(episodeId: Long) {
        episodeBox.remove(episodeId)
    }

    companion object {
        /**
         * Maximum number of episodes examined by [retrieveRelevant].
         *
         * Retrieval must not load the whole table: the candidate set is the most recent
         * [MAX_CANDIDATES_EXAMINED] episodes by createdAt descending, and ranking then happens over
         * that bounded set. Trade-off: an older episode outside the recency window is not considered.
         */
        const val MAX_CANDIDATES_EXAMINED = 200
    }
}
