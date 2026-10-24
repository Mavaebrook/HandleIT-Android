# Changelog

All notable changes to HandleIT-Android are recorded here. Entries are grouped by date and reference the relevant TODO checklist where applicable.

## 2026-10-24 — Argon review hardening (non-blocking notes 1 & 2)

Applied Argon's two non-blocking hardening notes from `Argon-Review-f5ddc3fb75-2026-10-24.txt` (verdict APPROVE-WITH-NOTES, zero blockers). Both are one-line, behavior-preserving polish in `MemoryMetadataStore.kt`, not defect remediation:

- `retire` now returns early when `priorStatus` is `STATUS_INACTIVE`, so a future caller that bypasses `MemoryRetirementGovernance.decide` on a memory deactivated through `markInactive` cannot record a misleading `priorStatus = "INACTIVE"` audit value.
- `initializeStatusIfAbsent` now treats a blank status as absent (`.isNullOrBlank()`), aligning it with `retire`'s existing blank handling.

No schema, dependency, or model-call change. No local build or test run; APK builds remain GitHub-Actions-only. See [the plan](docs/plans/argon-hardening-20261024.md).

## 2026-10-24 — Reviewed memory integration

PR #4 merged at 5526acd0. PR #5 is reconciled with the new main, retaining restoration integrity, normalized reinforcement and positive graph contributions. Shared documentation preserves both change histories. Combined hosted validation is pending; see [the integration record](docs/TODO/memory-integration-20261024/index.md). D02/D04 remain partial.

## 2026-10-23 — D02 restoration eligibility and persistence tests

Ordinary status writes now atomically invalidate restoration eligibility, including INACTIVE -> INACTIVE. Missing-status initialization checks and writes within one transaction. Restore validates all required audit fields and retains historical audit while consuming the marker.

MemoryRepository delegates metadata persistence to MemoryMetadataStore. Ten native ObjectBox JVM regressions cover reopen, same-status deactivation, successful/repeated/refused restoration, invalid audit and rollback. Hosted validation is pending at publication. No schema, dependency or automatic-retirement change. F4 documentation now distinguishes persisted PendingDecision from logged LeaveActive outcomes, callInTx from runInTx, and retained audit history.

See [the repair record](docs/TODO/d02-restore-eligibility-20261023/index.md). D02 remains partial, with no production restoration caller and no historical-device/full-retrieval acceptance.

## 2026-10-23 — D04 graph candidate admission

Both graph link directions now share a positive-contribution mutation gate. Zero/negative contributions create neither retrieval candidates nor score-part entries, including at relevance threshold 0. Scores from other retrieval signals and inspected-edge counts are preserved. Four JVM regressions cover candidate admission, existing candidates and positive accumulation at the production mutation seam; hosted validation is pending. D04b batching remains unimplemented, not established as schema-blocked.

See the [follow-up record](docs/TODO/d04-zero-score-candidates-20261023/index.md). PR #5 remains open for review.

## 2026-10-22 — MCP argument preservation

Preserve schema-declared strings, structured arguments, nested JSON literals and null positions through MCP conversion and bridge serialization. Remove raw conversion logging and malformed-array repair. Seven regression tests added; hosted validation pending.
No local build or test ran. See the [task record](docs/TODO/mcp-argument-preservation-20261022/index.md).

## 2026-10-22 — Reflector and Curator cancellation

Propagate coroutine cancellation through Reflector and Curator provider calls, including service acquisition. Ordinary failures still return null; service acquisition failures now use the existing role error logger. Ten parameterized regression cases added; hosted validation pending.
No local build or test ran. See the [task record](docs/TODO/reflector-curator-cancellation-20261022/index.md).

## 2026-10-22 — Main PR validation

PR Check now targets `main` and calls the existing Android Build and Android Tests
workflows for selected lanes. Both operate on the merge candidate. PR calls receive
no signing secrets and do not upload APK/AAB outputs; signed push and manual builds
retain their existing behavior. The aggregate Candidate checks gate remains in place.
Repository branch-protection enforcement is still pending administrator configuration.

Implementation is ready for hosted validation; no local Gradle build or test was run.
See the [active follow-up](docs/TODO/main-pr-runtime-fixes-20261022/index.md).

## 2026-10-21 — Learning pipeline hardening: D02 memory-retirement governance (PARTIAL)

D02 is delivered as a conservative prevention gate plus an additive, reversible restore API. Production never auto-retires a memory: a model-asserted `CORRECTS`/`SUPERSEDES` link type or a timestamp alone can never authorize retirement, so no such link auto-retires a fact. Only PendingDecision persists a marker; LeaveActive only logs. The overall D02 item remains PARTIAL and unchecked because production `correctionApplicable` is hardcoded `false` (no corroborating evidence source), active-user-constraint provenance is absent, restoration has no production UI/caller, and full retrieval/device acceptance remains incomplete. The October 23 follow-up adds native metadata persistence tests. Documentation and source changes only; no build or test was run on the VPS or any local machine.

### Added
- Pure `MemoryRetirementGovernance` policy object with a `decide` gate (self-link, already-inactive, ambiguity, user-constraint, direction/recency, link-type, evidence) and a `reasonCode` mapping.
- Pure `MemoryRetirementGovernance.evaluateRestore` decision that refuses restoration unless the memory is still `INACTIVE` with a complete retirement audit.
- Atomic audited retirement (`retireMemoryWithAudit`) and explicit `restoreMemory` in `MemoryRepository`, using existing `MemoryProperty` key-value storage (no schema change).

### Fixed
- Retirement uses store.runInTx; restoration uses value-returning store.callInTx, so a partial failure cannot leave audit rows without `status=INACTIVE` (or vice versa).
- Re-retirement is guarded so it cannot overwrite the recorded prior status and corrupt the audit chain.
- Changes away from INACTIVE are refused. The October 23 follow-up additionally invalidates the marker for same-status writes.
- Restoration retains the retirement provenance and stamps `restored_at` instead of erasing the audit trail; the pending reason is cleared on resolution.

### Tests
- Five new pure JVM tests for `evaluateRestore` (NOT_RETIRED, NOT_INACTIVE, MISSING_PROVENANCE, restore to ACTIVE, restore to a non-ACTIVE prior status). These are pure decision tests, not persistence evidence.

## 2026-10-21 — Learning pipeline hardening: memory reinforcement, propagation floor (D04 / R10)

Bounded repair of three memory-salience defects from the learning-pipeline hardening findings (R10). Two items are implemented; salience N+1 batching is not yet implemented or measured (see the TODO findings note). No ObjectBox schema change: `app/objectbox-models/default.json` is byte-for-byte unchanged. No new model calls. Documentation and source changes only; no build or test was run on the VPS or any local machine. Nothing here is device-verified.

