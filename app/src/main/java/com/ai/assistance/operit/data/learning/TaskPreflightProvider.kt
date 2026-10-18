package com.ai.assistance.operit.data.learning

import android.content.Context
import com.ai.assistance.operit.data.model.TaskEpisode
import com.ai.assistance.operit.data.repository.TaskEpisodeRepository
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CancellationException

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
     * Result of building the preflight context: the bounded text block plus an optional typed
     * snapshot of prior observations (Step 1, Area 2). The snapshot is advisory and does NOT gate
     * execution; it is passed through preflight plumbing alongside the text block.
     */
    data class PreflightContext(
        val textBlock: String,
        val snapshot: TaskContextSnapshot?
    )

    /**
     * Build the bounded preflight block for the current task.
     *
     * Suspending because retrieval now merges two candidate generators: the lexical path and the
     * episode vector index ([TaskEpisodeVectorIndex]), and the vector path performs a suspending
     * embedding request. When no embedding provider is configured the vector generator returns no
     * ids by its documented disabled-state rule and the merge contains exactly the lexical
     * candidates.
     *
     * This function only wires the real [TaskEpisodeRepository] retrieval into
     * [buildPreflightBlockFrom], which holds the retrieval-and-format core (and the cancellation
     * rule) so it is unit-testable without an ObjectBox store or an Android Context.
     *
     * @param taskQuery the user request text used for retrieval ranking.
     * @param currentTaskContext the actual task context (request text plus a bounded recent-turn
     *        excerpt) used for prerequisite applicability and satisfaction checks.
     * @return the formatted block, or "" when there is nothing to show. An empty string is the
     *         defined "no block produced" result; a retrieval failure is logged via
     *         [AppLogger.e] and also yields "". Cancellation is never converted into "".
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
        return buildPreflightBlockFrom(
            retrieve = { query, k -> TaskEpisodeRepository(context, profileId).retrieveRelevant(query, k) },
            taskQuery = taskQuery,
            currentTaskContext = currentTaskContext,
            useEnglish = useEnglish,
            topK = topK
        )
    }

    /**
     * Build the preflight context: the bounded text block (via [buildPreflightBlock]) plus a typed
     * [TaskContextSnapshot] taken from [snapshotStore] for the given scope coordinates.
     *
     * The snapshot is built from PRIOR completed events only: the current turn's preflight cannot
     * use future results. When [snapshotStore] is null, or the task query is blank, the snapshot is
     * null and only the text block is produced. This adds NO model call.
     */
    suspend fun buildPreflightContext(
        context: Context,
        profileId: String,
        taskQuery: String,
        currentTaskContext: String,
        useEnglish: Boolean,
        snapshotStore: TaskContextSnapshotStore?,
        projectScope: String = "",
        taskScope: String = "",
        executorId: String = "",
        environment: String = "",
        sessionId: String = "",
        topK: Int = DEFAULT_TOP_K
    ): PreflightContext {
        val textBlock = buildPreflightBlock(
            context = context,
            profileId = profileId,
            taskQuery = taskQuery,
            currentTaskContext = currentTaskContext,
            useEnglish = useEnglish,
            topK = topK
        )
        val snapshot = snapshotStore?.snapshotFor(
            memorySpace = profileId,
            projectScope = projectScope,
            taskScope = taskScope,
            executorId = executorId,
            environment = environment,
            sessionId = sessionId
        )
        return PreflightContext(textBlock = textBlock, snapshot = snapshot)
    }

    /**
     * The retrieval-and-format core of [buildPreflightBlock], with retrieval injected so the
     * cancellation rule below can be exercised in a plain JVM test (the real repository needs an
     * ObjectBox store and an Android Context, and this project has no Robolectric).
     *
     * CANCELLATION RULE: [CancellationException] is rethrown before the generic handler. Catching
     * it here would break structured concurrency: the caller's coroutine would keep running after
     * being cancelled, and the cancellation would be reported as an ordinary retrieval failure.
     * Only genuine failures are logged and turned into the documented "" no-block result.
     */
    internal suspend fun buildPreflightBlockFrom(
        retrieve: suspend (String, Int) -> List<TaskEpisode>,
        taskQuery: String,
        currentTaskContext: String,
        useEnglish: Boolean,
        topK: Int
    ): String {
        return try {
            val episodes = retrieve(taskQuery, topK)
            TaskPreflightFormatter.buildPreflightBlock(episodes, currentTaskContext, useEnglish)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            // The failure is logged here; "" is the explicit "no block produced" result, which is
            // the defined no-op for preflight (no block is injected into the system prompt).
            AppLogger.e(TAG, "Failed to build task preflight block", e)
            ""
        }
    }
}
