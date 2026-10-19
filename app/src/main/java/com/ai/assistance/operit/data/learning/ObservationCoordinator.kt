package com.ai.assistance.operit.data.learning

import java.util.concurrent.atomic.AtomicLong

/**
 * Shared owner of the Step 1 observation store (Area 2).
 *
 * A single instance is created by `EnhancedAIService` and injected into `ConversationService`, so
 * the store that tool results are WRITTEN to is the same store that preflight READS from. Before
 * this coordinator existed each service owned a separate [TaskContextSnapshotStore], so the reader
 * was always empty and no produced observation could ever reach preflight.
 *
 * ## Session generation
 *
 * The coordinator also owns a monotonic session generation. The intent is that whenever the
 * workspace, chat, memory space, or the underlying shell executor changes (or a PRoot/SSH session is
 * restarted or replaced), the generation is rotated and the store is cleared, so a late tool result
 * from an old generation is rejected rather than attributed to the new scope. Today the generation is
 * only rotated at the session boundaries that are actually wired: execution invalidation (via
 * `cancelConversation`) and chat-instance release *if* a caller invokes it. The other lifecycles
 * (workspace change, shell executor replacement, PRoot/SSH restart) are NOT yet wired.
 *
 * ## What is wired today, and what is not
 *
 * Generation rotation is wired to execution invalidation (`invalidateAllExecutionContexts`, reached
 * from `cancelConversation`) in `EnhancedAIService`. `releaseChatInstance` also calls
 * `rotateGeneration()`, but that method currently has no callers, so chat-instance release is not yet
 * a live rotation point. It is NOT wired to `ShellExecutorFactory.clearCache` (executor replacement)
 * because no caller of that method has access to this coordinator, and it is NOT wired to PRoot/SSH
 * restart/disconnect/replacement because those lifecycles do not exist in this codebase today. The
 * rotation seam is provided here and must be extended when those lifecycles are added. This is a
 * documented limitation, not a claim that every lifecycle is covered.
 *
 * ## Thread safety
 *
 * The store is synchronized internally; the generation is an [AtomicLong].
 */
class ObservationCoordinator(
    val store: TaskContextSnapshotStore = TaskContextSnapshotStore(clock = { System.currentTimeMillis() })
) {
    private val generation = AtomicLong(0L)

    /** The current session generation. A tool result stamped with an older generation is stale. */
    fun currentGeneration(): Long = generation.get()

    /**
     * Rotate the session generation and clear the store. Called at the session boundaries that are
     * wired today (execution invalidation via `cancelConversation`), and available for the other
     * boundaries (workspace change, shell executor replacement, PRoot/SSH restart) once they are
     * wired. Returns the new generation.
     */
    fun rotateGeneration(): Long {
        store.clear()
        return generation.incrementAndGet()
    }

    /** True when [stampedGeneration] is still the current generation. */
    fun isCurrent(stampedGeneration: Long): Boolean = stampedGeneration == generation.get()
}

/**
 * Resolves the effective memory space identically on the observation write side and the preflight
 * read side, so a produced observation and the preflight query agree on the memory-space coordinate.
 *
 * A non-blank override wins; otherwise the active memory space is used. This mirrors the resolution
 * already used by `ConversationService` for the system prompt, so the two sides cannot drift.
 */
object ObservationScopeResolver {
    /**
     * @param override the per-message memory-space override, or null.
     * @param activeMemorySpace the active memory space from preferences.
     * @return the effective memory space: the override when non-blank, otherwise the active space.
     */
    fun resolveMemorySpace(override: String?, activeMemorySpace: String): String =
        override?.takeIf { it.isNotBlank() } ?: activeMemorySpace
}
