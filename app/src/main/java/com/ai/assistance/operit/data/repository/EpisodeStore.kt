package com.ai.assistance.operit.data.repository

import com.ai.assistance.operit.data.model.TaskEpisode

/**
 * The episode write path the review scheduler depends on.
 *
 * This interface exists so the scheduler's drain logic can be exercised in a plain JVM test:
 * [TaskEpisodeRepository] needs a real ObjectBox store and an Android Context, and this project
 * has no Robolectric. [TaskEpisodeRepository] is the only production implementation.
 */
interface EpisodeStore {
    /**
     * Persist [episode].
     *
     * Unresolved list fields are preserved from the domain object's captured
     * [com.ai.assistance.operit.data.learning.ListFieldIntegrity.rawStored] inside
     * [com.ai.assistance.operit.data.model.TaskEpisodeEntity.fromEpisode]: an undecodable field is
     * written back with its original bytes instead of being re-encoded as an empty envelope. There
     * is no caller-supplied raw map, so a caller cannot erase those bytes.
     */
    fun save(episode: TaskEpisode): Long
    suspend fun indexSavedEpisode(episodeId: Long, episode: TaskEpisode)
}
