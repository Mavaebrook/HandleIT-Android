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
 * ## Decode outcome (five-way, precise)
 *
 * [decode] returns exactly one of five [DecodedList] variants. The order of checks is:
 *
 *  1. **Shape pre-check.** If [looksLikeEnvelope] is false — the value does not start with `{`
 *     after leading whitespace — it is [DecodedList.Legacy] by the established format, decoded with
 *     the exact old newline-join semantics. A newline in the value does NOT prove legacy; the only
 *     thing that proves legacy is the absence of a brace-starting shape, which is the format the
 *     old writer produced.
 *  2. **Strict envelope parse.** If the value is brace-starting, it is parsed strictly into
 *     [Envelope]:
 *      - parse succeeds and `v` is in [SUPPORTED_VERSIONS] → [DecodedList.Versioned];
 *      - parse succeeds and `v` is not supported → [DecodedList.UnsupportedVersion];
 *      - parse fails → step 3.
 *  3. **Prefix-based classification.** When the strict parse fails, the value is classified by a
 *     cheap, parse-independent prefix test ([looksLikeEnvelopePrefix]) rather than by re-parsing it:
 *      - it starts with an envelope-shaped prefix — `{`, optional whitespace, `"v"`, optional
 *        whitespace, `:` — → [DecodedList.Corrupt]. This covers truncated input, trailing junk, a
 *        non-integer `v`, a missing or wrongly-typed `items`, and extra keys: all of them clearly
 *        intended to be an envelope, so none of them may become Legacy or Ambiguous.
 *      - it is brace-starting but does NOT start with that prefix → [DecodedList.Ambiguous]. Its
 *        provenance (legacy literal vs envelope) cannot be proven, so the bytes are preserved
 *        verbatim.
 *
 * Consequences, all deliberate:
 *
 *  - `""` (empty column) is legacy → decodes to the empty list, exactly as before.
 *  - `"a\nb\nc"` is legacy → `["a","b","c"]`.
 *  - `"  a  \n\n b "` is legacy → `["a","b"]` (old trim + drop-empty semantics).
 *  - `"{\"v\":1,\"items\":[\"a\"]}"` looks like the envelope → parsed as current format.
 *  - `"{\"v\":1,\"items\":[\"a}b\"]}"` (a `}` inside a string item) looks like the envelope → the
 *    strict JSON parse handles the brace inside the string literal correctly.
 *  - `"{\"v\":1,\"items\":[\"a\""` (truncated) starts with the envelope prefix → [DecodedList.Corrupt].
 *    It is NEVER reinterpreted as legacy and NEVER returned as an empty list.
 *  - `"{\"v\":1,\"items\":[\"a\"]}garbage"` (trailing junk) starts with the envelope prefix →
 *    [DecodedList.Corrupt].
 *  - `"{\"v\":999,\"items\":[]}"` parses but the version is unknown → [DecodedList.UnsupportedVersion].
 *  - `"{\"v\":\"1\",\"items\":[]}"` (non-integer `v`) starts with the envelope prefix →
 *    [DecodedList.Corrupt]: it intended to be an envelope.
 *  - `"{\"a\":1}"` (a legacy item that happens to be a JSON object, with no `v`) is brace-starting
 *    but does NOT start with the envelope prefix → [DecodedList.Ambiguous]. Its provenance cannot be
 *    proven. The bytes are preserved verbatim and the caller must not treat it as healthy-empty and
 *    must not rewrite it.
 *  - `"{not json"` (brace-starting, not envelope-shaped) → [DecodedList.Ambiguous], preserved
 *    verbatim.
 *
 * ## Compatibility policy (what the discriminator can and cannot prove)
 *
 * The discriminator is STRUCTURAL, not a magic sentinel: a value is an envelope when it starts with
 * `{` and parses as a single JSON object carrying a recognized `v`. There is no sentinel string that
 * legacy rows could never contain.
 *
 * A magic prefix alone could not prove collision-free separation from arbitrary preexisting legacy
 * text either — any prefix can in principle appear in legacy content — and, more concretely, the
 * f39e4358 build already wrote UNPREFIXED `{"v":1,"items":...}` envelopes, so introducing a prefix
 * now would strand those rows. The envelope format is therefore kept as-is.
 *
 * The honest statement of the limit: **strict JSON proves syntax, not historical intent.** A value
 * that parses as an envelope is treated as an envelope; that is a structural inference, not proof
 * that the old writer never produced those bytes. Likewise **a newline does not prove legacy** — it
 * is merely consistent with the old join.
 *
 * When the strict parse fails, the Corrupt-vs-Ambiguous split is decided by a parse-independent
 * prefix test, not by a lenient re-parse: a value that starts with the envelope-shaped prefix
 * (`{` … `"v"` … `:`) is a recognized-but-malformed versioned payload and is reported as
 * [DecodedList.Corrupt]; a brace-starting value that does not start with that prefix is preserved as
 * [DecodedList.Ambiguous] rather than guessed. This is deterministic and does not depend on any JSON
 * parse succeeding, so truncated input and trailing junk are reliably Corrupt instead of falling
 * through to Ambiguous.
 *
 * [StringListMigration] leaves Corrupt, UnsupportedVersion and Ambiguous rows untouched and reports
 * [StringListMigration.Outcome.NEEDS_ATTENTION], so nothing is destroyed or guessed at.
 *
 * Both generations of stored data are handled:
 *
 *  - Pre-Step1 rows (newline-joined) that do not start with `{` are Legacy and are re-encoded by
 *    the migration with the old semantics preserved.
 *  - Existing Step1 envelopes (`{"v":1,...}`) parse as [DecodedList.Versioned] and are left
 *    byte-identical, so the migration is idempotent.
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
     * Typed result of [decode]. Exactly one of the five cases applies; there is no "unknown" case
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
         * shape, missing `items`, non-string item, trailing junk, ...). This is corruption and must
         * be surfaced, never reinterpreted as legacy and never returned as an empty list.
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

        /**
         * The stored bytes are brace-starting but do NOT start with the envelope-shaped prefix
         * (`{` … `"v"` … `:`), so they could be either a legacy literal string that happens to be
         * brace-starting OR a versioned envelope whose provenance cannot be proven.
         *
         * The bytes are preserved verbatim in [stored]; [reason] is a short, non-sensitive
         * description for logging. The caller must NOT treat this as healthy-empty and must NOT
         * rewrite it. This is the explicit honest compatibility policy for brace-starting legacy
         * text: the codec refuses to guess.
         */
        data class Ambiguous(val stored: String, val reason: String) : DecodedList()
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
     * Decode a stored column value. See the class KDoc for the exact five-way outcome and for what
     * each result means.
     *
     * Order of checks: shape ([looksLikeEnvelope]) → strict JSON parse → version check
     * ([DecodedList.UnsupportedVersion]) → [DecodedList.Versioned]; on strict-parse failure, a
     * parse-independent prefix test ([looksLikeEnvelopePrefix]) decides between
     * [DecodedList.Corrupt] (recognized-but-malformed versioned payload: starts with the
     * envelope-shaped prefix `{` … `"v"` … `:`) and [DecodedList.Ambiguous] (brace-starting text
     * whose provenance cannot be proven).
     *
     * Never throws for malformed input. A value that does not look like the envelope is legacy by
     * definition and is decoded with the old semantics.
     */
    fun decode(stored: String): DecodedList {
        if (!looksLikeEnvelope(stored)) {
            return DecodedList.Legacy(decodeLegacy(stored))
        }
        val envelope = try {
            json.decodeFromString(Envelope.serializer(), stored)
        } catch (e: kotlinx.serialization.SerializationException) {
            // Not a fallback: a value that looks like the envelope and fails to parse is either a
            // recognized-but-malformed versioned payload (Corrupt) or brace-starting text whose
            // provenance cannot be proven (Ambiguous). It is never silently reinterpreted as legacy.
            return classifyUnparseable(stored, e.message ?: "invalid envelope JSON")
        } catch (e: IllegalArgumentException) {
            // kotlinx.serialization throws IllegalArgumentException for some malformed inputs
            // (e.g. a non-string element). Same reasoning as above.
            return classifyUnparseable(stored, e.message ?: "invalid envelope JSON")
        }
        if (envelope.v !in SUPPORTED_VERSIONS) {
            return DecodedList.UnsupportedVersion(version = envelope.v, stored = stored)
        }
        return DecodedList.Versioned(version = envelope.v, items = envelope.items)
    }

    /**
     * Decide between [DecodedList.Corrupt] and [DecodedList.Ambiguous] for a brace-starting value
     * that failed the strict [Envelope] parse.
     *
     * The decision is a cheap, parse-independent prefix test ([looksLikeEnvelopePrefix]); it does
     * NOT depend on any JSON parse succeeding, so truncated input and trailing junk are reliably
     * classified:
     *  - starts with the envelope-shaped prefix (`{` … `"v"` … `:`) → [DecodedList.Corrupt]
     *    (truncated, trailing junk, non-integer `v`, missing/wrong `items`, extra keys, ...);
     *  - brace-starting but not envelope-shaped → [DecodedList.Ambiguous], preserved verbatim.
     */
    private fun classifyUnparseable(stored: String, strictReason: String): DecodedList {
        return if (looksLikeEnvelopePrefix(stored)) {
            // It clearly intended to be an envelope (starts with {"v"...): a malformed recognized
            // versioned payload. Truncated, trailing junk, wrong item types, extra keys, non-integer v,
            // missing items all land here. Never reinterpreted as legacy or ambiguous.
            DecodedList.Corrupt(stored = stored, reason = strictReason)
        } else {
            // Brace-starting but not envelope-shaped: provenance (legacy literal vs envelope) cannot be
            // proven. Preserve verbatim.
            DecodedList.Ambiguous(
                stored = stored,
                reason = "brace-starting value is not envelope-shaped; provenance unprovable"
            )
        }
    }

    /**
     * True when [stored], after leading whitespace, starts with an envelope-shaped prefix:
     * `{` then optional whitespace then `"v"` then optional whitespace then `:`.
     * This is a cheap, parse-independent test used only to distinguish a malformed recognized
     * versioned payload (Corrupt) from brace-starting text of unprovable provenance (Ambiguous).
     */
    private fun looksLikeEnvelopePrefix(stored: String): Boolean {
        var i = stored.indexOfFirst { !it.isWhitespace() }
        if (i < 0 || stored[i] != '{') return false
        i++
        while (i < stored.length && stored[i].isWhitespace()) i++
        if (i >= stored.length || stored[i] != '"') return false
        i++
        if (i >= stored.length || stored[i] != 'v') return false
        i++
        if (i >= stored.length || stored[i] != '"') return false
        i++
        while (i < stored.length && stored[i].isWhitespace()) i++
        return i < stored.length && stored[i] == ':'
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
     * True when [stored] starts with `{` after leading whitespace.
     *
     * This is only a cheap shape pre-check; the authoritative decision is the strict JSON parse in
     * [decode], which rejects truncated input and trailing junk as [DecodedList.Corrupt] and
     * classifies unprovable brace-starting text as [DecodedList.Ambiguous]. A value that does not
     * start with `{` is legacy by definition.
     */
    fun looksLikeEnvelope(stored: String): Boolean {
        val start = stored.indexOfFirst { !it.isWhitespace() }
        return start >= 0 && stored[start] == '{'
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
 *  - corrupt, unsupported version or ambiguous → returned unchanged, [Outcome.NEEDS_ATTENTION]. The
 *    row is never destroyed, never blanked and never guessed at; the caller logs it. An ambiguous
 *    brace-starting value is NEVER migrated and NEVER rewritten.
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

        /**
         * The value is corrupt, of an unsupported version, or ambiguous; it is unchanged and needs
         * attention.
         */
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

            is StringListCodec.DecodedList.Ambiguous ->
                ValueMigration(
                    outcome = Outcome.NEEDS_ATTENTION,
                    value = stored,
                    detail = "ambiguous brace-starting value: ${decoded.reason}"
                )
        }
}

/**
 * Counts returned by a repository-level list-field migration.
 *
 * @param migrated number of rows whose content changed (at least one field was legacy).
 * @param alreadyVersioned number of rows whose list fields were all already versioned.
 * @param skippedCorrupt number of rows left untouched because at least one field was corrupt, of an
 *   unsupported version, or ambiguous. Those rows are logged by the repository and need attention.
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
