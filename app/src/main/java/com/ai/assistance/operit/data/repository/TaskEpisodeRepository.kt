package com.ai.assistance.operit.data.repository

import android.content.Context
import com.ai.assistance.operit.data.db.ObjectBoxManager
import com.ai.assistance.operit.data.learning.TaskEpisodeRetrieval
import com.ai.assistance.operit.data.learning.TaskEpisodeVectorIndex
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
    private val context: Context,
    profileId: String
) {
    private val store = ObjectBoxManager.get(context, profileId)
    private val episodeBox: Box<TaskEpisodeEntity> = store.boxFor()

    /**
     * Additional candidate generator for [retrieveRelevant]: a separate vector namespace keyed by
     * episode id. It is not an ObjectBox entity, so no schema change is involved.
     */
    private val vectorIndex = TaskEpisodeVectorIndex(context, profileId)

    fun save(episode: TaskEpisode): Long {
        return episodeBox.put(TaskEpisodeEntity.fromEpisode(episode))
    }

    /**
     * Persist [episode] and then add it to the episode vector index.
     *
     * The episode is saved FIRST so the id returned by ObjectBox is the id used as the vector index
     * key; the index therefore always refers to a stored episode.
     *
     * The indexed text is the documented concatenation of [TaskEpisode.taskSummary],
     * [TaskEpisode.contextSummary] and [TaskEpisode.resolutionSummary], joined by single spaces.
     * Those three fields are the "what was the task, in what situation, and how was it resolved"
     * triple that vector similarity is meant to capture; failure/cause text is deliberately excluded
     * so a past failure does not pull unrelated failing tasks towards it.
     *
     * When the user has not configured an embedding provider, [TaskEpisodeVectorIndex.indexEpisode]
     * is a documented no-op (its disabled state), so the episode is still saved and simply not
     * indexed.
     *
     * @return the stored episode id.
     */
    suspend fun indexEpisode(episode: TaskEpisode): Long {
        val savedId = save(episode)
        val text = listOf(
            episode.taskSummary,
            episode.contextSummary,
            episode.resolutionSummary
        ).joinToString(" ")
        vectorIndex.indexEpisode(savedId, text)
        return savedId
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
     * Two candidate generators feed one merge rule ([TaskEpisodeRetrieval.mergeCandidates]):
     *  - lexical: the most recent [MAX_CANDIDATES_EXAMINED] episodes by createdAt descending,
     *    scored by [TaskEpisodeRetrieval.scoreEpisodes];
     *  - vector: up to [MAX_CANDIDATES_EXAMINED] episode ids from the episode vector index.
     * The lexical path is a defined member of the union, not a fallback. When the user has not
     * configured an embedding provider the vector generator returns no ids by its documented
     * disabled-state rule and the merge contains exactly the lexical candidates.
     */
    suspend fun retrieveRelevant(query: String, topK: Int = TaskEpisodeRetrieval.DEFAULT_TOP_K): List<TaskEpisode> {
        val lexicalCandidates = recentCandidates()
        val lexical = TaskEpisodeRetrieval.scoreEpisodes(query, lexicalCandidates)
        val vectorIds = vectorIndex.searchEpisodeIds(query, MAX_CANDIDATES_EXAMINED).toSet()
        val vectorCandidates =
            if (vectorIds.isEmpty()) {
                emptyList()
            } else {
                episodeBox.get(vectorIds.toList()).filterNotNull().map { it.toEpisode() }
            }
        return TaskEpisodeRetrieval.mergeCandidates(
            query = query,
            lexical = lexical,
            vectorIds = vectorIds,
            vectorCandidates = vectorCandidates,
            topK = topK
        )
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

    // FOLLOW-UP (C03 part d, NOT implemented): graph expansion over MemoryLink for episodes.
    // MemoryLink.source and MemoryLink.target are ToOne<Memory> (data/model/Memory.kt), and
    // TaskEpisodeEntity has no link field, so an episode cannot be linked to a memory or to another
    // episode without a schema change. Implementing it requires either a new ObjectBox entity
    // (for example EpisodeLinkEntity with sourceEpisodeId, targetEpisodeId and memoryId) or new
    // fields on TaskEpisodeEntity. Encoding ids into MemoryLink.description or type strings would
    // give no referential integrity and no indexed traversal, so it is deliberately not done.
    // The intended edges are: episode -> failure, episode -> fix, episode -> prerequisite.

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