### Fixed
- `MemoryLibrary` (D04a): reinforcement now fires only on normalized content novelty via the pure `MemoryContentNovelty.isNovel` (trim, collapse whitespace runs, normalize line breaks, case-insensitive), instead of raw string inequality. Cosmetic edits — trailing/leading whitespace, re-wrapped lines, case-only changes — no longer inflate occurrence counts. No embeddings or model calls.
- `MemoryRepository` (D04c): removed the unconditional `basePropagationScore = 0.03 * effectiveEdgeWeight` that was added to every traversed graph edge regardless of relevance. Propagation now uses the pure `MemoryPropagationScoring.propagatedScore`, where edge weight alone determines the score and a zero/negative-weight link contributes nothing. This is a behavior change: semantically meaningless neighbours are no longer boosted into results. No other constants retuned.
- `MemoryLibrary` (D04a, review fix): the `updatedEntities` reinforcement guard compared `memoryToUpdate.content` against `update.newContent` *after* `updateMemory` had already assigned `content = newContent` in place on the same instance (`MemoryRepository.kt:2383`), so the comparison was always new-vs-new and `isNovel` was always false — `reinforceMemory` was dead code on that path. `previousContent` is now captured before the call, matching the already-correct main-problem path. Found by independent review of D04.

### Not changed
- Salience N+1 (D04b): `loadMemorySalienceMetadata` still issues one property query per memory. Batching is **not yet implemented** in this job. The earlier claim that batching is impossible without a schema change is an overstatement: the "no scalar `memoryId`" fact is correct, but ObjectBox 5.3.0 `QueryBuilder.link(RelationInfo)` supports stand-alone `ToMany` relations and does not require a target FK, so a viable API exists. The limitation is recorded as "not yet implemented, viable API exists" rather than "blocked by schema". The code is intentionally left unchanged rather than partially patched.
- `edgesTraversed` still counts zero-weight edges (incremented for every traversed link regardless of weight). Diagnostic only — it feeds `MemorySearchDebugInfo.graphEdgesTraversed` and has no scoring or ranking impact.

### Tests
- Added `MemoryContentNoveltyTest` (JVM) — whitespace-only, re-wrapped, collapsed-run and case-only edits are not novel; genuine content changes are.
- Added `MemoryPropagationScoringTest` (JVM) — a zero-weight link contributes no score; edge weight is the determinant.

### CI
- Historical head `7a329587`: Android Build 39581380138 and Android Tests 39581383370 succeeded. Follow-up changes require fresh hosted validation; these runs do not validate the new head.

## 2026-10-20 — Learning pipeline hardening: D06 safe memory rendering and G02 execution-route audit

D06 is implemented: retrieved episode text is now rendered inside a fixed, sanitized, provenance-labelled fence instead of being interpolated verbatim into the SYSTEM prompt. G02 is audit-only: the execution-route audit is complete and recorded, but no enforcement is implemented. A01 device evidence is recorded as PARTIAL, based on user-reported device observation rather than CI verification. Documentation and source changes only; no build or test was run on the VPS or any local machine.

### Fixed
- Retrieved episode text is now rendered inside a fixed `<retrieved_memory trust="untrusted">` fence, separate from the fixed authoritative framing sentence, instead of being interpolated verbatim into the SYSTEM prompt.
- Every interpolated field is sanitized: control characters neutralized, `&`/`<`/`>` escaped, whitespace collapsed, length-capped. CJK and other Unicode preserved.
- Provenance attributes carry only values that exist on TaskEpisode; `source_chat` and `confidence` render as `unknown`, never fabricated or inferred from outcome.
- Whole-block budget enforced by adding episodes one at a time so framing and closing fence are always emitted and the block is never sliced mid-tag.
- Unresolved-integrity gate preserved: such rows render only the neutral task line and fixed caution, no resolution/cause/prereq content.
- Compile-safe Unicode whitespace regex: the whitespace run is now built with `java.util.regex.Pattern.compile("\\s+", java.util.regex.Pattern.UNICODE_CHARACTER_CLASS).toRegex()`. `kotlin.text.Regex` has no `(String, Int)` overload and its `(java.util.regex.Pattern)` constructor is `@PublishedApi`/internal, so the earlier `Regex(pattern, Pattern.UNICODE_CHARACTER_CLASS)` form resolved to the internal constructor and failed to compile; `RegexOption` has no `UNICODE_CHARACTER_CLASS` member either. `UNICODE_CHARACTER_CLASS` requires API 24; this module's `minSdk` is 26.
- Truncation no longer splits surrogate pairs: `String.take(n)` counts UTF-16 code units, so a cut could keep a high surrogate and drop its low surrogate, emitting a lone surrogate that encodes to `?` on the wire. A new `truncateWithEllipsis` drops an orphaned high surrogate at the cut.
- Truncation no longer splits escape entities: escaping ran before truncation, so a cut could land inside `&amp;`/`&lt;`/`&gt;`/`&quot;`/`&apos;` and leave a dangling entity, making the block not well-formed XML. Both sanitizers now truncate the neutralized text BEFORE escaping. The per-field cap therefore bounds the untrusted input; the whole-block `MAX_BLOCK_CHARS` budget still bounds the final block.
- A present but blank `outcome` now renders as `unknown` instead of `outcome=""`: `json.optString("outcome", OUTCOME_UNKNOWN)` returns the fallback only when the key is absent or JSON `null`, so a present `""`/`"   "` produced a blank attribute indistinguishable from a parse failure and inconsistent with `ts`, which maps its absent case to `unknown`.

### Tests
- Regressions at the renderer seam for fake system/assistant delimiters, embedded instructions, control characters, long inputs, CJK, missing provenance, unresolved rows, duplicate-block and empty-input cases.
- Existing codec/redaction tests preserved.
- `buildPreflightBlock_truncationDoesNotSplitSurrogatePair` (asserts UTF-8 round-trip equality as an exact well-formedness check).
- `buildPreflightBlock_truncationDoesNotSplitEscapeEntity` (asserts no `&…` and no partial entity before the ellipsis; asserts `&amp;` is present).
- `buildPreflightBlock_blankOutcome_rendersUnknown` (covers both `""` and `"   "`).
- `buildPreflightBlock_unicodeWhitespaceCollapsed` (NBSP `U+00A0`, `U+2028`, `U+2029` collapse to a single space; NBSP is the case ASCII `\s` would miss, so this proves the Unicode-aware run works).
- The stale KDoc on the existing `buildPreflightBlock_unicodeLineSeparator_isNeutralized` test was corrected; its assertions are unchanged. No existing test was weakened.

### CI
All runs are GitHub-hosted Actions, all triggered by `workflow_dispatch` (neither workflow triggers on a feature-branch push). Both workflows are now green on `49ad9977`; this is the first green pair on this branch.

- `5340951b`: Android Build run [39526251675](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39526251675) SUCCESS; Android Tests run [39526269306](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39526269306) FAILURE (1 test: the unresolved-row test used `TaskEpisodeEntity.toEpisode()` → `AppLogger.e` → `android.util.Log.e` "not mocked" on the JVM).
- `dce76750`: Android Build run [39529855733](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39529855733) CANCELLED; Android Tests run [39529857659](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39529857659) CANCELLED (superseded by the next dispatch).
- `adfcf154`: Android Build run [39530282737](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39530282737) CANCELLED; Android Tests run [39530284722](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39530284722) FAILURE (Kotlin compile: `TaskPreflightFormatter.kt:354 Unresolved reference 'UNICODE_CHARACTER_CLASS'`).
- `edbe22a8`: Android Build run [39531073371](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39531073371) FAILURE; Android Tests run [39531075607](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39531075607) FAILURE (Kotlin compile: `Cannot access 'constructor(nativePattern: Pattern): Regex': it is internal in 'kotlin.text.Regex'`).
- `49ad9977` (current final code SHA): Android Tests run [39534111508](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39534111508) **SUCCESS** (job "Android JVM tests" success, step 15 "Run JVM unit tests" success; ~9 min); Android Build run [39534113610](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39534113610) **SUCCESS** (job "Build Android artifacts" success, steps 20 "Configure APK signing keystore", 21 "Run Gradle build" and 23 "Upload successful Android outputs" success; ~15.5 min). No failed steps in either run.

