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
     * [originalRaw] maps a list field name to the raw stored string of the row being re-saved. It
     * must be supplied when re-saving a row read with unresolved list integrity, so the original
     * bytes of an undecodable field are preserved instead of being re-encoded as an empty envelope.
     * New (healthy) episodes use the default empty map.
     */
    fun save(episode: TaskEpisode, originalRaw: Map<String, String> = emptyMap()): Long
    suspend fun indexSavedEpisode(episodeId: Long, episode: TaskEpisode)
}
