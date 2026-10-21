# Step 1 — D06 checkpoint

**Current final code SHA: `49ad9977732bd57566fd78aa7933c65ef5f766f4`**

This is the single authoritative "final code SHA" for this branch. Earlier revisions of this
checkpoint named `adfcf154` and then `edbe22a8` as the final code SHA; both of those claims are
superseded and are retained below only as history. `edbe22a8` did not compile.

The documentation commit `e6000c3f` sits on top of `49ad9977` and is documentation-only (three doc
files, no source or workflow change), so it needs no CI run of its own; the green runs below verify
the code at `49ad9977`.

## Branch and commits

- Branch: `fix/step1-d06-safe-memory-rendering`
- Base: `origin/main` at `838deba5c3f7ea86c71265046a09a039e86a0d94` (PR #2 merged)
- Worktree: `/home/teddy/HandleIT-Android-d06`
- PR: [#3](https://github.com/Mavaebrook/HandleIT-Android/pull/3) — OPEN, unmerged by design.

Commit history on this branch, oldest → newest:

| Commit | Subject |
| --- | --- |
| `f91b43d5ce1fe83dbb1dab6a3b9a2b586f683b77` | D06 renderer + tests + plan doc |
| `5340951b5ac35195b37221ea2c868837d6153767` | index, CHANGELOG, G02 audit doc |
| `3e8a4946` | persist D06 checkpoint |
| `dce76750fb05d666354867e2b032f83013bf497d` | test builds `RowListIntegrity` directly (JVM `Log` stub) |
| `192b32ef` | record first CI dispatch outcome |
| `adfcf154327dff29ce21d095e2bf19dcb3ce07a1` | escape attribute quotes + Unicode line separators |
| `a85ccd69` | record review fixes |
| `edbe22a846b237495b924080cbf0130af6eeb9ea` | attempted `Pattern.UNICODE_CHARACTER_CLASS` fix — **DID NOT COMPILE** |
| `64bf104aad36273b91cad7a849c70d23c981730d` | record compile fix + fourth dispatch |
| `49ad9977732bd57566fd78aa7933c65ef5f766f4` | **CURRENT FINAL CODE SHA**: compile-safe regex + truncation well-formedness + blank outcome |
| `e6000c3f` | docs only: record the green CI result (no CI run needed) |

## Completed criteria

- [x] D06 renderer fenced and sanitized; provenance limited to existing fields; unknown rendered as unknown.
- [x] Whole-block budget enforced without slicing mid-tag.
- [x] Unresolved-integrity gate preserved.
- [x] Regressions added at the renderer seam (10 new tests at `f91b43d5`, 3 more at `adfcf154`, 4 more at `49ad9977`; no existing test weakened).
- [x] G02 execution-route audit recorded (audit only).
- [x] A01 device evidence recorded as PARTIAL.
- [x] index.md, CHANGELOG.md updated.
- [x] Branch pushed; both hosted workflows dispatched on the current final SHA `49ad9977`.

## Root cause of the compile blocker (verified)

Both `adfcf154` and `edbe22a8` failed to compile because of how the Unicode-aware whitespace regex
was constructed. Verified against kotlin-stdlib 2.2.20 bytecode via `javap`:

- `kotlin.text.Regex` has exactly four constructors: `(java.util.regex.Pattern)`, `(String)`,
  `(String, RegexOption)`, `(String, Set<RegexOption>)`. **There is no `(String, Int)` overload.**
- The `(Pattern)` constructor is annotated `@kotlin.PublishedApi` and its Kotlin metadata
  visibility is `INTERNAL`. Passing an `Int` flag therefore made overload resolution fall back to
  that internal `(Pattern)` constructor, producing
  `Cannot access 'constructor(nativePattern: Pattern): Regex': it is internal in 'kotlin.text.Regex'`.
- `RegexOption` has only 7 constants (`IGNORE_CASE`, `MULTILINE`, `LITERAL`, `UNIX_LINES`,
  `COMMENTS`, `DOT_MATCHES_ALL`, `CANON_EQ`). None enables `UNICODE_CHARACTER_CLASS`, and
  `UNICODE_CASE` does not exist as a `RegexOption` at all — hence the earlier
  `Unresolved reference 'UNICODE_CHARACTER_CLASS'` at `adfcf154`.

**Fix in `49ad9977`:**

```kotlin
private val WHITESPACE_RUN: Regex =
    java.util.regex.Pattern.compile("\\s+", java.util.regex.Pattern.UNICODE_CHARACTER_CLASS)
        .toRegex()
```

`Pattern.compile(String, int)` is public static; `Pattern.toRegex()` is a public Kotlin stdlib
extension (`@InlineOnly`, in `kotlin.text.StringsKt__RegexExtensionsJVMKt`). `UNICODE_CHARACTER_CLASS`
requires API 24; this module's `minSdk` is 26.

## Three additional defects fixed in `49ad9977`

Found by review against the D06 acceptance criteria, not by CI.

1. **Truncation split surrogate pairs.** `String.take(240)` counts UTF-16 code units, so input
   `"a"*239 + U+1F600 + "b"*10` left a lone high surrogate at index 239 — malformed UTF-16 that
   encodes to `?` on the wire. Fixed with a new private `truncateWithEllipsis()` that drops an
   orphaned high surrogate at the cut.
2. **Truncation split escape entities.** Escaping ran BEFORE truncation, so a cut could land inside
   `&amp;`/`&lt;`/`&gt;`/`&quot;`/`&apos;` and leave a dangling entity (e.g.
   `"a"*239 + "&" + "b"*10` → tail `…aaaa&…`), making the block not well-formed XML. Fixed by
   truncating the neutralized text BEFORE escaping in both `sanitizeDataField` and
   `sanitizeAttributeValue`. Note: the per-field cap now bounds the untrusted INPUT, so escaped
   output can exceed 240 characters; the whole-block `MAX_BLOCK_CHARS` budget still bounds the
   final block.
3. **Blank outcome rendered `outcome=""`.** `ReflectorCurator.parseEpisode` uses
   `json.optString("outcome", OUTCOME_UNKNOWN)`, which returns the fallback only when the key is
   absent or JSON `null` — a present `""` or `"   "` yields a blank outcome, which sanitized to
   `""` and rendered `outcome=""`, indistinguishable from a parse failure and inconsistent with
   `ts` (which maps its absent case to `unknown`). Fixed with `.ifEmpty { PROVENANCE_UNKNOWN }`.

## Four new regression tests added in `49ad9977`

- `buildPreflightBlock_truncationDoesNotSplitSurrogatePair` — asserts UTF-8 round-trip equality as
  an exact well-formedness check.
- `buildPreflightBlock_truncationDoesNotSplitEscapeEntity` — asserts no `&…` and no partial entity
  before the ellipsis; asserts `&amp;` is present.
- `buildPreflightBlock_blankOutcome_rendersUnknown` — covers both `""` and `"   "`.
- `buildPreflightBlock_unicodeWhitespaceCollapsed` — NBSP `U+00A0`, `U+2028`, `U+2029` → single
  space. NBSP is the case ASCII `\s` would miss, so this proves the Unicode-aware run works.

The stale KDoc on the existing `buildPreflightBlock_unicodeLineSeparator_isNeutralized` test was
corrected; its assertions are unchanged. No existing test was weakened.

## CI

All runs are GitHub-hosted Actions, all triggered by `workflow_dispatch` — neither workflow
triggers on a feature-branch push, so every dispatch below was manual.

| SHA | Android Build | Android Tests |
| --- | --- | --- |
| `5340951b` | run [39526251675](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39526251675) **SUCCESS** | run [39526269306](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39526269306) **FAILURE** — 1 test: the `unresolvedRow` test used `TaskEpisodeEntity.toEpisode()` → `AppLogger.e` → `android.util.Log.e` "not mocked" on the JVM |
| `dce76750` | run [39529855733](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39529855733) **CANCELLED** | run [39529857659](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39529857659) **CANCELLED** — superseded by the next dispatch |
| `adfcf154` | run [39530282737](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39530282737) **CANCELLED** | run [39530284722](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39530284722) **FAILURE** — Kotlin compile: `TaskPreflightFormatter.kt:354 Unresolved reference 'UNICODE_CHARACTER_CLASS'` (`RegexOption` has no such member) |
| `edbe22a8` | run [39531073371](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39531073371) **FAILURE** | run [39531075607](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39531075607) **FAILURE** — Kotlin compile: `Cannot access 'constructor(nativePattern: Pattern): Regex': it is internal in 'kotlin.text.Regex'` |
| `49ad9977` (CURRENT) | run [39534113610](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39534113610) **SUCCESS** | run [39534111508](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39534111508) **SUCCESS** |

Both workflows are now GREEN on `49ad9977`; this is the first green pair on this branch.

- Android Tests run [39534111508](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39534111508):
  status `completed`, conclusion `SUCCESS`; job "Android JVM tests" success, step 15 "Run JVM unit
  tests" success; created 2026-10-21T02:38:11Z, completed 2026-10-21T02:47:16Z (~9 min); no failed
  steps.
- Android Build run [39534113610](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39534113610):
  status `completed`, conclusion `SUCCESS`; job "Build Android artifacts" success, step 20
  "Configure APK signing keystore" success, step 21 "Run Gradle build" success, step 23 "Upload
  successful Android outputs" success; created 2026-10-21T02:38:12Z, completed
  2026-10-21T02:53:47Z (~15.5 min); no failed steps.

Honest scope of that green result: CI verifies that the code COMPILES and that the JVM unit tests
PASS. It does NOT verify device behavior, does NOT verify A01 historical entity preservation, and
does NOT change D06's PARTIAL status.

## Remaining work

- [x] CI outcome for the fifth dispatch on the current final code SHA `49ad9977`: both workflows
      GREEN — Android Tests run 39534111508 SUCCESS, Android Build run 39534113610 SUCCESS (first
      green pair on this branch).
- [ ] PR #3 review and merge (left unmerged by design; requires explicit authorization).
- [ ] A01 full verification requires a compatible populated historical baseline, established separately.
- [ ] G02 enforcement implementation (separate task; audit only here).

## Status of related items (unchanged)

- **D06 is PARTIAL.** Escaped and fenced memory still rides in the SYSTEM message, not a true
  lower-trust provider boundary. Fencing and escaping reduce but do not eliminate prompt injection;
  there is no claim of universal prompt-injection prevention. A true lower-trust message boundary
  would need provider-level plumbing that does not exist.
- **G02 is audit-only.** The execution-route audit is complete and recorded in
  [07-step1-g02-execution-route-audit.md](07-step1-g02-execution-route-audit.md); enforcement is
  NOT implemented and the checkbox stays unchecked.
- **A01 is PARTIAL.** The update smoke is PASS as user-reported; full historical local-entity
  preservation is NOT VERIFIED; there is no known compatible populated baseline; the old snapshot
  was not restored; no diagnostics receiver or test harness was added. A01 does not block this task.

## Next action

1. CI is green on `49ad9977`: Android Tests run
   [39534111508](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39534111508) SUCCESS and
   Android Build run [39534113610](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39534113610)
   SUCCESS.
2. The next action is the PR [#3](https://github.com/Mavaebrook/HandleIT-Android/pull/3) review
   handoff: hand the branch and this checkpoint to the human reviewer.
3. Merging PR #3 requires explicit authorization. This agent stops at review handoff and does not
   merge.

## Resume instructions

If this task is resumed: the current final code SHA is `49ad9977732bd57566fd78aa7933c65ef5f766f4`,
and CI is GREEN on it — Android Tests run 39534111508 SUCCESS, Android Build run 39534113610
SUCCESS (first green pair on this branch). The documentation commit `e6000c3f` sits on top of
`49ad9977` and is documentation-only, so it needs no CI run. The remaining work is: PR #3
review/merge authorization, the A01 baseline, and G02 enforcement. Do not run Gradle/build/tests
locally. Do not merge PR #3 without explicit authorization. Do not touch
`app/objectbox-models/default.json` or any source file as part of a documentation-only resume.