Both workflows are green on `49ad9977` (Android Tests run 39534111508 and Android Build run 39534113610, both SUCCESS). CI verifies compilation and the JVM unit tests only; it does NOT verify device behavior, does NOT verify A01 historical entity preservation, and does NOT change D06's PARTIAL status. PR [#3](https://github.com/Mavaebrook/HandleIT-Android/pull/3) was merged on 2026-10-21 at `5b3277cf`.

### Notes
- Honest limitation: fencing and escaping reduce but do not eliminate prompt injection. The block still rides in the SYSTEM message, and a true lower-trust message boundary would need provider-level plumbing that does not exist.
- G02 enforcement is not implemented; the execution-route audit is recorded in `docs/TODO/learning-pipeline-hardening-20261017/07-step1-g02-execution-route-audit.md`.
- A01 device evidence is user-reported, not CI-verified. Historical entity preservation is NOT VERIFIED, and no compatible populated baseline exists.
- Invariants preserved: `app/objectbox-models/default.json` is byte-for-byte unchanged, no new entities, columns or UIDs, and no `entity12`/`MemoryLedger` edits.

## 2026-10-20 — PR #2 diagnostic redaction

- Redact retained recovery bytes from `ListFieldIntegrity` and unresolved codec `toString()` results, including nested episode/revision diagnostics. Original properties and verbatim re-save behavior remain intact.
- Add a sentinel regression for corrupt, unsupported-version, and ambiguous payloads; GitHub-hosted [Build](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39517747866) and [Tests](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39517750560) passed on code commit `1794c64fab569fc913c691bae5ad1c81cd7ad72d`.
- Document that unresolved evidence contributes zero and edits to unresolved lists are ignored in favor of the captured bytes. No explicit resolution API is introduced.
- Verified the prior documentation correction at `5cf24170`; the older repair evidence below applies to `c50b399b`, not this cleanup. A01 device verification remains PENDING.

## 2026-10-19 — Learning pipeline hardening: Step 1 codec & data integrity repair

Bounded repair of the Step 1 codec and data-integrity work after the independent review of bridge #36 did NOT establish Step 1 acceptance. This is NOT Stage 2 (the next stage is D06 safe memory rendering and G02 execution-route audit). No Job 2 observation-lifecycle changes, no Stage 2 D06/G02 rollout, no graph/relational entities, no H01, no vector caching, no orchestrator redesign. No ObjectBox schema change: `app/objectbox-models/default.json` is byte-for-byte unchanged. Documentation and source changes only; no build or test was run on the VPS or any local machine. Nothing here is device-verified.

### Fixed
- `StringListCodec` (D07): added an explicit `Ambiguous` decode outcome for brace-starting bytes whose provenance (legacy literal vs versioned envelope) cannot be proven. The Corrupt-vs-Ambiguous split is a parse-independent prefix test, not a lenient re-parse, so truncated/trailing-junk envelopes are reliably `Corrupt` and brace-starting legacy text (`{"a":1}`, `{placeholder}\nsecond_item`) is preserved as `Ambiguous` rather than guessed. Unknown versions remain `UnsupportedVersion`; non-brace-starting values keep the exact legacy newline semantics; the unprefixed envelope format is unchanged.
- `TaskEpisode` / `SkillRevision`: added non-persisted `listIntegrity` metadata (`ListFieldIntegrity` / `RowListIntegrity`) distinguishing a genuine empty list from an unresolved field, retaining field name, reason/version and original raw bytes for recovery. Raw bytes are never rendered into prompts or logs.
- `TaskPreflightFormatter` / `TaskEpisodeRetrieval`: an episode with unresolved `requiredPrerequisites` or `applicableConstraints` is machine-readably `INELIGIBLE_UNRESOLVED_INTEGRITY` and the formatter emits a bounded fixed caution (no raw payload, no duplicated memory block).
- `SkillRevisionLifecycle`: `evaluate` rejects a candidate with any unresolved list field (`passed=false`, explicit reason); `preservesUserConstraints` returns false for an unresolved candidate; a baseline-aware overload rejects an unresolved authoritative revision.
- `TaskEpisodeEntity` / `SkillRevisionEntity` / repositories: `TaskEpisodeEntity.fromEpisode` / `SkillRevisionEntity.fromRevision` `encodeField` preserves unresolved bytes unconditionally from the captured `ListFieldIntegrity.rawStored` (no caller-supplied map, no DB read, no guard); `save()` is a single `put()`. An unresolved field is written back verbatim instead of being re-encoded as an empty envelope, so a re-save cannot erase unresolved bytes, and the previous read-then-put TOCTOU is eliminated.
- `UnresolvedWriteGuard` removed: preservation is now unconditional at the entity boundary, so the guard's decision has no inputs left.
- `RowMigrationPlanner`: treats `Ambiguous` as `NEEDS_ATTENTION`; any such row is left entirely untouched in memory and on disk; repeated migration is idempotent.
- Known limitation (PARTIAL / BLOCKED, not resolved): the envelope-shaped legacy literal collision cannot be disambiguated under the fixed schema. A pre-Step1 legacy value that is itself a well-formed envelope-shaped JSON object is byte-indistinguishable from a real versioned envelope and is decoded as `Versioned`/HEALTHY. This is documented in the `StringListCodec` KDoc as an accepted limitation, not a defect to be fixed by more parsing; the durable fix is a schema change and is out of scope.

### Tests
- Added `Step1CodecIntegrityRegressionTest` + `Step1CodecIntegrityRedRegressionTest` (see TODO index for current counts) covering corrupt prerequisites/constraints/steps, applicability ineligibility, formatter caution, unconditional save preservation, lifecycle rejection (candidate and baseline), the provenance policy (Ambiguous vs Corrupt vs UnsupportedVersion vs Legacy), lossless round-trip, idempotent migration, healthy empty lists, later-field row immutability, and integrity preservation through `copy` including `applicableConstraintsJoined` unresolved -> ineligible and `UNSUPPORTED_VERSION` integrity status.

### CI
- Before the diagnostic cleanup above, verified code SHA `c50b399b0c6d84bfb15c38000d314a8fd4537c9e` (base `f495c1083b204ee1c50eb333600a68c3a32c963d`). Android Build success (https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39461571548), Android Tests success (https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39461574664). `08ab06b767d0d338d48f13e17957b750d08e77c0` is a historical intermediate only: it added `UnresolvedWriteGuard`, which was later removed. It is superseded by `02e8f029` (red-first regressions for unresolved-write erasure, ineligible rendering, and raw reason leakage) and `66887f07` (unconditional preservation from captured `ListFieldIntegrity.rawStored`, gated ineligible rendering, fixed reason codes, `UnresolvedWriteGuard` removed). `02e8f029`'s Android Tests run 39421303124 failed intentionally (red-first); its Android Build succeeded. Intermediate failures and the two independent-review fix rounds are recorded honestly in the TODO index. JVM/source evidence only; device/DB upgrade verification (A01) remains PENDING. Final acceptance remains with the human reviewer.

