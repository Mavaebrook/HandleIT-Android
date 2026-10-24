---
repository: https://github.com/Mavaebrook/HandleIT-Android
created: 2026-10-21
status: partial
branch: fix/memory-retirement-governance
---

# D02 memory-retirement governance — historical checkpoint (2026-10-21)

Superseded by [the October 23 repair record](../d02-restore-eligibility-20261023/index.md). Earlier failures and repairs below are retained as history.

## Job

Repair existing PR #4 (not a new D02 implementation): merge current main, fix concrete retirement/restore data-integrity defects, correct D02 completion accounting, run hosted CI, update the PR body. Priority P0.

## Commits on the branch (newest first)

- `8235b709` fix(learning): use value-returning callInTx in restoreMemory to fix compile
- `45de0a72` docs(learning): add D02 repair checkpoint with tested SHA and pending CI run IDs
- `ed90b28b` docs(learning): correct D02 accounting to PARTIAL and record integrity repair
- `5a3832ba` fix(learning): make D02 retire/restore atomic and guard silent reactivation
- `0b997e66` Merge origin/main (5b3277cf) into fix/memory-retirement-governance
- `4603a6d7` docs(learning): record D02 CI run IDs and PR link (pre-existing)
- (earlier D02 commits `6e266045`..`44a33616` unchanged)

## Merge

Current main `5b3277cf` (PR #3 merged) merged at `0b997e66` without rewriting history. Only conflict: `index.md`; resolved by keeping main's A01 `PARTIAL` wording plus the D02 section. `default.json` byte-identical to main.

## Integrity repair (5a3832ba)

- `retireMemoryWithAudit` and `restoreMemory` wrapped in a single `store.runInTx`.
- Re-retirement guard: returns early if `retired_prev_status` already set.
- New pure `MemoryRetirementGovernance.evaluateRestore` refuses restore unless status is still `INACTIVE` with a complete audit (`NOT_RETIRED`/`NOT_INACTIVE`/`MISSING_PROVENANCE`).
- Restore retains retirement provenance, stamps `restored_at`, clears only the consumed restorable marker and pending reason.
- Five new pure JVM tests for `evaluateRestore` (pure decision tests, not persistence evidence).

## Compiler repair (8235b709)

The integrity repair above did not compile. `restoreMemory` is declared `Boolean`, but in ObjectBox 5.3.0 `BoxStore.runInTx(Runnable)` returns `Unit`, so the `when`-expression result was discarded and `:app:compileDebugKotlin` failed with:

```
MemoryRepository.kt:439:9 Return type mismatch: expected 'Boolean', actual 'Unit'
```

Fix: `restoreMemory` now uses `store.callInTx(Callable<R>)`, the supported value-returning write transaction in ObjectBox 5.3.0, instead of `store.runInTx`.

- Atomicity preserved: eligibility checks and all writes still run in a single write transaction; `callInTx` commits on success and rolls back on exception.
- `Boolean` contract preserved: the `when` result (`true` on restore, `false` on refusal) is now the transaction's return value.
- Retained provenance, consumed-marker handling and conservative no-auto-retirement behavior are unchanged.
- `retireMemoryWithAudit` still uses `runInTx` — correct there, since it returns `Unit`.

## Exact tested SHA and CI

**The previous CI runs on `ed90b28b` FAILED — they are not pending and not green.**

- Android Build run `39580341182` — https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39580341182 — **FAILED** at `:app:compileDebugKotlin` with `MemoryRepository.kt:439:9 Return type mismatch: expected 'Boolean', actual 'Unit'`.
- Android Tests run `39580344481` — https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39580344481 — **FAILED** with the same compile error.

Superseding evidence: head 1323cb2ecd130e99c10ef3254a5a5eb4dc345837 later passed Android Build [39593653711](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39593653711) and Android Tests [39593659455](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39593659455). These runs do not validate the October 23 follow-up.

Historical (pre-merge) green CI on `44a33616`: Build `39542091105`, Tests `39542094153`.

## Limitations (why D02 stays PARTIAL)

- Production `correctionApplicable` hardcoded `false`; no corroborating evidence source.
- No active-user-constraint provenance (`Memory` has no constraint model).
- Restoration has no production UI/caller.
- Persistence/retrieval acceptance (checklist items 6, 7, 8) is incomplete and unchecked.
- Existing tests are pure-Boolean policy tests over `MemoryRetirementGovernance`; they do **not** prove ObjectBox transaction or persistence behavior. At this historical checkpoint the transactional wiring was compile-verified only. Native persistence tests are added in the October 23 follow-up.
- Checking `INACTIVE` plus an audit record is a local precondition, not proof about what other operations may have done; broader authority/provenance semantics are left to independent review.
- Green CI would not be device acceptance and not a complete security gate.

## Next action

1. Resume from the October 23 repair record; do not repeat the completed compiler repair.
2. Independent review is a separate gate (this job does not self-certify merge readiness).
3. Do not merge without explicit authorization.
