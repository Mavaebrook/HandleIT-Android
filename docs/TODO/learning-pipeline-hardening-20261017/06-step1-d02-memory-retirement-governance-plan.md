---
repository: https://github.com/Mavaebrook/HandleIT-Android
created: 2026-10-21
status: partial
base_commit: 838deba5c3f7ea86c71265046a09a039e86a0d94
branch: fix/memory-retirement-governance
---
# D02 memory-retirement governance gate and reversible retirement

This is the scoped plan for checklist item D02 in [index.md](index.md): add a governance gate before retiring a `CORRECTS`/`SUPERSEDES` target, verify direction and recency, check active user constraints, and make retirement reversible. It is a planning document only. No source code is changed by this commit.

The stage description it implements is in [02-implementation.md](02-implementation.md), Stage D. The finding it addresses is R08 in [01-findings.md](01-findings.md).

## Context and problem

Today a model-produced link can permanently retire a memory fact with no governance at all. The whole path is four lines of code.

In `app/src/main/java/com/ai/assistance/operit/api/chat/library/MemoryLibrary.kt` lines 531-568, the link-creation loop resolves both endpoints by title only. The source is resolved at line 534 and the target at line 538, both through `memoryRepository.findMemoryByTitle(...)`. There is no check that the title is unique, no check that the resolved memory is the one the model meant, and no check on the direction of the relation.

When the link type is `CORRECTS`, line 549 calls `memoryRepository.markMemoryInactive(target)` and then writes a normalized `SUPERSEDES` audit link at lines 550-556. When the link type is `SUPERSEDES`, line 559 calls `memoryRepository.markMemoryInactive(target)` directly. The only guard is the self-link check at line 546, `if (source.id != target.id)`. Nothing else stands between a model assertion and permanent retirement.

In `app/src/main/java/com/ai/assistance/operit/data/repository/MemoryRepository.kt` lines 343-348, `markMemoryInactive` writes a single `status=INACTIVE` property through `writeProperty`. The write is one-way. It records no prior state, no reason, no link type, no source id and no timestamp. There is no reactivation path anywhere in the repository, so a wrong retirement is unrecoverable except by direct database editing.

In `MemoryRepository.kt` line 963, `findMemoryByTitle` runs `memoryBox.query(Memory_.title.equal(title)).build().findFirst()`. It returns an arbitrary first row. It applies no status filter and no ordering, so when two memories share a title the caller silently gets whichever row ObjectBox returns first. The sibling `findMemoriesByTitle` at line 972 returns all matches, but the retirement path does not use it.

In `MemoryRepository.kt` line 1420, `STATUS_INACTIVE` is filtered only inside `searchMemories`, as `timeFilteredMemoriesInScope.filterNot { salienceOf(it).status == STATUS_INACTIVE }`. That is the only consumer of the status. Retirement therefore has exactly one visible effect, and no other code path observes or audits it.

There is no audit or provenance storage for retirement, no reactivation operation, and no consent or pending-decision model. A retirement is an unlogged, irreversible, unverified side effect of a model-generated link.

## Bounded scope

Authorized scope, restated precisely:

- A governance decision must be made before any retirement, not after
- Target identity must be stable and unambiguous before a retirement is considered
- Link direction and recency must be checked
- The correction must be genuinely applicable to the target
- Active explicit user constraints must be preserved
- Ambiguous, hallucinated or reversed links must not deactivate anything
- Model output cannot grant permission and cannot certify its own authority
- A timestamp alone does not prove semantic contradiction
- Insufficient evidence means leave the memory active and record a pending decision
- Retirement must be reversible, with audit provenance stored in existing storage, plus an explicit restoration operation
- No automatic fallback and no automatic restoration

Explicitly out of scope for this job:

- No ObjectBox schema change, no new entity, no new column, no `default.json` regeneration
- No UID change and no `app/objectbox-models/default.json` edit
- No `entity12` or `MemoryLedger` change
- No D05 and no G02
- No D06 renderer work
- No `MCPToolParameter` or `MCPToolExecutor` change
- No `ReflectorCurator` cancellation work
- No H01
- No promotion logic
- No graph or schema expansion
- No vector caching
- No UI redesign
- No signing or orchestrator change
- No wholesale `MemoryRepository` rewrite
- No unrelated retrieval tuning