## 2026-10-19 — Learning pipeline hardening: Step 1 relational-memory repair

Focused repair of the Step 1 foundations, authorized as implementation + push followed by GitHub-hosted CI and an independent review gate. This is NOT Stage 2 (the next stage is D06 safe memory rendering and G02 execution-route audit). No C04-C06 expansion, relation entities, graph traversal, H01, vector caching, or orchestrator redesign. No ObjectBox schema change: `app/objectbox-models/default.json` is byte-for-byte unchanged. Documentation and source changes only; no build or test was run on the VPS or any local machine. Nothing here is device-verified.

### Fixed
- `StringListCodec` (D07): the legacy/envelope discriminator no longer uses the first raw `}`. A JSON-aware structural scan plus strict parsing means a `}` inside a string item no longer misroutes a valid envelope to Legacy, so `encode(listOf("a {b} c", "}", "{\"nested\":1}"))` round-trips and repeat migration is idempotent. Truncated/malformed versioned data and trailing junk surface as Corrupt; unknown versions as UnsupportedVersion; never Legacy and never silently empty. Legacy newline decoding is preserved exactly. The compatibility policy is documented honestly: the discriminator is structural, not a magic prefix, so an ambiguous legacy row that is itself a single JSON object with a `v` field is surfaced and left untouched with NEEDS_ATTENTION rather than guessed at.
- Shared observation store: `EnhancedAIService` and `ConversationService` now share one `ObservationCoordinator`, so the store tool results are written to is the store preflight reads from. The memory space is resolved identically on both sides (`ObservationScopeResolver`). The session generation is stamped as the `sessionId` coordinate and rotated on chat-instance release and execution invalidation; a late tool result from an old generation is rejected.
- Deterministic TTL: `TaskContextSnapshotStore` enforces a bounded default TTL from the injected clock, honors an earlier explicit expiry, fails closed for future observation times / clock rollback, clamps overflow, and uses an explicit expiry boundary (`now >= validUntilMs`). `ToolObservationProducer` accepts an optional explicit expiry; `0` means the store applies the default bound.
- Migration atomicity: `TaskEpisodeRepository` and `SkillRevisionRepository` plan the whole row first (`RowMigrationPlanner`) and mutate/persist only eligible rows; a row with any corrupt/unsupported field is left entirely untouched in memory and on disk.

### Tests
- `StringListCodecTest` grew with braces-in-items round-trip, idempotent repeat migration, truncated/trailing-junk Corrupt, and unknown-version UnsupportedVersion regressions.
- `TaskContextSnapshotStoreTtlTest` (fresh/stale/default-zero-validUntil, explicit earlier expiry, explicit-later-cannot-extend, expiry boundary, future timestamp, clock rollback, overflow, scope mismatch).
- `MigrationAtomicityTest` (later-field failure leaves the row untouched, all-legacy migrates, all-versioned no-op, mixed, corrupt-first, idempotent re-run, unsupported version).
- `ObservationCoordinatorTest` (produced observation reaches preflight in the same scope; cannot cross memory space, task scope, or a rotated session generation; unknown scope does not match a real scope; in-flight old-session completion rejected; memory-space resolution).

### Known limitations
- No PRoot/SSH executor lifecycle exists in this codebase today, so generation rotation cannot be wired to it; the seam is provided and wired to chat-instance release and execution invalidation. `ShellExecutorFactory.clearCache` is not yet wired to rotation because no caller has access to the coordinator.
- Ambiguous legacy-vs-envelope strings cannot be universally disambiguated without a persistent discriminator (a schema change forbidden here); such rows are retained with a needs-attention outcome.
- ObjectBox migration-on-device and upgrade testing cannot be performed from CI; A01 remains PENDING. Nothing in this repair is device-verified.

### Verification
- CI evidence is recorded in `docs/TODO/learning-pipeline-hardening-20261017/index.md` under "Step 1 relational-memory repair". All runs are GitHub-hosted Actions. This is distinct from the historical Step 1 CI: the historical `f39e4358` green runs did not prove this repair's acceptance.

## 2026-10-18 — Learning pipeline hardening: Step 1 (baseline harness, typed observations, D07, R14b)

Implemented Step 1 only of the multi-relational memory upgrade, authorized by Teddy. The later architecture (relational entities, graph expansion, structural matching, vector caching, H01, policy-gate expansion, automatic skill promotion) is recorded in `docs/TODO/learning-pipeline-hardening-20261017/03-step1-execution-plan.md` and is NOT implemented. No ObjectBox schema additions were made. Documentation and source changes only; no build or test was run on the VPS or any local machine. Nothing here is device-verified.

### Added
- Baseline replay harness and evaluation seam (`ReplayFixtures`, `ReplayHarnessTest`, `ReplayEvaluationSeam`): a deterministic, Android-free replay of the existing retrieval/formatter logic over a fixed bounded synthetic fixture set (same entities/different roles, negated prerequisite, partial prerequisites, stale observation, executor mismatch, scope mismatch, analogous structure with different vocabulary, substring satisfaction), split across development and held-out families. Known limitations are asserted as KNOWN LIMITATION, not cemented as desired behaviour. Relates to F01; does NOT complete it.
- Typed observation models (`ObservationRecord`, `TaskContextSnapshot`, `TypedValue`, `ObservationSource`, `TrustLevel`), a pure three-valued evaluator (`PrerequisiteEvaluator`), a bounded in-memory scoped store (`TaskContextSnapshotStore`, injected clock, TTL, record/payload caps), and a minimal real deterministic producer (`ToolObservationProducer`). Relates to C04-C06 and G02; does NOT complete them.
- `StringListCodec`: a shared pure versioned JSON string-list codec (envelope `{"v":1,"items":[...]}`) that is lossless for multiline items, empty items, Unicode, quotes, backslashes, delimiters, order and duplicates, with explicit legacy decoding, corruption surfacing and unsupported-version handling. `StringListMigration` and `migrateListFieldsToVersioned()` on both learning repositories provide an explicit, idempotent, batched migration (not auto-invoked).

### Changed
- `TaskEpisodeEntity` and `SkillRevisionEntity` now encode list fields with `StringListCodec` on write and decode with it on read; existing ObjectBox columns/IDs are unchanged. Legacy untagged rows keep the exact old newline-join decoding; malformed current-format JSON is logged at the entity read boundary and the row is never rewritten; unsupported versions are surfaced.
- `ReviewSignalDeriver.CORRECTION_MARKERS`: removed the neutral retry phrases `"redo"`, `"try again"` and the Chinese `"重新"`; genuine corrective phrases are kept. The explicit correction UI signal remains authoritative.
- `EnhancedAIService.processToolResults` records typed observations from actual tool results into a per-execution `TaskContextSnapshotStore`; `ConversationService` passes a `TaskContextSnapshot` through preflight metadata (`taskContextSnapshot`) alongside the bounded text context. Advisory only; no execution authorization gate and no new model call.

### Tests
- `StringListCodecTest`, `TaskEpisodeEntityCodecTest`, `SkillRevisionEntityCodecTest` (D07 codec and entity round-trip).
- `ObservationRecordTest` (negative/unknown/expiry with fake clock/scope and executor isolation/conflict/invalidation/source separation/producer-to-snapshot wiring/bounded store).
- `ReplayHarnessTest` (fixture replay, known-limitation demonstrations, retrieval recall and caution precision).
- `ReviewSignalDeriverTest` grew with 10 R14b tests (plain retry and ordinary instructions do not set the candidate signal; clear corrections and the mixed "that's wrong, try again" still do; explicit action remains authoritative).

