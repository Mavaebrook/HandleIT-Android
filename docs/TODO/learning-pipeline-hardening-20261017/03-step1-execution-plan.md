# Step 1 execution plan and acceptance matrix

This document is the written execution plan for **Step 1 only** of the multi-relational memory upgrade, authorized by Teddy. It is a companion to [index.md](index.md), [01-findings.md](01-findings.md) and [02-implementation.md](02-implementation.md). It does not replace them; it reconciles the Step 1 work against the existing IDs so nothing is duplicated or falsely checked.

## Scope of Step 1 (authorized)

1. Baseline replay fixtures and evaluation seam (relates to F01, but does NOT complete F01).
2. Typed observations and bounded `TaskContextSnapshot` production plumbing (relates to C04-C06 and G02, but does NOT complete them).
3. D07 lossless list serialization with explicit compatibility migration.
4. R14b correction-marker cleanup.

## Explicitly NOT in Step 1 (recorded for later, not implemented)

- Relational entities (`RelationFrameEntity`, `FrameMembershipEntity`, `PatternEntity`).
- Graph expansion (C03d), structural matching, vector caching.
- H01 reviewer-model setting.
- Policy-gate expansion (G02) and automatic skill promotion.
- Any ObjectBox schema addition. If a schema addition turns out to be unavoidable, Step 1 finishes the independent safe work and documents the precise blocker instead.

## Invariants that bind every Step 1 change

- All Gradle builds and tests run ONLY in GitHub-hosted Actions (`Android Build`, `Android Tests`). No build or test on VPS, local host, phone or PRoot.
- Preserve namespace/applicationId `com.ai.assistance.operit` and `.debug`, internal Operit classes, licensing, signing identity/certificate, the `a9d4849` notification behaviour, existing data and ObjectBox IDs/UIDs.
- Never modify `entity12`/`MemoryLedger`.
- No secrets output, no credential rotation, no history rewrite, no speculative fallback chains, no silent catches, no test weakening.
- Cancellation must propagate.
- No new model calls on preflight.
- No ObjectBox schema additions in Step 1.
- Legacy decoding below is an explicit version-compatibility requirement, not permission for exception-driven fallback.

## Acceptance matrix

| Step 1 item | Acceptance evidence | Existing ID relationship | Status after Step 1 |
| --- | --- | --- | --- |
| Baseline replay fixtures + evaluation seam | Deterministic Android-free replay harness over existing retrieval/formatter logic; fixed bounded fixture set covering the eight required scenarios; episode families split dev/held-out; current outputs and known limitations captured; retrieval and prerequisite metrics that fixtures actually support; extension seams for later comparison | Relates to F01; does NOT complete F01 (no real-task fixtures, no A/B promotion gate) | Implemented + CI-verified; F01 stays unchecked |
| Typed observations + bounded `TaskContextSnapshot` | Pure typed `ObservationRecord`/`TaskContextSnapshot` models; minimal real deterministic producer wired from tool execution results; snapshot passed through production preflight alongside bounded text context; pure Satisfied/Contradicted/Unknown evaluator for supported typed predicates; bounded in-memory scoped store with injected clock and TTL; tests for negative/unknown/expiry/scope/executor isolation/conflict/invalidation/source separation/producer-to-snapshot wiring | Relates to C04-C06 and G02; does NOT complete them (no schema extension, no plan-time gap check, no execution authorization gate) | Implemented + CI-verified; C04-C06 and G02 stay unchecked |
| D07 lossless list serialization | Shared pure versioned JSON string-list codec; preserves multiline/empty/Unicode/quotes/backslashes/delimiters/order/duplicates; existing ObjectBox columns/IDs unchanged; documented version envelope for new writes; untagged rows keep defined legacy decoding; explicit idempotent migration/read/write; malformed current-format JSON surfaced not reinterpreted; tests for roundtrip/old rows/malformed/unsupported version/repeat migration/mixed datasets | Is D07 | Implemented + CI-verified; A01 device verification stays PENDING |
| R14b correction-marker cleanup | Neutral retry phrases (`try again`, `redo`, analogous neutral phrases across supported languages) removed as standalone correction signals; genuine corrective phrases kept; explicit correction UI signal remains authoritative; tests use formerly problematic inputs, ordinary instructions containing those phrases, clear corrective statements, and mixed "that's wrong, try again" | Is R14b | Implemented + CI-verified; R14-UI stays outstanding |

## Reconciliation notes (no duplication, no false checks)

- **F01** stays unchecked. Step 1 builds the replay harness and fixtures that F01 needs, but F01 also requires real-task fixtures and a baseline/with-lesson/without-lesson promotion comparison, which Step 1 does not do.
- **C04-C06** stay unchecked. Step 1 adds typed observation plumbing and a snapshot evaluator, but C04 (episode schema extension), C05 (Reflector prompt) and C06 (plan-time gap check) are not implemented.
- **G02** stays unchecked. Step 1 adds no execution authorization gate.
- **D07** is completed by Step 1 (codec + migration + tests), with A01 device verification still pending.
- **R14b** is completed by Step 1 (marker cleanup + tests). R14-UI stays outstanding.
- **C03d** stays blocked on a schema decision; Step 1 adds no schema.

## Later architecture (recorded, NOT implemented in Step 1)

- Keep the current graph/vector backend. Three physical entity starting design: `RelationFrameEntity` (versioned conditions / bounded evidence payload), `FrameMembershipEntity` (indexed reverse lookup), `PatternEntity` (versioned abstraction). The count is a design choice subject to measured access patterns, not dogma.
- Indexed `roleTargetKey` encodes a collision-safe VERSIONED tuple `[memorySpace, projectScope, role, targetType, targetId]` via canonical encoding, not raw pipe concatenation. Use the pinned ObjectBox API, case-sensitive exact queries, indexed `frameId`. A batched membership IN query is a union; group frame IDs and verify all required role-target pairs for conjunction. Preserve multiplicity/order where semantics require them. Update frame and memberships transactionally; handle edits/retirement/deletion without stale searchable links. No claim of O(1) total retrieval or one native cursor pass without measurement.
- Structural similarity and procedure applicability are separate. Later retrieval uses semantic/exact/structural candidates, bounded expansion and deterministic verification. Preserve role, direction, order and scope; do not treat hashes as proof of isomorphism. Patterns must retain evidence and counterexamples.
- Before active retrieval: strengthen data/authority separation in the formatter and inventory all execution paths for shared policy enforcement; escaping/XML alone is not injection prevention and guards only cover explicitly enforced policies.
- Later vector lifecycle: bounded cache keyed by space + model identity/version + dimension, serialized mutations, capacity growth, eviction/invalidation, atomic persistence and observable save failure. The current manager uses `ObjectInputStream` and dynamically grows from `maxElements=1`; the candidate limit is not index capacity.
- H01 reviewer-model setting before production frame extraction, not a prerequisite for Step 1. No automatic new provider subscription.
- Later replay comparisons and device measurement before active rollout; no HDC/hyperbolic/sheaf work now. Keep the existing behaviour rollback design subject to AGENTS.md/user authorization; do not add forbidden fallback logic.
