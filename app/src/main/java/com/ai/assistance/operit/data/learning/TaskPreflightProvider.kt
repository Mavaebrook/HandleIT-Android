package com.ai.assistance.operit.data.learning

import android.content.Context
import com.ai.assistance.operit.data.repository.TaskEpisodeRepository

/**
 * Bounded similar-task preflight for development/workspace tasks.
 *
 * Retrieval is bounded (top-k clamped to 3-5) and makes NO model call, so it does not add an
 * unconditional per-turn LLM cost. Failures are swallowed so preflight never blocks a task.
 */
object TaskPreflightProvider {

    const val DEFAULT_TOP_K = 3

    fun buildPreflightBlock(
        context: Context,
        profileId: String,
        taskQuery: String,
        currentTaskContext: String,
        useEnglish: Boolean,
        topK: Int = DEFAULT_TOP_K
    ): String {
        if (taskQuery.isBlank()) return ""
        return runCatching {
            val repository = TaskEpisodeRepository(context, profileId)
            val episodes = repository.retrieveRelevant(taskQuery, topK)
            TaskPreflightFormatter.buildPreflightBlock(episodes, currentTaskContext, useEnglish)
        }.getOrDefault("")
    }
}