### Verification
- CI evidence is recorded in `docs/TODO/learning-pipeline-hardening-20261017/index.md` under "Step 1 CI evidence". All runs are GitHub-hosted Actions. Nothing in this entry is device-verified; A01 device update-over-existing-install verification (which now also covers D07 adapter-level ObjectBox upgrade compatibility) remains PENDING.

## 2026-10-18 — Learning pipeline hardening: review defects fixed (episode indexing, cancellation) and C03 checklist split

Fixed two defects found in review of the Stage C part 1 work, and corrected the checklist so it no longer overstates what shipped. Documentation and source changes only; no build or test was run on the VPS or any local machine. Nothing here is device-verified.

### Fixed
- Episodes produced by the review scheduler are now added to the episode vector index (`31314e1c`). Previously the scheduler used the non-indexing save, so the vector index was never written and vector retrieval returned nothing in production. An embedding failure no longer fails or retries the review: the episode stays saved and the failure is logged with the episode id.
- `CancellationException` is no longer swallowed by the preflight provider or the review scheduler (`4698f77e`); cancellation now propagates so structured concurrency is preserved.

### Changed
- Docs: split the C03 checklist item into C03a (non-Latin tokenization, bounded query and vector candidate generation — done) and C03d (graph expansion over `MemoryLink` — not implemented, blocked on a schema decision), so the checklist no longer claims graph expansion shipped. The original C03 wording is kept as explicitly superseded text so the history of the item is not lost.

### Verification
- `31314e1c`: Android Build success (https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39126148124) and Android Tests success (https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39126148173). CI-verified.
- `4698f77e`: Android Build success (https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39127121659) and Android Tests success (https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39127121763). CI-verified.
- All runs are GitHub-hosted Actions. No build or test was run on the VPS or any local machine. Nothing in this entry is device-verified; A01 device update-over-existing-install verification remains PENDING.

## 2026-10-18 — Learning pipeline hardening: Stage C part 1 (C01, C02, C03 a/b/c)

Implemented Stage C part 1 (C01, C02 and C03 parts a, b and c) of `docs/TODO/learning-pipeline-hardening-20261017/`. C03 part (d), graph expansion over `MemoryLink`, is NOT implemented and is recorded as a follow-up.

### Added
- `TaskEpisodeVectorIndex`: an episode vector index built on the existing file-backed vector infrastructure, in a separate namespace under the same vector directory (`<filesDir>/.vector_index/episode_hnsw_<profile>_<dimension>.idx`), keyed by episode id. No ObjectBox schema change; the embedding vector is not persisted on `TaskEpisodeEntity`.
- `TaskEpisodeRepository.indexEpisode`: saves an episode, then indexes `taskSummary` + `contextSummary` + `resolutionSummary`.
- `TaskEpisodeRetrieval.mergeCandidates`: unions the lexical and vector candidate sets by episode id, adds `VECTOR_CANDIDATE_BONUS = 0.5` to episodes also returned by vector search, sorts by combined score then `createdAtMs` then `id`, and clamps once to `clampTopK`.
- `TaskEpisodeRetrieval.scoreEpisodes`: returns scored candidates without the top-k clamp; `rankRelevantEpisodes` delegates to it.
- `TaskPreflightFormatterTest` (6 tests): `buildPreflightBlock_applicablePrerequisiteNotEvident_rendersVerifyCaution`, `buildPreflightBlock_applicablePrerequisiteEvident_isNotRenderedAsCaution`, `buildPreflightBlock_nonApplicablePrerequisite_isNotRendered`, `buildPreflightBlock_manyEpisodesAndPrerequisites_staysBounded`, `buildPreflightBlock_neverUsesImperativeMandatePhrasing`, `buildPreflightBlock_chineseFraming_rendersChineseCautionLabel`.

### Changed
- C01 (`07cda3ea`): `ConversationService.prepareConversationHistory` no longer passes `workspacePath` as `currentTaskContext`. It passes the actual user request text (the same `processedInput` as `taskQuery`, capped at `PREFLIGHT_TASK_CONTEXT_MAX_CHARS = 800`) plus a bounded excerpt of the last `PREFLIGHT_RECENT_TURN_COUNT = 4` USER/ASSISTANT turns (capped at `PREFLIGHT_RECENT_TURNS_MAX_CHARS = 600`); `SYSTEM`/`TOOL_CALL`/`TOOL_RESULT`/`SUMMARY` turns are skipped. The `workspacePath` gate is unchanged. `TaskPreflightFormatter` now calls `TaskEpisodeRetrieval.missingPrerequisites` in the production preflight path; "satisfied" is deterministic with no model call (a prerequisite is covered when its normalized key occurs as a substring of the normalized request text). `TaskPreflightProvider` no longer swallows failures with `runCatching {}.getOrDefault("")`; it catches explicitly, logs via `AppLogger.e`, and `""` is the documented "no block produced" no-op. Bounds: `MAX_EPISODES_IN_BLOCK = 5`, `MAX_CHARS_PER_FIELD = 240`, `MAX_PREREQUISITES_PER_EPISODE = 3`, `MAX_BLOCK_CHARS = 2000`.
- C02 (`55151c83`): `isPrerequisiteApplicable` no longer matches on any single shared token. Both sides are reduced to meaningful tokens (tokenizer output minus `ENGLISH_STOPWORDS` (111 entries) and `CHINESE_STOPWORDS` (101 entries), minus purely numeric tokens); a condition or context reducing to no meaningful tokens is not applicable; a condition is applicable only when at least `MIN_DISTINCT_MATCHED_TOKENS = 2` distinct meaningful tokens are shared AND the shared count is at least `MIN_MATCHED_FRACTION = 0.5` of the condition's meaningful tokens. No loosening fallback. `relevanceScore` and `rankRelevantEpisodes` also score on meaningful tokens.
- C03(a) (`a364a032`): Unicode-aware tokenizer. It walks the text once, grouping into maximal CJK runs and maximal non-CJK word runs (`Character.isLetterOrDigit`); a non-CJK run is emitted as one token when its length is at least 2; a CJK run is emitted as every overlapping character bigram, and a 1-character run as that character. CJK blocks: U+4E00-U+9FFF, U+3400-U+4DBF, U+3040-U+309F, U+30A0-U+30FF, U+AC00-U+D7AF. No `android.icu`, no `BreakIterator`. CJK applicability uses contiguous phrase containment: a condition containing CJK tokens is applicable when at least `MIN_CONTIGUOUS_CJK_BIGRAMS = 3` consecutive meaningful bigrams of the condition appear consecutively in the context; non-CJK conditions keep the C02 rule.
- C03(b) (`a364a032`): `retrieveRelevant` no longer calls `all()`. It fetches at most `MAX_CANDIDATES_EXAMINED = 200` episodes with a bounded ObjectBox query ordered by `createdAt` descending and ranks over that window. Documented trade-off: an older episode outside the recency window is not considered.
- C03(c) (`b0bd7705`): `retrieveRelevant` and `TaskPreflightProvider.buildPreflightBlock` are now `suspend`. When no embedding provider is configured, `TaskEpisodeVectorIndex.isEnabled()` is false and `indexEpisode`/`searchEpisodeIds` are documented no-ops that log at debug level — the defined disabled state, not a fallback. The lexical path is a defined member of the merge union, not a fallback.

