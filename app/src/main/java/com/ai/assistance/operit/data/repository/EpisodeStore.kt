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
    fun save(episode: TaskEpisode): Long
    suspend fun indexSavedEpisode(episodeId: Long, episode: TaskEpisode)
}