## Design

### The policy object

Add a new pure, Context-free policy object `MemoryRetirementGovernance` in package `com.ai.assistance.operit.data.repository`, the same package as `MemoryGraphScopeFilter` and `PendingReviewBatchPolicy`.

It follows that exact pattern: a Kotlin `object`, no Android imports, no ObjectBox imports, no coroutines, and a KDoc block that explains why it exists. The reason is the same as for `PendingReviewBatchPolicy`: the repositories need a real ObjectBox store and cannot be instantiated in `app/src/test`, because this project has no Robolectric. Production delegates to the pure policy. The October 23 repair adds native ObjectBox JVM persistence tests through MemoryMetadataStore without Android service initialization.

The object takes plain data inputs and returns a sealed decision. It never reads storage and never writes storage.

Inputs:

- Candidate target identity: `id`, `title`, `createdAtMs`, `status`
- Source identity: `id`, `title`, `createdAtMs`
- Link type, as a string, matching the existing `CORRECTS` and `SUPERSEDES` values
- Whether the target is protected by an active explicit user constraint
- Evidence flags describing what the caller actually observed

Output is a sealed class with three cases:

- `Retire` — the retirement is justified and may proceed
- `LeaveActive(reason)` — the retirement must not happen
- `PendingDecision(reason)` — the evidence is insufficient; leave active and record the decision

The reasons are an enum, so they are stable, testable and storable as a property value:

- `AMBIGUOUS_TARGET`
- `REVERSED_DIRECTION`
- `TARGET_NEWER_THAN_SOURCE`
- `NO_APPLICABLE_CORRECTION`
- `PROTECTED_USER_CONSTRAINT`
- `INSUFFICIENT_EVIDENCE`
- `SELF_LINK`
- `ACCEPTED`

`ACCEPTED` is the only reason that pairs with `Retire`. Every other reason maps to `LeaveActive` or `PendingDecision`, and the mapping is fixed in the object so a caller cannot reinterpret it.

In the implemented wiring, `correctionApplicable` is set to `false` unconditionally. The data model contains no corroborating evidence that a correction genuinely applies: the link type is model-asserted, a timestamp only orders writes, and the link description is guaranteed non-blank by the parser so it cannot serve as evidence. Production therefore never auto-retires. Only PendingDecision outcomes (AMBIGUOUS_TARGET or INSUFFICIENT_EVIDENCE) persist a pending marker. Earlier LeaveActive outcomes only log; caller-excluded self-links never reach the policy. The policy's `Retire` path is exercised by unit tests and activates only once a real evidence source exists.

### Reversible retirement

Add `MemoryRepository` methods that record prior state and audit provenance as `MemoryProperty` rows. `MemoryProperty` is an existing entity with `key` and `value` string fields, so this is key-value storage in existing storage and requires no schema change.

New property keys, all additive:

- `retired_prev_status` — the status value that was overwritten
- `retired_at` — the retirement timestamp in milliseconds
- `retired_reason` — the `MemoryRetirementGovernance` reason that authorized it
- `retired_by_link_type` — the link type that triggered it
- `retired_source_id` — the id of the source memory

The audited retirement method reads the current status first, writes it to `retired_prev_status`, writes the remaining audit keys, and only then writes `status=INACTIVE`. Ordinary status writes, including same-status markMemoryInactive calls, atomically invalidate retired_prev_status while retaining audit history. Retirement and restore manage their own marker lifecycle.

Add an explicit `restoreMemory(memory)` operation. It validates the marker, INACTIVE status and all required audit fields, restores the recorded status, and clears only the marker and pending decision. Audit history is retained and restored_at is stamped. If `retired_prev_status` is absent the operation does nothing and reports that there is nothing to restore, rather than guessing a status.

There is no automatic restoration anywhere. MemoryRepository.restoreMemory has no production caller. Native tests invoke its delegated MemoryMetadataStore.restore path; pure tests invoke evaluateRestore. Nothing in the retrieval path, the review path or the scheduler calls it.

