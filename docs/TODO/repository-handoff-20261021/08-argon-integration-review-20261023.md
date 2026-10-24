# Argon independent integration review — accepted 2026-10-23

Source: [05 Argon Integration Review 20261022](https://docs.google.com/document/d/1RcrYuCxbdmY3KfYwPKD1T_0xXfJXlvd5FrjanF3NZEI). The review is dated 2026-10-23 01:02 UTC and covers main `f12cfe007cbf803fcac2ce21342992ca2995d286`, PR #5 `210ddf1c5688f39cea6e2668847a188a456c767d`, and handoff `6e413c13`. The original review is preserved in Google Docs; this record reconciles its conclusions and follow-ups against live source.

## Accepted verdicts

- Main: merged #8–#10 verified, with final-main Build 39809127467 and Tests 39809127516 successful.
- PR #5: **independent review complete; ready for merge review** at `210ddf1c`. F3 is resolved in both graph directions. PR Check [39879669404](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39879669404) is successful; Fast, JVM and Candidate checks passed, while the APK lane was skipped. D04a/D04c are complete for this bounded scope; D04 remains partial because batching is unimplemented/unmeasured.
- PR #4: conservative prevention is eligible for bounded merge review. F2 still blocks restoration activation; repair F2 and its F4 documentation next, preserving `correctionApplicable = false`.
- PR #6: still blocked by unwired trusted production ProjectIdentity. D05 is partial; G02 remains unenforced.
- PR #7: keep the active handoff current. Review recommendations do not authorize merging #4–#7.

Live GitHub still reports main `f12cfe00` and open, mergeable PR #5 at `210ddf1c`. This documentation update does not alter application code or merge a PR.

## O1 — scheduler cancellation and replay (separate follow-up)

Confirmed at main `f12cfe00` in `ReviewScheduler.kt`:

- Lines 211–213 mark the batch PROCESSING and initialize `succeededIds`.
- Lines 241–242 save/index the episode, then record the succeeded review ID.
- Line 256 deletes succeeded reviews only after the entire loop.
- Cancellation propagates out of the loop. Earlier completed reviews remain PROCESSING and may be retried after they become stale and a recovery sweep resets them.

`PendingReviewRepository.kt:123–145` confirms recovery is conditional on staleness, not every immediate restart. The backlog item is pre-existing and is not a blocker for the reviewed PR #5 change.

Repair acceptance must cover two different boundaries:

1. Cancellation during a later review must not replay an earlier completed review. Cleanup of already succeeded IDs in `finally`, or immediate per-success acknowledgement, can address this case.
2. Cancellation after the current episode is saved at `ReviewScheduler.kt:93` but during indexing at line 95 must not duplicate the saved episode on retry. That ID has not yet reached `succeededIds`; the review's proposed final cleanup alone does not solve this window. Establish an explicit persistence/acknowledgement or idempotency contract before choosing the repair.

Preserve cancellation propagation, avoid charging a failed model attempt for cancellation, preserve pending work, and test both interruption points plus restart/retry. Do not claim exactly-once or crash-safe persistence from a batch-finally change alone. No schema change is authorized by this backlog entry.

## O2 — remote MCP cancellation state and transport coverage (separate follow-up)

Confirmed at main `f12cfe00`, `RemoteMcpRuntimeSession.kt:109–125`: `callTool` passes the argument map to the MCP SDK; its general `catch (e: Exception)` sets `connected = false`, logs an error, and rethrows, including for coroutine cancellation.

Cancellation therefore changes the local connected flag; this is not proof the underlying transport actually disconnected. A future repair should distinguish cancellation from a real transport failure and test the intended state transitions.

Nested map/list/null serialization through the remote SDK is a **coverage gap**, not a demonstrated data-conversion defect. The bridge conversion tests do not exercise the remote SDK. Inspect the SDK adapter and add transport-level coverage before asserting a serialization bug. Preserve declared strings and nested null positions.

## Review qualifications

- At `210ddf1c`, the four new test methods begin at `MemoryPropagationScoringTest.kt:84,101,113,124`; the review's 209–260 range is a citation error. `addContribution` starts at `MemoryPropagationScoring.kt:18`, with the positive gate at line 24. This does not change the accepted F3 verdict.
- The guard rejects a **computed contribution** that is non-positive or NaN. The review's generalized claim that either negative source/global multiplier always implies rejection is too broad: two negative factors can produce a positive product. Production graph expansion is already gated on a positive effective multiplier; no new production defect is established by that arithmetic qualification.
- The source audit's status-writer inventory is scoped to the inspected repository class/base. Recheck all relevant status-writing paths on the actual PR #4 repair branch before declaring the F2 fix complete.
- Device preservation, native ObjectBox transaction behavior, D04 batching, D06's SYSTEM-message trust limitation, branch protection and G02 remain outside the completed review's acceptance.

## Next action

PR #5 has finished independent review and awaits a merge decision. Continue the already authorized PR #4 F2/F4 repair without depending on a #5 merge; reconcile with whichever main is current at implementation time, and validate the combined tree. PR #6's trusted-identity design follows. O1/O2 remain separate backlog items, not additions silently folded into these PRs.
