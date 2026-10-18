package com.ai.assistance.operit.data.learning

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Lossless, versioned codec for the `List<String>` fields that ObjectBox entities persist in a
 * single `String` column (D07 / R13).
 *
 * ## Why this exists
 *
 * `TaskEpisodeEntity` and `SkillRevisionEntity` used to store list fields as newline-joined
 * strings: `values.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")` on write and
 * `value.split("\n").map { it.trim() }.filter { it.isNotEmpty() }` on read. That encoding is
 * lossy in four ways:
 *
 *  1. an item containing `\n` is split into two items on read;
 *  2. an empty item is dropped;
 *  3. leading/trailing whitespace of an item is destroyed by `trim()`;
 *  4. an empty list and a list of only-blank items both become `""`, so they are indistinguishable.
 *
 * This codec replaces that encoding for NEW writes with a versioned JSON envelope that preserves
 * multiline items, empty items, Unicode, quotes, backslashes, delimiter characters, order and
 * duplicates. It does not trim and does not filter.
 *
 * ## Version envelope (new writes)
 *
 * A new write is a single-line JSON object:
 *
 * ```
 * {"v":1,"items":["first","second\nline",""]}
 * ```
 *
 *  - `v` is the schema version of the envelope. [CURRENT_VERSION] is `1`. The field exists so a
 *    future format can be distinguished from this one instead of being guessed at.
 *  - `items` is the list, in order, with duplicates and empty strings preserved verbatim.
 *
 * An empty list encodes to `{"v":1,"items":[]}` — never to `""`. `""` is therefore unambiguously a
 * legacy value (see below), which is what makes the legacy/current split decidable.
 *
 * ## Legacy detection rule (precise)
 *
 * A stored value is treated as LEGACY — decoded with the exact old newline-join semantics — if and
 * only if it does NOT look like the version envelope. "Looks like the version envelope" is defined
 * as: the value, after stripping leading whitespace, starts with `{` AND the first `}` in the value
 * is followed only by whitespace (i.e. the value is a single JSON object, not a JSON object
 * embedded in other text). This is [looksLikeEnvelope].
 *
 * Consequences, all deliberate:
 *
 *  - `""` (empty column) is legacy → decodes to the empty list, exactly as before.
 *  - `"a\nb\nc"` is legacy → `["a","b","c"]`.
 *  - `"  a  \n\n b "` is legacy → `["a","b"]` (old trim + drop-empty semantics).
 *  - `"{\"v\":1,\"items\":[\"a\"]}"` looks like the envelope → parsed as current format.
 *  - `"{\"v\":1,\"items\":[\"a\""` (truncated) looks like the envelope → [DecodedList.Corrupt]. It is
 *    NEVER reinterpreted as legacy and NEVER returned as an empty list.
 *  - `"{\"v\":999,\"items\":[]}"` parses but the version is unknown → [DecodedList.UnsupportedVersion].
 *  - A legacy item that happens to be a JSON object, e.g. a single item `{"a":1}` stored by the old
 *    join, looks like the envelope and will be reported as [DecodedList.Corrupt] rather than being
 *    silently decoded as legacy. That is the intended trade-off: the alternative (guessing) is how
 *    corruption gets destroyed. The row is never rewritten by a read, so the original bytes survive
 *    for inspection and for [StringListMigration], which leaves it unchanged.
 *
 * ## Legacy data is already lossy
 *
 * Legacy rows were written by the old join, so any newline inside an item, any empty item and any
 * surrounding whitespace was already destroyed before this codec ever saw the row. Legacy decoding
 * reproduces the OLD interpretation exactly; it cannot reconstruct what the old write threw away.
 * That is why [StringListMigration] re-encodes legacy rows with the envelope: from then on the row
 * is lossless, and the loss that already happened stays a documented, unrecoverable fact.
 *
 * ## Purity
 *
 * This object is pure and side-effect free: no Android imports, no logging, no I/O. Every failure
 * is returned as a typed [DecodedList] variant so the caller decides how to surface it. Callers log
 * (see the entity read boundary in `TaskEpisodeEntity`/`SkillRevisionEntity`).
 *
 * ## What these pure tests do NOT prove
 *
 * Adapter-level ObjectBox upgrade compatibility (an existing install whose rows were written by the
 * old join, read back through the new codec after an app update) is NOT proven by the JVM unit tests
 * in `StringListCodecTest` / `TaskEpisodeEntityCodecTest` / `SkillRevisionEntityCodecTest`: there is
 * no Robolectric or mockk in this project, so no live ObjectBox store is exercised. That check
 * remains part of A01 device verification and is PENDING.
 */
object StringListCodec {

    /** Schema version written by [encode] and understood by [decode]. */
    const val CURRENT_VERSION: Int = 1