### Tests
- `TaskEpisodeRetrievalTest` grew 18 → 32 tests: 4 for C01 (`satisfiedPrerequisites_prerequisiteMentionedInRequest_isReturnedAsSatisfied`, `satisfiedPrerequisites_prerequisiteNotMentionedInRequest_isNotReturned`, `satisfiedPrerequisites_emptyRequest_returnsEmpty`, `satisfiedPrerequisites_blankPrerequisite_isNeverSatisfied`), 6 for C02 (`isPrerequisiteApplicable_freshInstallation_doesNotMatchUpdateOnlyPrerequisite`, `isPrerequisiteApplicable_inPlaceUpdate_matchesUpdateOnlyPrerequisite`, `isPrerequisiteApplicable_pathLikeContext_doesNotMatchOnComOrStorage`, `isPrerequisiteApplicable_allStopwordCondition_isNotApplicable`, `isPrerequisiteApplicable_chineseUpdateVersusFreshInstall_behavesTheSame`, `rankRelevantEpisodes_stopwordsDoNotCreateRelevance`), 6 for C03(b) (`scoreEpisodes_returnsScoredCandidatesWithoutTopKClamp`, `mergeCandidates_vectorHitReceivesBonusAndOutranksLexicalOnly`, `mergeCandidates_tiesBrokenByCreatedAtMsThenId`, `mergeCandidates_emptyVectorIds_equalsLexicalRanking`, `mergeCandidates_resultIsClampedToTopK`, `maxCandidatesExamined_isAPositiveDocumentedCap`) and 2 for C03(c) (`mergeCandidates_vectorOnlyHitWithZeroLexicalScore_stillSurvivesViaBonus`, `mergeCandidates_lexicalEntryWinsOnDuplicateId`).
- `isPrerequisiteApplicable_chineseUpdateVersusFreshInstall_behavesTheSame` was added in C02 with `@Ignore("Requires the C03 Unicode-aware tokenizer")` because the old `[^a-z0-9]+` tokenizer collapsed CJK into one token; C03(a) removed the `@Ignore` and the test now runs and passes.
- Three `TaskPreflightFormatterTest` contexts were widened in C02 from "perform an in-place apk update; the signing key may have changed" to "perform an in-place apk update; verify signing and continuity of the release key", because C02 intentionally makes a bare "signing continuity" prerequisite inapplicable to a context sharing only "signing". The tests' intent was preserved; no assertion was weakened.

### Verification
- C01 (`07cda3ea`): Android Build run 39122350953 and Android Tests run 39122350976 were cancelled by the concurrency group (superseded by the C02 push); covered by the green run on `55151c83`.
- C02 (`55151c83`): Android Tests success (https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39122619106); Android Build run 39122619089 cancelled by the concurrency group (superseded by the C03 push).
- C03(a)+(b) (`a364a032`): Android Build run 39123369842 and Android Tests run 39123369812 cancelled by the concurrency group (the latter mid-test-execution); covered by the green run on `b0bd7705`.
- C03(c) (`b0bd7705`): both Android Build (run 39123568648, https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39123568648) and Android Tests (run 39123568673, https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39123568673) succeeded.
- The `b0bd7705` tree contains C01, C02, C03(a), C03(b) and C03(c) together, so its green Android Tests run verifies that the whole of Stage C part 1 compiles and its JVM tests pass.
- All runs are GitHub-hosted Actions. No build or test was run on the VPS or any local machine.

### Notes
- C03 part (d), graph expansion over `MemoryLink`, is NOT implemented and is a recorded follow-up. `MemoryLink.source`/`target` are `ToOne<Memory>` and `TaskEpisodeEntity` has no link field, so episode-to-memory and episode-to-episode edges need a new ObjectBox entity (for example `EpisodeLinkEntity` with `sourceEpisodeId`, `targetEpisodeId`, `memoryId`) or new fields on `TaskEpisodeEntity`. Encoding ids into `MemoryLink.description` or `type` strings would give no referential integrity and no indexed traversal, so it is deliberately not faked. Recorded as a follow-up comment in `TaskEpisodeRepository`. Intended edges: episode -> failure, episode -> fix, episode -> prerequisite.
- Documented limit of the "satisfied" rule: a request that merely mentions a prerequisite is treated as covering it, so every remaining item stays framed as a caution to verify.
- A01 device update-over-existing-install verification is still PENDING and its checklist item remains unchecked. Nothing in this job is device-verified.

## 2026-10-18 — Learning pipeline hardening: R17 (dedicated reviewer model) and R14b (retry markers) recorded (docs only)

Recorded two new findings and their checklist items in `docs/TODO/learning-pipeline-hardening-20261017/`. Documentation only: no source code, resource, workflow or checkbox changed; no build or test run.

### Added
- R17 in `01-findings.md`: reviews run on the `MEMORY` and `SUMMARY` models (`ReflectorCurator.kt:42` and `:69`) and there is no dedicated reviewer/teacher model. Records the proposed `REVIEW` function type, the three exhaustive `when` branches in `FunctionalConfigScreen.kt` that must gain a branch, the string resources, the settings privacy text, and the requirement to record the model config id/name/provider on every review so the Stage F baseline/same-model/stronger-model comparison can be measured (left UNMEASURED until replays exist).
- R14b in `01-findings.md`: `"try again"` and `"redo"` remain in `ReviewSignalDeriver.CORRECTION_MARKERS` (`ReviewSignalDeriver.kt:73-74`); `d3535a5a` changed only the retry test input, not the marker list, so a plain retry request is still flagged as a candidate correction.
- Checklist items H01 (R17) and R14b (R14 follow-up) in `index.md`, both unchecked.
- Stage H01 text in `02-implementation.md`, placed after Stage B and before Stage D because Stage D's Curator wiring depends on it.

### Changed
- `index.md` checkpoint refreshed: exact commit `f2547e57`, next action Stage C unless the user reprioritizes H01, blockers unchanged (A01 device verification pending; R14-UI and R14b open follow-ups).

### Notes
- Verified at HEAD `f2547e57`: the Reflector/Curator routing, the absence of a review-specific model setting, `normalizeMapping` defaulting and unknown-key skipping, the three exhaustive `when` expressions, the `ROLE_RESPONSE_PLANNER` precedent, and the `CORRECTION_MARKERS` contents. One claim was corrected: `ApiProviderType` has 38 provider types, not "about 40".
- Known unresolved security item (unchanged): earlier signing-document redaction does not erase exposed passwords from git history. No rotation or history rewrite is performed under this task.


## 2026-10-18 — Learning pipeline hardening: Stage B, R14 and R15

Implemented Stage B (B01, B02) and the Stage A review refinements R14 and R15 of `docs/TODO/learning-pipeline-hardening-20261017/`.

