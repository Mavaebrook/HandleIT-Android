# Step 1 relational-memory repair plan

## Context and authorization
Follow-up to bridge #35 (exhausted 50 turns). Verified HEAD on 2026-10-19 is `f39e4358d22bb2afc8cfebde47564066554a7b8e`; original Step 1 baseline was `5f9f1bf16ffa30f0947eb86f19341936b779e7b9`. This is authorized implementation and push, followed by GitHub-hosted CI and an independent review gate.

HARD SCOPE: only repair the Step 1 foundations below. NO Stage 2 implementation (the next stage is D06 safe memory rendering and G02 execution-route audit, not merely a dispatch-level gate), no C04-C06 expansion, relation entities, graph traversal, H01, vector caching, or orchestrator redesign.

Invariants: `app/objectbox-models/default.json` byte-for-byte unchanged; no new ObjectBox entities/UID allocation; no entity12/MemoryLedger changes; preserve package `com.ai.assistance.operit`, `.debug`, internal Operit classes, licensing, existing data/IDs, the a9d4849 notification snapshot, and signing identity/certificate. No secrets output, rotation, or history rewriting. All builds/tests ONLY through GitHub-hosted Android Build and Android Tests Actions; NEVER VPS/local/phone/PRoot Gradle execution. Preserve the independent review gate. Legacy version decoding is explicitly authorized compatibility behavior; never catch malformed new data and reinterpret it as legacy.

## Repair 1 — StringListCodec.kt / D07 version discriminator
Current bug: `looksLikeEnvelope` uses the first raw `}` (`StringListCodec.kt:216`), so `encode(listOf("a {b} c", "}", "{\"nested\":1}"))` decodes as Legacy and repeat migration double-encodes. A `}` inside a JSON string item terminates the scan early.

Planned fix:
- Replace shape guessing with an explicit version discriminator and real JSON parsing (a JSON-aware scan that respects string literals/escapes/nesting, or a leading magic prefix plus strict parse).
- Preserve legacy newline decoding semantics exactly (`split("\n").map{trim()}.filter{isNotEmpty()}`), including JSON-shaped legacy `{"a":1}`, whitespace and empty-row interpretation.
- Preserve lossless NEW list elements: empty strings, whitespace, literal newlines, braces, quotes, escapes, Unicode.
- Recognized malformed/truncated versioned data and trailing junk must surface Corrupt; unknown versions explicitly UnsupportedVersion, NEVER Legacy or silently empty.
- Add round-trip, repeat-migration/idempotency, malformed, truncated, trailing junk and unknown-version regressions.
- COMPATIBILITY REALITY (documented honestly): a magic prefix alone cannot prove collision-free separation from arbitrary preexisting legacy text, and `f39e4358` already writes unprefixed `{"v":1,"items":...}`. Devise/document an explicit compatibility/migration policy for BOTH pre-Step1 rows and existing Step1 envelopes. Do not silently reinterpret ambiguous old strings or falsely claim universal unambiguous detection. Retain ambiguous raw data with a surfaced needs-attention outcome when origin cannot be established. If a persistent discriminator truly requires schema changes forbidden here, report that specific blocker and finish the other repairs rather than guessing/destroying data. Do not assume this APK was never installed.

## Repair 2 — Shared observation store and production authority
Current defect: `EnhancedAIService.MessageExecutionContext` owns the written store (`EnhancedAIService.kt:446-447`, per-message); `ConversationService` owns a DIFFERENT empty read store (`ConversationService.kt:101`). `memorySpace` is resolved as `override ?: ""` (`EnhancedAIService.kt:2325`) vs `effectiveMemorySpaceId` (`ConversationService.kt:563-565`). All other coordinates are hard-coded `""`.

