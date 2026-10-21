package com.ai.assistance.operit.api.chat.library

/**
 * Pure novelty comparison for memory content.
 *
 * Memory reinforcement (occurrence counting) must only fire when the stored content genuinely
 * changed. A raw string inequality check treats cosmetic edits — trailing/leading whitespace,
 * re-wrapped lines, collapsed whitespace runs, or case-only changes — as novelty, which inflates
 * occurrence counts without any new information being learned.
 *
 * This object normalizes content before comparing so that only real content changes count as
 * novel. It is deliberately pure: no embeddings, no model calls, no I/O, no Android/ObjectBox
 * dependencies.
 */
object MemoryContentNovelty {

    private val WHITESPACE_RUN = Regex("\\s+")

    /**
     * Normalize content for novelty comparison: trim, collapse internal whitespace runs to a
     * single space, normalize line breaks (\r\n and \r to \n) before collapsing, and compare
     * case-insensitively.
     */
    fun normalize(content: String): String {
        return content
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .trim()
            .replace(WHITESPACE_RUN, " ")
            .lowercase()
    }

    /** True when [newContent] is genuinely novel relative to [previousContent] after normalization. */
    fun isNovel(previousContent: String, newContent: String): Boolean =
        normalize(previousContent) != normalize(newContent)
}