### Added
- `PendingReviewBatchPolicy` (`app/src/main/java/com/ai/assistance/operit/data/repository/PendingReviewBatchPolicy.kt`): a pure decision core for the review drain — `MAX_ATTEMPTS = 3`, `PROCESSING_STALE_AFTER_MS = 10 min`, `dispositionFor`, `isStaleProcessing`.
- `ReviewLessonLanguage` (`app/src/main/java/com/ai/assistance/operit/data/learning/ReviewLessonLanguage.kt`): a pure locale decision that derives `useEnglish` from the user's locale, mirroring `LocaleUtils.usesChineseContent`.
- Terminal `STATUS_DEAD_LETTER` on `PendingReviewEntity`, with `getDeadLettered()` for inspection.
- A recovery sweep in `ReviewScheduler.start()` that returns stale `PROCESSING` rows to `PENDING`.
- A `Verify committed ObjectBox model is byte-identical` step in `.github/workflows/objectbox-model.yml` that fails the workflow when a regeneration differs from the committed model.

### Changed
- B01 (`79a17f54`): `drainPendingReviewsForSpace` now decides each review individually and deletes only the ids that produced an episode; a review whose `reflect()` returned null is marked failed on its own and stays retryable; a review reaching `MAX_ATTEMPTS` (3) is dead-lettered and never retried or deleted; stale `PROCESSING` rows are recovered on start. Per-space failure isolation from `58bedd67` is preserved.
- B02 (`68931d45`): `useEnglish` is no longer hardcoded; it is derived from the user's locale once per batch.
- R14 (`72c98edc`): `ReviewSignals` now carries the raw `ObservedTurnEvents` in `ReviewSignals.observed` alongside the interpreted booleans; `complexSuccessWithReusableLesson` was renamed to `complexSuccessfulTurn` and the reason string is now "complex successful turn warranted review" and no longer claims a lesson; correction detection uses word-boundary matching, the bare `"wrong"` marker was removed and replaced by specific phrases, a lexical match now sets `candidateCorrection` only, and only a deliberate action sets `explicitUserCorrection`. `ReviewTriggerPolicy` remains the single decision point; a candidate alone may trigger a review, checked last and bounded by the preceding-assistant-turn plus matched-marker requirement.
- R15 (`2703b216`): the ObjectBox Model workflow now fails on a non-byte-identical regeneration (and when the model was not generated); the step has no `if:` condition and no `continue-on-error`, and the print/upload steps keep `if: always()`.

### Fixed
- Reviews are no longer silently lost when one review in a batch fails, and permanently failing reviews no longer retry forever.
- Rows stranded in `PROCESSING` after a crash are recovered instead of leaking permanently.
- Chinese-locale users no longer receive English lessons.
- CI failure on `72c98edc`: Android Tests run 39094387036 failed one JUnit assertion, `ReviewSignalDeriverTest.retry_doesNotSetExplicitCorrection` at `ReviewSignalDeriverTest.kt:222`, because the test used "try again" as its input while "try again" is itself a `CORRECTION_MARKERS` entry, so `candidateCorrection` was legitimately set (1423 tests ran, 1 failed, 6 skipped). The root cause was the test's input, not the implementation; fixed in `d3535a5a` by using marker-free text.

### Tests
- New `ReviewSchedulerBatchTest` (9 tests): `batchOfThreeWithOneFailure_deletesExactlyTwo`, `reviewFailingPastMaxAttempts_isDeadLettered`, `deadLetteredReview_isNotRetried`, `reviewBelowMaxAttempts_staysRetryable`, `staleProcessingRow_isRecovered`, `freshProcessingRow_isLeftAlone`, `processingRowExactlyAtStalenessBoundary_isNotStale`, `nonProcessingStatus_isNeverStale`, `succeededReview_isDeleted`.
- New `ReviewLessonLanguageTest` (7 tests): `chineseLocale_requestsChinese`, `chineseRegionVariant_requestsChinese`, `englishLocale_requestsEnglish`, `otherSupportedLocale_requestsEnglish`, `uppercaseChineseCode_isStillChinese`, `blankCode_requestsEnglish`, `lessonLanguage_matchesUseEnglish`.
- `ReviewSignalDeriverTest` grew 13 → 23 tests: 4 complexity tests renamed to the `complexSuccessfulTurn` name, 3 correction tests updated to candidate semantics, and 10 R14 tests added (`retry_doesNotSetExplicitCorrection`, `edit_doesNotSetExplicitCorrection`, `normalReplyBeginningWithNo_doesNotSetExplicitCorrection`, `wrongInsideAnotherWord_doesNotSetCandidate`, `wrongAsUnrelatedTopic_doesNotSetCandidate`, `lexicalMatch_setsCandidateOnly`, `explicitInput_setsExplicit`, `complexButUnremarkableTurn_triggersReviewWithoutAssertingLesson`, `observedEvents_areRetainedAlongsideSignals`, `candidateCorrection_aloneTriggersBoundedReview`).
- `ReviewTriggerPolicyTest` grew 8 → 10 tests: `complexSuccessWithReusableLesson_triggersReview` renamed to `complexSuccessfulTurn_triggersReview` (now asserting the reason does NOT contain "lesson"), plus `candidateCorrection_triggersReview` and `explicitCorrection_takesPrecedenceOverCandidate`.
- No test was deleted. `ReviewSchedulerMemorySpaceTest` (5 tests) is unchanged and still green.

### Verification
- Final green CI on `d3535a5a`: Android Build success (https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39095530659), Android Tests success (https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39095530635). That tree contains B01, B02, R14, R15 and the R14 test fix together, so its green runs verify the whole of Stage B plus R14 and R15 compile and their JVM tests pass.
- B01 (`79a17f54`) and B02 (`68931d45`): Android Build and Android Tests runs were cancelled by the concurrency group (superseded by the next push), not by a code failure; zero failing steps. Covered by the green run on `d3535a5a`.
- R14 (`72c98edc`): Android Build success (https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39094387037); Android Tests failure (https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39094387036), fixed in `d3535a5a`.
- R15 (`2703b216`) touches only `.github/workflows/objectbox-model.yml`, which is outside the `paths:` filter of Android Build and Android Tests, so neither workflow ran for that SHA; ObjectBox Model is workflow_dispatch-only.
- ObjectBox Model run 39096814734 (https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39096814734) succeeded on `d3535a5a`; the `Verify committed ObjectBox model is byte-identical` step concluded success and printed `ObjectBox model is byte-identical to the committed file.` at runtime, proving `git diff --exit-code` returned 0.
- All runs are GitHub-hosted Actions. No build or test was run on the VPS or any local machine.

### Notes
- A01 device update-over-existing-install verification is still PENDING and its checklist item remains unchecked. Nothing in this job is device-verified.
- R14 finding: there is no explicit user-initiated "this reply is wrong" control in the app. Searches for `markWrong`, `markAsWrong`, `userCorrection`, `explicitCorrection`, `reportIncorrect`, `flagWrong`, `thumbsDown`, `dislike`, `isWrong` return zero hits; the only user-initiated re-run action is `regenerate`, which is a retry, not a correction marker. The explicit-signal input (`TurnObservations.explicitCorrectionAction`) is implemented but set by nothing in production. No UI was built; the R14-UI follow-up is open.
- B02 limitation: the generation language is not persisted with the lesson. `TaskEpisode`/`TaskEpisodeEntity` have no language column; adding one would change the ObjectBox schema and require regenerating `default.json`, which was out of scope. Recording it is a follow-up requiring a model regeneration.
- Byte-identical regeneration is necessary but is not proof of upgrade compatibility.
- Stage C is not started.

