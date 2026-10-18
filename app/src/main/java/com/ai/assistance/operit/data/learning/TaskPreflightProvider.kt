package com.ai.assistance.operit.data.learning

import android.content.Context
import com.ai.assistance.operit.data.repository.TaskEpisodeRepository
import com.ai.assistance.operit.util.AppLogger

/**
 * Bounded similar-task preflight for development/workspace tasks.
 *
 * Retrieval is bounded (top-k clamped to 3-5) and makes NO model call, so it does not add an
 * unconditional per-turn LLM cost. A retrieval failure is logged and yields the defined no-op
 * result (an empty block), so preflight never blocks a task.
 */
object TaskPreflightProvider {

    private const val TAG = "TaskPreflightProvider"

    const val DEFAULT_TOP_K = 3

    /**
     * Build the bounded preflight block for the current task.
     *
     * Suspending because retrieval now merges two candidate generators: the lexical path and the
     * episode vector index ([TaskEpisodeVectorIndex]), and the vector path performs a suspending
     * embedding request. When no embedding provider is configured the vector generator returns no
     * ids by its documented disabled-state rule and the merge contains exactly the lexical
     * candidates.
     *
     * @param taskQuery the user request text used for retrieval ranking.
     * @param currentTaskContext the actual task context (request text plus a bounded recent-turn
     *        excerpt) used for prerequisite applicability and satisfaction checks.
     * @return the formatted block, or "" when there is nothing to show. An empty string is the
     *         defined "no block produced" result; a retrieval failure is logged via
     *         [AppLogger.e] and also yields "".
     */
    suspend fun buildPreflightBlock(
        context: Context,
        profileId: String,
        taskQuery: String,
        currentTaskContext: String,
        useEnglish: Boolean,
        topK: Int = DEFAULT_TOP_K
    ): String {
        if (taskQuery.isBlank()) return ""
        return try {
            val repository = TaskEpisodeRepository(context, profileId)
            val episodes = repository.retrieveRelevant(taskQuery, topK)
            TaskPreflightFormatter.buildPreflightBlock(episodes, currentTaskContext, useEnglish)
        } catch (e: Exception) {
            // The failure is logged here; "" is the explicit "no block produced" result, which is
            // the defined no-op for preflight (no block is injected into the system prompt).
            AppLogger.e(TAG, "Failed to build task preflight block", e)
            ""
        }
    }
}
