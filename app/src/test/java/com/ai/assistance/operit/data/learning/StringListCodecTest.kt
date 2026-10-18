package com.ai.assistance.operit.data.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM regression tests for D07: the lossless versioned [StringListCodec] and the explicit,
 * idempotent [StringListMigration]. Pure JVM: no Android, no ObjectBox, no Robolectric, no mockk.
 *
 * Adapter-level ObjectBox upgrade compatibility (an existing install whose rows were written by the
 * old newline join, read back through the new codec after an app update) is NOT proven here; that
 * remains part of A01 device verification and is PENDING.
 */
class StringListCodecTest {

    // --- Roundtrip ---

    @Test
    fun encodeDecode_normalList_roundtripsUnchanged() {
        val values = listOf("first", "second", "third")

        val decoded = StringListCodec.decode(StringListCodec.encode(values))

        assertEquals(StringListCodec.DecodedList.Versioned(1, values), decoded)
    }

    @Test
    fun encodeDecode_multilineItem_roundtripsUnchanged() {
        // The exact defect R13 describes: an item containing "\n" used to be split into two items.
        val values = listOf("step one\nstep two", "plain")

        val decoded = StringListCodec.decode(StringListCodec.encode(values))

        assertEquals(StringListCodec.DecodedList.Versioned(1, values), decoded)
    }

    @Test
    fun encodeDecode_emptyStringItem_isPreserved() {
        // The old join dropped empty items entirely.
        val values = listOf("a", "", "b")

        val decoded = StringListCodec.decode(StringListCodec.encode(values))

        assertEquals(StringListCodec.DecodedList.Versioned(1, values), decoded)
    }

    @Test
    fun encodeDecode_unicodeItems_arePreserved() {
        val values = listOf("中文步骤", "emoji 🚀🔥", "mixed 中文 and ascii")

        val decoded = StringListCodec.decode(StringListCodec.encode(values))

        assertEquals(StringListCodec.DecodedList.Versioned(1, values), decoded)
    }

    @Test
    fun encodeDecode_quotesAndBackslashes_arePreserved() {
        val values = listOf("he said \"hi\"", "C:\\path\\to\\file", "back\\\\slash")

        val decoded = StringListCodec.decode(StringListCodec.encode(values))

        assertEquals(StringListCodec.DecodedList.Versioned(1, values), decoded)
    }

    @Test
    fun encodeDecode_delimiterCharacters_arePreserved() {
        val values = listOf("a,b,c", "a;b;c", "a|b|c", "a\tb", "a\nb")

        val decoded = StringListCodec.decode(StringListCodec.encode(values))

        assertEquals(StringListCodec.DecodedList.Versioned(1, values), decoded)
    }

    @Test
    fun encodeDecode_orderAndDuplicates_arePreserved() {
        val values = listOf("b", "a", "b", "a", "b")

        val decoded = StringListCodec.decode(StringListCodec.encode(values))

        assertEquals(StringListCodec.DecodedList.Versioned(1, values), decoded)
    }

    @Test
    fun encodeDecode_leadingAndTrailingWhitespace_arePreserved() {
        // The old join trimmed every item.
        val values = listOf("  padded  ", "\ttabbed\t")

        val decoded = StringListCodec.decode(StringListCodec.encode(values))

        assertEquals(StringListCodec.DecodedList.Versioned(1, values), decoded)
    }

    @Test
    fun encodeDecode_emptyList_roundtripsToEmptyList() {
        val encoded = StringListCodec.encode(emptyList())

        assertEquals("""{"v":1,"items":[]}""", encoded)
        assertEquals(StringListCodec.DecodedList.Versioned(1, emptyList()), StringListCodec.decode(encoded))
    }

    @Test
    fun encode_emptyList_isNotTheEmptyString() {
        // "" must stay unambiguously legacy, so an empty list can never encode to it.
        assertNotEquals("", StringListCodec.encode(emptyList()))
    }

    @Test
    fun encode_producesTheDocumentedEnvelope() {
        assertEquals("""{"v":1,"items":["a","b"]}""", StringListCodec.encode(listOf("a", "b")))
    }

    // --- Legacy decoding ---

    @Test
    fun decode_legacyNewlineJoinedValue_usesOldSemantics() {
        val decoded = StringListCodec.decode("a\nb\nc")

        assertEquals(StringListCodec.DecodedList.Legacy(listOf("a", "b", "c")), decoded)
    }

    @Test
    fun decode_legacyValueWithBlankLinesAndWhitespace_trimsAndDropsEmpties() {
        // Exact old semantics: split("\n").map { trim() }.filter { isNotEmpty() }.
        val decoded = StringListCodec.decode("  a  \n\n   \n b \n")

        assertEquals(StringListCodec.DecodedList.Legacy(listOf("a", "b")), decoded)
    }

    @Test
    fun decode_emptyStoredValue_isLegacyEmptyList() {
        assertEquals(StringListCodec.DecodedList.Legacy(emptyList()), StringListCodec.decode(""))
    }

    @Test
    fun decode_legacyValueContainingBraceButNotAnEnvelope_isLegacy() {
        // A legacy item that merely contains "{" is not an envelope: the value does not start with
        // "{" after trimming, so it is legacy by definition.
        val decoded = StringListCodec.decode("a {b} c\nd")

        assertEquals(StringListCodec.DecodedList.Legacy(listOf("a {b} c", "d")), decoded)
    }

