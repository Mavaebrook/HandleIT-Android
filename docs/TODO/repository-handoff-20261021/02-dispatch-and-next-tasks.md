# HandleIT-Android remaining tasks — October 24, 2026

## Integrated work

PR #4 merged as 5526acd0 after its independent review and PR Check 40052350300 passed. PR #5 was reconciled with #4 at 738d345f, passed combined PR Check 40064775483 and merged as f5ddc3fb75e5eab59f785f5bc72f34f479007b5f. PR #7 delivers the consolidated handoff. See [exact integration evidence](10-memory-integration-20261024.md).

PRs #8–#10 were already integrated at f12cfe00; do not restart them. F2 and F3 are repaired. Their completed work does not make all of D02 or D04 complete.

## Next implementation: PR #6 trusted identity

Head at the last review: d1ab20f998d18b036a3e539451f008dce876cfb7. Inspect for newer changes before editing.

F1 blocks merge: production callers do not supply ProjectIdentity, causing HandleIT-specific rules to remain inactive. Define and wire trusted identity through both prompt and summary composition, and add scoped tests. Do not infer trust from model output, directory names or global singleton state.

D05 remains partial and G02 dispatch enforcement is separate unfinished work. Correct remaining overbroad documentation with the implementation. #6 has no merge authorization.

## Remaining memory work

- D02: applicable correction evidence, active-user-constraint provenance, explicit restoration integration, and full retrieval/device acceptance. Automatic retirement stays disabled. Carry forward the low-severity retirement-boundary hardening note, blank-status coverage and direct Android-wrapper coverage from [PR #4 acceptance](09-pr4-restoration-repair-20261024.md).
- D04b: query batching implemented and measured in PR #18 (`fix/d04b-salience-query-batching`): 3 round trips vs the legacy `2*S + R`; relation-query assessment documented; no schema change required.
- A01: historical phone-local entity preservation still needs a compatible populated baseline. Native JVM database tests do not establish upgrade preservation on a historical phone.

## Separate follow-ups

The [Argon integration review acceptance](08-argon-integration-review-20261023.md) records scheduler replay after cancellation and remote MCP cancellation-state handling. Scheduler work must account for cancellation between episode persistence and indexing, not only cleanup of earlier successes. Remote serialization is a coverage gap, not a proven defect.

D06 fencing still leaves retrieved memory in SYSTEM. G02 tool enforcement requires invocation context and coverage of raw-exec routes. Branch protection was configured on 2026-10-24 (`Candidate checks` required, strict, include administrators). Interface redesign and orchestrator model-selection work remain deferred.

## Execution and communication

The user authorized merges #4/#5/#7 once checks pass. Preserve that authorization across model changes; do not ask again for those merges. Do not extend it to #6.

Read targeted sources, avoid repeated CI polling, and report exact-head evidence. Builds/tests run only through the hosted Android workflows. Completed bridge jobs #44–#49 and accepted independent reviews should not be repeated without new changes or a concrete concern.
