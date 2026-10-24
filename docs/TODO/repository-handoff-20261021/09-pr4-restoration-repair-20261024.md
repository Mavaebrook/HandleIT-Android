# PR #4 restoration eligibility repair — October 24, 2026

This is the accepted pre-merge review checkpoint. PR #4 subsequently merged as 5526acd0; [the integration record](10-memory-integration-20261024.md) supersedes its open-PR and no-merge-authorization statements.

## Published state

PR #4 is open at `54b3ade24d335b176e51f3df6390eb8a8cc9df37`. Its parents are previous head `1323cb2ecd130e99c10ef3254a5a5eb4dc345837` and main `f12cfe007cbf803fcac2ce21342992ca2995d286`. The conflict is resolved without rewriting history. PR #5 remains separate and unmerged.

The implementation was prepared October 23, saved through the interrupted workspace session, and published October 24. The dated implementation plan is [on the PR branch](https://github.com/Mavaebrook/HandleIT-Android/blob/54b3ade24d335b176e51f3df6390eb8a8cc9df37/docs/TODO/d02-restore-eligibility-20261023/index.md).

## Behavior and acceptance

Ordinary status writes clear retired_prev_status in the same transaction, including deliberate INACTIVE → INACTIVE writes. Retirement and restoration retain their own transactional marker lifecycle. Missing-status initialization checks and writes in one transaction.

Restore requires INACTIVE, an unconsumed prior-status marker, positive retirement timestamp and source ID, ACCEPTED reason, and CORRECTS/SUPERSEDES link type. It consumes the marker and pending reason, preserves historical audit, and records restored_at. Structural validity is not proof of source existence or semantic correction evidence.

MemoryRepository delegates to the production MemoryMetadataStore. Ten native ObjectBox JVM tests cover database reopen durability, same-status deactivation, valid/repeated/refused restoration, incomplete/invalid audit, rollback after writes, and status initialization. Existing pure governance tests remain.

F4 wording now distinguishes durable PendingDecision from logged LeaveActive, callInTx restoration from runInTx retirement, and retained audit from consumed eligibility.

## Validation checkpoint

[PR Check 40052350300](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/40052350300) completed successfully. GitHub job results were checked once after review delivery: Fast checks, Android JVM tests and Candidate checks passed; the separate APK lane was skipped by the path selector. Merge candidate `5203e94c8d2378c9948350fd9dc411784d033cba` has the same tree `c19524f858dbf3c359e355a63f8acdc1ac021e02` as the review head.

[Argon's independent review](https://docs.google.com/document/d/1AMrLefS85k7mkKTMZSzYqv2CB6Hf8-pmQWOHwi1Skvw) records actual execution of MemoryMetadataStoreTest (10 passed) and MemoryRetirementGovernanceTest (19 passed), with zero failures and zero skipped tests in either class. These class-level counts are attributed to Argon's report; this acceptance pass verified the GitHub job outcomes without repeating the report inspection. Old green runs 39593653711 and 39593659455 apply only to 1323cb2e. Pending text in the implementation's publication snapshot is historical and superseded by this checkpoint.

No local build or test ran. Source comparison against main shows no schema, ObjectBox ID, entity12/MemoryLedger, signing, package, workflow or dependency changes.

## Independent review accepted

Argon's October 24 review covers exact head 54b3ade2 and concludes **ready for merge review**. F2 status-writer invalidation, audit validation, rollback, replay refusal, status initialization and production delegation are accepted within the bounded repair scope. No new production source change is needed to accept this review.

One low-severity, non-blocking hardening note remains: MemoryMetadataStore.retire relies on caller policy validation. A direct call on an already-INACTIVE memory with no marker can record INACTIVE as its prior status. Invalid audit parameters can also be written, although restoration's structural checks refuse them. Before expanding activation/callers, consider validating the current status inside the retirement transaction and defining canonical argument validation. The suggested INACTIVE guard alone does not validate reason/link/source parameters. Current automatic retirement is disabled. Recording an INACTIVE prior status does not itself reactivate the memory.

Some source line references in the report are offset. At 54b3ade2, MemoryMetadataStore.writeProperty starts at line 61, initializeStatusIfAbsent at 74, retire at 88 and restore at 103; MemoryRetirementGovernance.evaluateRestore starts at 171. Use symbols and the exact SHA, not the report's line offsets. These citation corrections do not change the verdict.

## Remaining limits and next actions

- #4 and #5 are merged (`5526acd0` and `f5ddc3fb`). PR #7 reconciles this documentation; no merge authorization is recorded for #6 (F1 trusted identity remains blocked).
- D02 remains partial: correctionApplicable is false, restoration has no production caller, active-user-constraint provenance is absent, and full retrieval/historical phone-data preservation remain unverified.
- Tests exercise the production metadata store directly; Android-dependent MemoryRepository coroutine wrappers are compile-verified, not directly invoked in these tests.
- Blank status versus missing status during reinforcement initialization lacks dedicated coverage. This is a coverage gap, not a demonstrated new defect.
- Next implementation priority is trusted production identity for #6, which remains merge-blocked. G02 enforcement remains unimplemented.
- Do not repeatedly poll CI or repeat completed reviews. Keep exact-head evidence and future activation prerequisites in the handoff.