Planned fix:
- Fix ownership/injection so actual tool-result production reaches actual preflight snapshot retrieval (a shared coordinator/store injected into both, and production actually invokes it).
- Resolve effective memorySpace identically.
- Populate verified matching coordinates for memory-space, project/workspace, task/chat scope where applicable, executor identity, environment and session generation/ID. Resolve the actual executor used, not simply the selected UI tool or a stale workspace. Unknown coordinates must not grant authority because empty strings match. Do not invent identities or infer can_write from generic success.
- Wire real workspace/chat/memory-space changes and PRoot/SSH restart/disconnect/replacement lifecycles into invalidation or generation rotation. Reject late tool results from old generations. Audit actual producers and reset callers, not only data models. Preserve bounded storage/thread safety. If a route lacks trusted scope, retain it only as nonauthoritative diagnostics or omit it from applicability with a documented limitation.
- Add regression coverage through the shared production wiring/coordinator used by EnhancedAIService and ConversationService, proving a produced observation reaches preflight in the same scope, and cannot cross workspace, memory-space, executor or restarted PRoot/SSH session. Include unknown scope and in-flight old-session completion. Pure Kotlin test seams are welcome but production must actually invoke them; a test manually sharing an unrelated store is insufficient.
- LIMITATION: no PRoot/SSH executor lifecycle exists in this codebase today (`ShellExecutorFactory` caches executors; no restart/disconnect hooks). The generation-rotation seam must be added at the actual executor-replacement point (`ShellExecutorFactory.clearCache` / executor selection); a route that lacks trusted scope is retained only as nonauthoritative diagnostics.

## Repair 3 — Deterministic TTL
Current defect: `TaskContextSnapshotStore` `ttlMs` is unused (`TaskContextSnapshotStore.kt:17`); `ToolObservationProducer` sets `validUntilMs=0` (`ToolObservationProducer.kt:64,96`).

Planned fix:
- Enforce a bounded default TTL for production records via an injected clock; honor any earlier explicit expiry.
- Fail closed for future observation times / `now < observedAt`, handle overflow and the expiry boundary explicitly.
- Include deterministic fresh/stale/default-zero-validUntil, explicit expiry, clock rollback/future timestamp tests.
- If using monotonic time, keep its time domain separate from wall-clock provenance and session/boot lifetime; no mixing.
- Ensure the evaluator cannot reauthorize stale records.

## Repair 4 — Migration atomicity/cleanup
Current defect: `TaskEpisodeRepository.migrateBatch` (`TaskEpisodeRepository.kt:192-234`) and `SkillRevisionRepository.migrateBatch` (`SkillRevisionRepository.kt:93-135`) mutate fields via `setListField` BEFORE discovering a later corrupt field; the in-memory entity is left half-migrated (`put()` is skipped so the DB is safe, but the object is dirty inside the transaction).

Planned fix:
- Stage all field outcomes first, mutate/persist only eligible whole rows; NEEDS_ATTENTION rows retain original values.
- Add a meaningful regression for later-field failure and idempotent re-run.

## Documentation and evidence backfill
- Backfill Step1 evidence in `docs/TODO/learning-pipeline-hardening-20261017/index.md`: `f39e4358d22bb2afc8cfebde47564066554a7b8e`, Android Build https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39144816330 and Android Tests https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39144816266 both succeeded. Distinguish that historical CI from the new repair CI; historical success did not prove acceptance.
- Preserve R14b literal "try again"/"redo" neutral tests and genuine/mixed corrective positive tests, CJK coverage, replay dev/held-out separation and honest known limitations. No widening completion claims.

## Delivery
- Commit and push scoped changes; record exact full job commit SHAs and base-to-head diff. Run/verify BOTH GitHub-hosted workflows for the exact repair code SHA, include run links and test evidence. Confirm `default.json` unchanged. Distinguish JVM/source evidence from ObjectBox migration-on-device and upgrade testing (do not claim device verification).

## Known limitations (honest)
- No PRoot/SSH executor lifecycle exists today; generation rotation must be added at the real executor-replacement point.
- ObjectBox migration-on-device and upgrade testing cannot be done from CI; remains PENDING (A01).
- Ambiguous legacy-vs-envelope strings cannot be universally disambiguated without a persistent discriminator; such rows are retained with a needs-attention outcome.