    /** The only version this codec can read. */
    private val SUPPORTED_VERSIONS: Set<Int> = setOf(CURRENT_VERSION)

    /**
     * The envelope. `v` is the schema version, `items` the list in order.
     *
     * `encodeDefaults = true` keeps `v` and `items` in the output even when they hold default values,
     * so the envelope is always self-describing.
     */
    @Serializable
    data class Envelope(val v: Int, val items: List<String>)

    /**
     * Typed result of [decode]. Exactly one of the four cases applies; there is no "unknown" case
     * and no case that silently degrades into another.
     */
    sealed class DecodedList {
        /**
         * A versioned (current-format) value that parsed successfully. [items] is the stored list
         * verbatim: order, duplicates, empty strings and embedded newlines preserved.
         */
        data class Versioned(val version: Int, val items: List<String>) : DecodedList()

        /**
         * An untagged value written by the old newline-join encoding. [items] is the result of the
         * exact old semantics `value.split("\n").map { it.trim() }.filter { it.isNotEmpty() }`.
         * The loss the old write already caused cannot be undone.
         */
        data class Legacy(val items: List<String>) : DecodedList()

        /**
         * The value looks like the version envelope but does not parse as one (invalid JSON, wrong
         * shape, missing `items`, non-string item, ...). This is corruption and must be surfaced,
         * never reinterpreted as legacy and never returned as an empty list.
         *
         * [reason] is a short, non-sensitive description for logging; [stored] is the original value
         * so a caller can inspect or preserve it.
         */
        data class Corrupt(val stored: String, val reason: String) : DecodedList()

        /**
         * The value is a well-formed envelope whose `v` this codec does not support (a newer format
         * written by a later app version). The items are NOT guessed at.
         */
        data class UnsupportedVersion(val version: Int, val stored: String) : DecodedList()
    }

    /**
     * Strict JSON: unknown keys are rejected so a future envelope that adds a key is not silently
     * read as version 1, and `isLenient` is off so malformed input fails instead of being coerced.
     */
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
        explicitNulls = true
    }

    /**
     * Encode [values] losslessly into the version envelope.
     *
     * No trimming, no filtering, no de-duplication: multiline items, empty items, Unicode, quotes,
     * backslashes, delimiter characters, order and duplicates all survive. An empty list encodes to
     * `{"v":1,"items":[]}`.
     */
    fun encode(values: List<String>): String =
        json.encodeToString(Envelope.serializer(), Envelope(v = CURRENT_VERSION, items = values))

    /**
     * Decode a stored column value. See the class KDoc for the exact legacy-detection rule and for
     * what each result means.
     *
     * Never throws for malformed input: a value that looks like the envelope but does not parse is
     * returned as [DecodedList.Corrupt], and an unknown version as
     * [DecodedList.UnsupportedVersion]. A value that does not look like the envelope is legacy by
     * definition and is decoded with the old semantics.
     */
    fun decode(stored: String): DecodedList {
        if (!looksLikeEnvelope(stored)) {
            return DecodedList.Legacy(decodeLegacy(stored))
        }
        val envelope = try {
            json.decodeFromString(Envelope.serializer(), stored)
        } catch (e: kotlinx.serialization.SerializationException) {
            // Not a fallback: a value that looks like the envelope and fails to parse is corruption.
            // Returning Legacy here would silently reinterpret (and later rewrite) damaged data.
            return DecodedList.Corrupt(stored = stored, reason = e.message ?: "invalid envelope JSON")
        } catch (e: IllegalArgumentException) {
            // kotlinx.serialization throws IllegalArgumentException for some malformed inputs
            // (e.g. a non-string element). Same reasoning as above: corruption, not legacy.
            return DecodedList.Corrupt(stored = stored, reason = e.message ?: "invalid envelope JSON")
        }
        if (envelope.v !in SUPPORTED_VERSIONS) {
            return DecodedList.UnsupportedVersion(version = envelope.v, stored = stored)
        }
        return DecodedList.Versioned(version = envelope.v, items = envelope.items)
    }

    /**
     * The exact old read semantics, preserved verbatim for legacy rows:
     * `value.split("\n").map { it.trim() }.filter { it.isNotEmpty() }`.
     *
     * Kept as a named function so the compatibility rule is one testable definition rather than an
     * inline expression that could drift.
     */
    fun decodeLegacy(stored: String): List<String> =
        stored.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * True when [stored] looks like the version envelope: after stripping leading whitespace it
     * starts with `{`, and the first `}` is followed only by whitespace.
     *
     * This is the whole legacy-detection rule. It is intentionally structural rather than a
     * substring search for `"v"`, so a legacy item that merely contains the text `"v"` is not
     * mistaken for an envelope, and a truncated envelope is still recognised as an envelope (and
     * therefore reported as corruption instead of being decoded as legacy).
     *
     * A value that starts with `{` is envelope-shaped even when it is truncated (no closing `}`),
     * so a truncated envelope is corruption, not legacy.
     */
    fun looksLikeEnvelope(stored: String): Boolean {
        val start = stored.indexOfFirst { !it.isWhitespace() }
        if (start < 0 || stored[start] != '{') return false
        // A value that starts with '{' is envelope-shaped even when it is truncated (no closing
        // '}'), so a truncated envelope is reported as corruption rather than decoded as legacy.
        val end = stored.indexOf('}', startIndex = start)
        if (end < 0) return true
        for (i in end + 1 until stored.length) {
            if (!stored[i].isWhitespace()) return false
        }
        return true
    }
}