## 2026-10-17 — Learning pipeline hardening: Stage A review amendments (docs only)

- Recorded R14 (review signals conflate the observed event with its interpreted meaning), R15 (ObjectBox schema lineage recorded but upgrade compatibility not yet proven) and R16 (keep review trigger, lesson confidence and promotion evidence separate) in docs/TODO/learning-pipeline-hardening-20261017/01-findings.md.
- Promoted G01 (per-lesson outcome tracking) and G02 (dispatch-level action guard) into the ordered checklist with finding text, acceptance tests and stage placement.
- Amended E02/N03 (retrieve compact episode indexes first, expand only relevant trace segments), C06 (dismissal is not evidence a lesson is wrong) and F02 (tune only on held-out replay cases) by adding superseded-wording notes; no existing text was deleted; the checkpoint Blockers line was extended, not reworded.
- Recorded an explicitly deferred backlog: hypergraph subtask/skill links, multi-attempt sampling with a verifier, GEPA-style prompt optimization, validity-window facts, retrieval keyed on current step or error, duplicate consolidation and pruning, Mem0-style add/update/delete/no-op decisions.
- Updated the index.md checkpoint (owner, state, next action Stage B, blocker A01 device verification pending).
- Documentation only: no source code, workflow or checkbox changed; no build or test run.

## 2026-10-17 — learning pipeline hardening, Stage A

Implemented Stage A of `docs/TODO/learning-pipeline-hardening-20261017/`: the review queue now fills, and it drains from the memory space the review was written to.

### Added
- New CI workflow `.github/workflows/objectbox-model.yml` (workflow_dispatch only) that regenerates and uploads the ObjectBox model.

### Changed
- A01: committed the ObjectBox model for `TaskEpisodeEntity`, `SkillRevisionEntity` and `PendingReviewEntity` (ids 12/13/14), generated by the new `ObjectBox Model` workflow; purely additive, no existing entity changed. Device update-over-existing-install verification pending.
- A02: `ReviewSignals` are now derived from deterministic turn outcomes (tool results, model hops, conversation turns) instead of hardcoded `false`, so the review queue actually fills.
- A03: `ReviewScheduler` drains every memory space with pending reviews instead of the hardcoded `"default"` space, and saves each episode into the space it was enqueued in.

### Notes
- A01's device update-over-existing-install verification has not been performed and remains pending; its checklist item stays unchecked.
- Stage B (batch-delete correctness, retry cap, `PROCESSING` recovery, locale-aware `useEnglish`) is not started.


## 2026-10-17 — learning pipeline hardening review

Recorded an independent static review of the learning pipeline as an ordered, checkable TODO in `docs/TODO/learning-pipeline-hardening-20261017/`. Documentation only; no source code changed.

### Added
- `docs/TODO/learning-pipeline-hardening-20261017/` with `index.md` (ordered checklist A01-F02), `01-findings.md` (per-finding evidence, impact, fix and acceptance test) and `02-implementation.md` (staged order A-F).
- README link to the new TODO alongside the existing active TODO link.

### Notes
- Every finding was re-verified against commit `28b53abd` before being recorded. Three findings were corrected rather than copied forward: there is no `entity12`/`MemoryLedger` in the schema (the strings appear only in prose), `ReflectorCurator.reflect` is wired but unreachable rather than entirely unwired, and `getPendingAndFailed` excludes `PROCESSING` rows rather than including them.
- The T09/T10 checklist marks in `docs/TODO/reliable-learning-architect-20261016/index.md` overstate the wiring; a visible note was added under T07-T10 without deleting the history.
- Known unresolved security item: earlier signing-document redaction does not erase exposed passwords from git history. No rotation or history rewrite is performed under this task.


## 2026-10-16 — reliable learning and persistent architect workflow

Implemented the bounded specification in `docs/TODO/reliable-learning-architect-20261016/` (stages A–E).

### Added
- Durable project requirements in `AGENTS.md` and a runtime architect-rules hook pinned into the existing `SystemPromptComposeHook` (task start/resume) and `SummaryGenerateHook` (compaction) registries, scoped to development/workspace tasks. Completion-gate helper (`ArchitectCompletionGate`).
- Root `CHANGELOG.md` and README links to the changelog and active TODO.
- Structured task episodes (`TaskEpisode`) with independent-evidence deduplication, bounded similar-task retrieval (top-k 3–5), and conditional missing-prerequisite applicability checks (`TaskEpisodeRetrieval`).
- Bounded similar-task preflight for development tasks (`TaskPreflightProvider`/`TaskPreflightFormatter`), pure retrieval with no extra model call.
- Versioned candidate skill revisions (`SkillRevision`) with a constraint-preserving promotion lifecycle (draft → evaluate → promote/reject) and rollback (`SkillRevisionLifecycle`).
- Logical Reflector/Curator roles (`ReflectorCurator`) reusing existing configured model providers; bounded review-trigger policy (`ReviewTriggerPolicy`); resumable review scheduler (`ReviewScheduler`).

### Fixed
- Inactive/out-of-scope memory resurrection: graph expansion (both outgoing links and backlinks) and the final result path now enforce the allowed active + in-scope id set (`MemoryGraphScopeFilter`). Historical access is preserved; only normal active retrieval is filtered.

### Tests
- 47 new JVM regression tests: `MemoryGraphScopeFilterTest` (8), `TaskEpisodeRetrievalTest` (14), `SkillRevisionLifecycleTest` (12), `ReviewTriggerPolicyTest` (7), `ReflectorCuratorParseTest` (6).

### Verification
- GitHub-hosted CI on commit `c0bba97a`: Android Build success (https://github.com/Mavaebrook/HandleIT-Android/actions/runs/38958024774) and Android Tests success (https://github.com/Mavaebrook/HandleIT-Android/actions/runs/38958024652).
- An earlier CI run on `ef2f3bc5` failed at `:app:compileDebugKotlin` (4 Kotlin compile errors); fixed in `c0bba97a`.
- Device acceptance remains pending (no actual device evidence available).

### Notes
- Full GEPA prompt search, automatic executable-skill synthesis, wholesale memory-backend replacement, new model subscriptions, credential rotation, history rewriting, and signing-key replacement remain deferred.
- Teacher-comparison evaluation (baseline vs same-model vs stronger-model) was not measured; no stronger configured model access was confirmed.
- Device acceptance remains pending (no actual device evidence available).
- Known unresolved security item: earlier signing-document redaction does not erase exposed passwords from git history. No rotation or history rewrite is performed under this task.

## 2026-10-16

### Added
- Tracked TODO for reliable learning and persistent architect workflow: `docs/TODO/reliable-learning-architect-20261016/` (design, implementation sequence, and progress checklist).
- Durable project requirements section in `AGENTS.md`, pinned separately from salience-ranked memory so development constraints survive task resume and context compaction.
- Runtime architect-rules preflight/resume/compaction hook and completion gate (see `docs/TODO/reliable-learning-architect-20261016/`).

### Notes
- Full GEPA prompt search, automatic executable-skill synthesis, wholesale memory-backend replacement, new model subscriptions, credential rotation, history rewriting, and signing-key replacement remain deferred.
- Known unresolved security item: earlier signing-document redaction does not erase exposed passwords from git history. No rotation or history rewrite is performed under this task.