### Wiring the gate into the call site

The gate is wired into `MemoryLibrary.kt` around lines 543-560, replacing the direct `markMemoryInactive` calls at lines 549 and 559.

The sequence before any retirement:

1. Resolve the target identity unambiguously. Use `findMemoriesByTitle` and require exactly one match. If there is more than one match the target is ambiguous, so do not retire and record a pending decision. This replaces the `findMemoryByTitle` first-row behaviour at line 538 for the retirement decision only; link creation itself is unchanged.
2. Check direction and recency from the source and target `createdAt` values and the link type.
3. Check whether the target is protected by an active explicit user constraint.
4. Call `MemoryRetirementGovernance` with the collected inputs.

Only a `Retire` decision calls the new audited retirement method. Neither LeaveActive nor PendingDecision deactivates a fact. Only PendingDecision persists pending_retirement_reason; LeaveActive logs without writing the marker. There is no UI for pending decisions.

Three invariants are stated explicitly and must hold in the implementation:

- Model output cannot grant permission. The policy reads only stored facts and caller-observed evidence flags; no field of the model's link output is treated as authorization.
- The policy only ever reduces the set of retirements, never expands it. Every path that previously retired still passes through the policy, and the policy can only turn a retirement into a non-retirement.
- There is no heuristic fallback that silently retires. If the policy cannot decide, the outcome is `PendingDecision`, not a default retirement.

## Acceptance checklist

Policy-level items are covered by pure JVM tests in `MemoryRetirementGovernanceTest.kt` (no ObjectBox store). The October 23 follow-up adds ten native ObjectBox tests in MemoryMetadataStoreTest. Hosted execution is tracked in [the repair record](../d02-restore-eligibility-20261023/index.md); full retrieval and historical-device preservation remain untested.

- [x] A reversed link does not retire its target (pure test `reversedLink_doesNotRetire`)
- [x] A same-title ambiguity does not retire any candidate (pure test `ambiguousTarget_doesNotRetire`)
- [x] A protected user constraint or user correction is preserved (pure test `protectedUserConstraint_doesNotRetire`; note: production supplies `targetProtectedByActiveUserConstraint = false` because no constraint model exists)
- [x] Missing evidence returns PendingDecision (pure test missingEvidence_returnsPendingDecision). MemoryLibrary persists that outcome; the pure test is not itself persistence evidence.
- [x] An accepted, justified retirement still works at the policy level (pure test `justifiedCorrection_retires`; not reachable end-to-end in production, see incomplete criteria)
- [ ] The previous status is auditable after a retirement — the code writes `retired_prev_status`/`retired_at`/`retired_reason`/`retired_by_link_type`/`retired_source_id` inside one transaction, native persistence tests are now added, with hosted execution tracked in the current repair record.
- [ ] Explicit restoration returns the memory to its prior status — pure policy and native persistence tests are now added, with hosted execution tracked in the current repair record.
- [ ] Retrieval after restoration returns the fact again — not tested; no retrieval test exists.
- [x] Production wiring is inspected, not only the pure policy tests (gate wiring in `MemoryLibrary` reviewed; `correctionApplicable` and `targetProtectedByActiveUserConstraint` are hardcoded `false` by design)
- [x] No ObjectBox schema change and `default.json` is byte-identical (verified against main `5b3277cf`)
- [x] No unrelated files are touched

## Known incomplete criteria and product decisions needed

Three parts of the authorized scope cannot be fully satisfied within this bounded scope. They are recorded here rather than quietly claimed as done.

### There is no explicit user constraint model on memory facts

The only constraint concept in this codebase lives on `TaskEpisode` and `SkillRevision`. `TaskEpisodeModels.kt` line 29 declares `applicableConstraints`, and `SkillRevisionEntity.kt` line 39 declares `userConstraintsJoined`. Neither is a memory fact, and neither is reachable from a `Memory` row.

A `Memory` has no constraint flag, no authority marker and no provenance field. Adding one would be a schema change, which is out of scope here.