/**
 * Explicit, idempotent migration of stored list columns from the legacy newline-join encoding to the
 * [StringListCodec] version envelope.
 *
 * There is no ObjectBox schema change here: the column name, the property id and the Kotlin field
 * name are untouched. Only the string CONTENT of existing rows is rewritten.
 *
 * ## Per-value rule ([migrateValue])
 *
 *  - already versioned (current format) → returned unchanged, [Outcome.ALREADY_VERSIONED];
 *  - legacy → decoded with the old semantics, then re-encoded with the envelope,
 *    [Outcome.MIGRATED];
 *  - corrupt or unsupported version → returned unchanged, [Outcome.NEEDS_ATTENTION]. The row is
 *    never destroyed, never blanked and never guessed at; the caller logs it.
 *
 * Idempotent: migrating an already-migrated value returns it unchanged, so running the migration
 * twice produces the same bytes.
 *
 * ## Invocation
 *
 * This object is pure. The repository methods
 * `TaskEpisodeRepository.migrateListFieldsToVersioned()` and
 * `SkillRevisionRepository.migrateListFieldsToVersioned()` run it over rows in bounded transactions.
 * They are NOT invoked automatically on app start: doing so would be a behaviour change that needs
 * device verification (A01), so invoking them is a separate, device-verified step.
 */
object StringListMigration {

    /** What happened to one stored value. */
    enum class Outcome {
        /** The value was legacy and has been re-encoded with the version envelope. */
        MIGRATED,

        /** The value was already in the current versioned format; it is unchanged. */
        ALREADY_VERSIONED,

        /** The value is corrupt or of an unsupported version; it is unchanged and needs attention. */
        NEEDS_ATTENTION
    }

    /** Result of migrating one stored value. */
    data class ValueMigration(
        val outcome: Outcome,
        /** The value to store. For [Outcome.NEEDS_ATTENTION] this is the original value. */
        val value: String,
        /** Short non-sensitive description, populated only for [Outcome.NEEDS_ATTENTION]. */
        val detail: String = ""
    )

    /**
     * Migrate one stored value. Pure and idempotent; see the object KDoc for the rule.
     */
    fun migrateValue(stored: String): ValueMigration =
        when (val decoded = StringListCodec.decode(stored)) {
            is StringListCodec.DecodedList.Versioned ->
                ValueMigration(outcome = Outcome.ALREADY_VERSIONED, value = stored)

            is StringListCodec.DecodedList.Legacy ->
                ValueMigration(
                    outcome = Outcome.MIGRATED,
                    value = StringListCodec.encode(decoded.items)
                )

            is StringListCodec.DecodedList.Corrupt ->
                ValueMigration(
                    outcome = Outcome.NEEDS_ATTENTION,
                    value = stored,
                    detail = "corrupt envelope: ${decoded.reason}"
                )

            is StringListCodec.DecodedList.UnsupportedVersion ->
                ValueMigration(
                    outcome = Outcome.NEEDS_ATTENTION,
                    value = stored,
                    detail = "unsupported envelope version ${decoded.version}"
                )
        }
}

/**
 * Counts returned by a repository-level list-field migration.
 *
 * @param migrated number of rows whose content changed (at least one field was legacy).
 * @param alreadyVersioned number of rows whose list fields were all already versioned.
 * @param skippedCorrupt number of rows left untouched because at least one field was corrupt or of
 *   an unsupported version. Those rows are logged by the repository and need attention.
 */
data class MigrationReport(
    val migrated: Int = 0,
    val alreadyVersioned: Int = 0,
    val skippedCorrupt: Int = 0
) {
    operator fun plus(other: MigrationReport): MigrationReport = MigrationReport(
        migrated = migrated + other.migrated,
        alreadyVersioned = alreadyVersioned + other.alreadyVersioned,
        skippedCorrupt = skippedCorrupt + other.skippedCorrupt
    )
}
