package com.ai.assistance.operit.api.chat.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM regression tests for [MemoryContentNovelty].
 *
 * These cover the D04/R10 acceptance criteria: memory reinforcement must fire only on genuine
 * content novelty. Cosmetic edits — trailing/leading whitespace, re-wrapped lines, collapsed
 * whitespace runs, and case-only changes — must NOT count as novelty, while real content changes
 * must.
 */
class MemoryContentNoveltyTest {

    // Covers: a whitespace-only edit (trailing space) is NOT novel.
    @Test
    fun isNovel_trailingSpaceOnly_isNotNovel() {
        assertFalse(MemoryContentNovelty.isNovel("user prefers dark mode", "user prefers dark mode "))
    }

    // Covers: a whitespace-only edit (leading space) is NOT novel.
    @Test
    fun isNovel_leadingSpaceOnly_isNotNovel() {
        assertFalse(MemoryContentNovelty.isNovel("user prefers dark mode", "  user prefers dark mode"))
    }

    // Covers: a re-wrapped edit (same words, different line breaks) is NOT novel.
    @Test
    fun isNovel_rewrappedWithNewlines_isNotNovel() {
        val previous = "user prefers dark mode and large fonts"
        val rewrapped = "user prefers dark mode\nand large fonts"

        assertFalse(MemoryContentNovelty.isNovel(previous, rewrapped))
    }

    // Covers: \r\n vs \n line endings are NOT novel.
    @Test
    fun isNovel_crlfVersusLf_isNotNovel() {
        val previous = "line one\nline two"
        val crlf = "line one\r\nline two"

        assertFalse(MemoryContentNovelty.isNovel(previous, crlf))
    }

    // Covers: collapsed internal whitespace runs (multiple spaces/tabs -> single space) are NOT novel.
    @Test
    fun isNovel_collapsedWhitespaceRuns_isNotNovel() {
        val previous = "user prefers dark mode"
        val padded = "user   prefers\t\tdark    mode"

        assertFalse(MemoryContentNovelty.isNovel(previous, padded))
    }

    // Covers: a case-only change is NOT novel.
    @Test
    fun isNovel_caseOnlyChange_isNotNovel() {
        assertFalse(MemoryContentNovelty.isNovel("User Prefers Dark Mode", "user prefers dark mode"))
    }

    // Covers: a real content change (different words) IS novel.
    @Test
    fun isNovel_differentWords_isNovel() {
        assertTrue(MemoryContentNovelty.isNovel("user prefers dark mode", "user prefers light mode"))
    }

    // Covers: empty vs whitespace-only is NOT novel.
    @Test
    fun isNovel_emptyVersusWhitespaceOnly_isNotNovel() {
        assertFalse(MemoryContentNovelty.isNovel("", "   \n\t  "))
    }

    // Covers: identical strings are NOT novel.
    @Test
    fun isNovel_identicalStrings_isNotNovel() {
        assertFalse(MemoryContentNovelty.isNovel("user prefers dark mode", "user prefers dark mode"))
    }

    // Covers: a genuine addition of a word IS novel.
    @Test
    fun isNovel_addedWord_isNovel() {
        assertTrue(MemoryContentNovelty.isNovel("user prefers dark mode", "user prefers dark mode always"))
    }

    // Covers: normalize trims, collapses whitespace runs, normalizes line breaks and lowercases.
    @Test
    fun normalize_appliesAllRules() {
        assertEquals("a b c", MemoryContentNovelty.normalize("  A\r\n\tB   C  "))
    }
}