So "preservation of active explicit user constraints" can only be honored for constraints that are representable in existing storage. In practice that means a memory the user explicitly marked, or a correction the user made, expressed through existing `MemoryProperty` keys. The policy input `protectedByActiveUserConstraint` is therefore supplied by the caller from whatever existing signal it can find, and when no such signal exists the input is false. This is a real gap: a user constraint that is not representable in existing storage cannot be protected by this gate. Closing it needs a product decision about where user authority over a memory fact is recorded, and that decision implies a schema change.

### There is no UI surface for pending decisions

The pending marker is recorded in storage as a `MemoryProperty` row on the target. Nothing renders it. There is no screen, no badge and no review queue entry for a pending retirement decision.

Surfacing pending decisions in the UI is out of scope for this job. The consequence is honest and limited: a pending decision is durable and inspectable in storage, but a user will not see it without a future UI change. This job does not claim that pending decisions are visible to the user.

### Direction and recency are weak evidence

Direction and recency can only be inferred from `createdAt` timestamps and the link type. Both are weak.

A timestamp proves only that one row was created before another. It does not prove that the later fact semantically contradicts the earlier one, and it does not prove that the model read the direction correctly. A model can emit a `CORRECTS` link in the reversed direction, and the timestamps will then look consistent with a genuine correction.

The design therefore treats a timestamp alone as insufficient. `TARGET_NEWER_THAN_SOURCE` is a reason to leave the memory active, but the absence of that condition is not proof of a genuine correction. Genuine-correction detection is limited to what the link type plus explicit evidence flags provide. When those are absent the outcome is `PendingDecision`, not `Retire`. This job does not claim semantic contradiction detection.

### Production retirement is currently always deferred

Because `correctionApplicable` is `false` in production, no `CORRECTS`/`SUPERSEDES` link retires a memory today. Only PendingDecision outcomes persist a marker; LeaveActive outcomes log without writing one.

This is the strongest bounded safe change available. The alternative — treating a model-asserted link type or a timestamp as sufficient evidence — would fabricate authority the data model does not contain, which the authorized scope forbids. The acceptance criterion "an accepted, justified retirement still works" is therefore satisfied at the policy level and by unit tests, but not reachable end-to-end in production until a genuine evidence source is added.

Closing this needs a product decision about what constitutes corroborating evidence for a correction, and that decision likely implies a schema or API change, both of which are out of scope here.

## Verification plan

All builds and tests run only in the GitHub-hosted Android Build and Android Tests workflows. Nothing is built or tested locally, on the VPS, or in PRoot.

The regression test file to be added is `app/src/test/java/com/ai/assistance/operit/data/repository/MemoryRetirementGovernanceTest.kt`. It is plain JUnit4, in the same package as the object under test, uses `// Covers:` comments per test, and matches the style of `MemoryGraphScopeFilterTest.kt`.

Test cases, matching the acceptance checklist:

- `reversedLink_doesNotRetire` — a link whose direction contradicts the timestamps returns `LeaveActive(REVERSED_DIRECTION)`
- `targetNewerThanSource_doesNotRetire` — returns `LeaveActive(TARGET_NEWER_THAN_SOURCE)`
- `ambiguousTarget_doesNotRetire` — more than one title match returns `PendingDecision(AMBIGUOUS_TARGET)`
- `selfLink_doesNotRetire` — returns `LeaveActive(SELF_LINK)`
- `protectedUserConstraint_doesNotRetire` — returns `LeaveActive(PROTECTED_USER_CONSTRAINT)`
- `noApplicableCorrection_doesNotRetire` — returns `LeaveActive(NO_APPLICABLE_CORRECTION)`
- `insufficientEvidence_returnsPendingDecision` — returns `PendingDecision(INSUFFICIENT_EVIDENCE)`
- `justifiedCorrection_retires` — returns `Retire(ACCEPTED)`
- `onlyAcceptedReason_retires` — every other reason maps to a non-retire decision
- `policyNeverExpandsRetirementSet` — no input combination produces `Retire` without `ACCEPTED`