    @Test
    fun decode_legacyValueWithTrailingTextAfterBrace_isLegacy() {
        // "{" ... "}" followed by more text is not a single JSON object, so it is legacy.
        val decoded = StringListCodec.decode("{\"v\":1} trailing")

        assertEquals(StringListCodec.DecodedList.Legacy(listOf("{\"v\":1} trailing")), decoded)
    }

    // --- Corruption and unsupported versions ---

    @Test
    fun decode_malformedEnvelope_returnsCorruptNotLegacyAndNotEmpty() {
        val stored = "{\"v\":1,\"items\":[\"a\""

        val decoded = StringListCodec.decode(stored)

        assertTrue("expected Corrupt but was $decoded", decoded is StringListCodec.DecodedList.Corrupt)
        assertEquals(stored, (decoded as StringListCodec.DecodedList.Corrupt).stored)
    }

    @Test
    fun decode_envelopeWithWrongShape_returnsCorrupt() {
        // Looks like an envelope, parses as JSON, but has no "items" array.
        val decoded = StringListCodec.decode("""{"v":1}""")

        assertTrue("expected Corrupt but was $decoded", decoded is StringListCodec.DecodedList.Corrupt)
    }

    @Test
    fun decode_envelopeWithNonStringItem_returnsCorrupt() {
        val decoded = StringListCodec.decode("""{"v":1,"items":[1,2]}""")

        assertTrue("expected Corrupt but was $decoded", decoded is StringListCodec.DecodedList.Corrupt)
    }

    @Test
    fun decode_unsupportedVersion_returnsUnsupportedVersion() {
        val stored = """{"v":999,"items":["a"]}"""

        val decoded = StringListCodec.decode(stored)

        assertEquals(
            StringListCodec.DecodedList.UnsupportedVersion(version = 999, stored = stored),
            decoded
        )
    }

    @Test
    fun looksLikeEnvelope_distinguishesEnvelopeFromLegacy() {
        assertTrue(StringListCodec.looksLikeEnvelope("""{"v":1,"items":[]}"""))
        assertTrue(StringListCodec.looksLikeEnvelope("""  {"v":1,"items":[]}  """))
        assertTrue(StringListCodec.looksLikeEnvelope("{\"v\":1,\"items\":[\"a\""))
        assertFalse(StringListCodec.looksLikeEnvelope(""))
        assertFalse(StringListCodec.looksLikeEnvelope("a\nb"))
        assertFalse(StringListCodec.looksLikeEnvelope("""{"v":1} trailing"""))
    }

    // --- Migration ---

    @Test
    fun migrateValue_legacyValue_isReencodedWithEnvelope() {
        val result = StringListMigration.migrateValue("a\nb\nc")

        assertEquals(StringListMigration.Outcome.MIGRATED, result.outcome)
        assertEquals(StringListCodec.encode(listOf("a", "b", "c")), result.value)
    }

    @Test
    fun migrateValue_versionedValue_isUnchanged() {
        val stored = StringListCodec.encode(listOf("a\nb", ""))

        val result = StringListMigration.migrateValue(stored)

        assertEquals(StringListMigration.Outcome.ALREADY_VERSIONED, result.outcome)
        assertEquals(stored, result.value)
    }

    @Test
    fun migrateValue_corruptValue_isUnchangedAndNeedsAttention() {
        val stored = "{\"v\":1,\"items\":[\"a\""

        val result = StringListMigration.migrateValue(stored)

        assertEquals(StringListMigration.Outcome.NEEDS_ATTENTION, result.outcome)
        assertEquals(stored, result.value)
    }

    @Test
    fun migrateValue_unsupportedVersion_isUnchangedAndNeedsAttention() {
        val stored = """{"v":999,"items":["a"]}"""

        val result = StringListMigration.migrateValue(stored)

        assertEquals(StringListMigration.Outcome.NEEDS_ATTENTION, result.outcome)
        assertEquals(stored, result.value)
    }

    @Test
    fun migrateValue_repeatedMigration_isIdempotent() {
        val once = StringListMigration.migrateValue("a\nb\nc").value
        val twice = StringListMigration.migrateValue(once)

        assertEquals(StringListMigration.Outcome.ALREADY_VERSIONED, twice.outcome)
        assertEquals(once, twice.value)
    }

    @Test
    fun migrateValue_mixedDataset_migratesLegacyLeavesVersionedAndSkipsCorrupt() {
        val legacy = "a\nb"
        val versioned = StringListCodec.encode(listOf("x\ny"))
        val corrupt = "{\"v\":1,\"items\":[\"a\""

        val legacyResult = StringListMigration.migrateValue(legacy)
        val versionedResult = StringListMigration.migrateValue(versioned)
        val corruptResult = StringListMigration.migrateValue(corrupt)

        assertEquals(StringListMigration.Outcome.MIGRATED, legacyResult.outcome)
        assertEquals(StringListCodec.encode(listOf("a", "b")), legacyResult.value)
        assertEquals(StringListMigration.Outcome.ALREADY_VERSIONED, versionedResult.outcome)
        assertEquals(versioned, versionedResult.value)
        assertEquals(StringListMigration.Outcome.NEEDS_ATTENTION, corruptResult.outcome)
        assertEquals(corrupt, corruptResult.value)
    }

    @Test
    fun migrationReport_plus_sumsAllCounters() {
        val total = MigrationReport(migrated = 1, alreadyVersioned = 2, skippedCorrupt = 3) +
            MigrationReport(migrated = 4, alreadyVersioned = 5, skippedCorrupt = 6)

        assertEquals(MigrationReport(migrated = 5, alreadyVersioned = 7, skippedCorrupt = 9), total)
    }
}