Native tests now use the same MemoryMetadataStore as the repository, with a generated MyObjectBox store in a temporary directory. They exercise status/audit persistence, reopen and rollback. Android service initialization and full retrieval remain outside this seam.

## Rollback and reversibility

The change is additive. It adds one new object, new repository methods, new property keys and a gated call site. It removes nothing and renames nothing.

Reverting the commit restores the prior behaviour exactly: the call site returns to the direct `markMemoryInactive` calls at `MemoryLibrary.kt` lines 549 and 559, and the new object and methods become unreferenced.

There is no data migration. The new property keys are written only by the new audited retirement method, so a revert leaves any already-written audit rows in place as inert key-value data. They are not read by any code path after a revert, and they do not affect retrieval, because `searchMemories` at `MemoryRepository.kt` line 1420 filters only on `status`.

No schema change means no default.json regeneration; it does not prove absence of upgrade risk. A01 device verification remains PENDING and is unaffected by this job.

## Implementation record

Branch `fix/memory-retirement-governance`, based on `838deba5c3f7ea86c71265046a09a039e86a0d94`.

Commits:

- `6e266045` plan document
- `ac940edb` pure `MemoryRetirementGovernance` policy and JVM regressions
- `c6c94346` audited reversible retirement and explicit restore in `MemoryRepository`
- `1be9f718` governance gate wired into `MemoryLibrary`
- `ea048df8` corrections: already-inactive guard, honest evidence flag, title-ambiguity counting

Files changed: `MemoryRetirementGovernance.kt` (new), `MemoryRetirementGovernanceTest.kt` (new), `MemoryRepository.kt` (additive, 86 lines), `MemoryLibrary.kt` (gate wiring), this document.

No ObjectBox schema change. `app/objectbox-models/default.json` is byte-identical to the base commit.

### CI evidence

Both hosted workflows succeeded on code commit `44a3361681252c464a84f46acc8e5d9725bb65f7`:

- Android Build run [39542091105](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39542091105) — success
- Android Tests run [39542094153](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39542094153) — success

Pull request: [#4](https://github.com/Mavaebrook/HandleIT-Android/pull/4), open and unmerged, awaiting review.


## Integrity repair and merge (2026-10-21)

Current main `5b3277cf` (PR #3 merged) was merged into the branch at `0b997e66` without rewriting history. The only conflict was in `index.md` and was resolved by keeping main's newer A01 `PARTIAL` wording plus the D02 section; no blanket ours/theirs was used.

Commit `5a3832ba` repairs concrete data-integrity defects in the retirement/restore path:

- Retirement uses store.runInTx; restoration uses value-returning store.callInTx, so a partial failure cannot leave audit rows without `status=INACTIVE` (or vice versa).
- Re-retirement is guarded: if `retired_prev_status` is already set, the method returns without overwriting the audit chain.
- New pure `MemoryRetirementGovernance.evaluateRestore` refuses restoration unless the memory is still `INACTIVE` with a complete retirement audit (`NOT_RETIRED`, `NOT_INACTIVE`, `MISSING_PROVENANCE`). This prevents silently reactivating a memory that another operation deliberately changed.
- Restore retains the retirement provenance (`retired_at`, `retired_reason`, `retired_by_link_type`, `retired_source_id`), stamps `restored_at`, and clears only the consumed restorable marker and the pending reason.

Regression coverage: five new pure JVM tests for `evaluateRestore` in `MemoryRetirementGovernanceTest.kt`. Those historical tests were pure decision tests; the October 23 follow-up adds native persistence coverage at the delegated metadata store.

CI on the final head is recorded in PR #4. This is not device acceptance and not a complete security gate.

## October 23 F2/F4 follow-up

Ordinary status writes now invalidate restoration eligibility atomically, including INACTIVE -> INACTIVE. Initialization checks status inside a transaction. Restore requires a positive timestamp/source ID, ACCEPTED reason and supported link type, retaining historical audit. These checks are structural, not semantic correction evidence. Ten real ObjectBox tests exercise the production metadata store; hosted validation and independent review are pending at publication. See [the current repair record](../d02-restore-eligibility-20261023/index.md).
