# Argon detailed checks, coverage gaps and source supplement

> Historical pre-integration record. PRs #8–#10 subsequently merged with authorization reported by the user. Read [the integration update](07-integration-20261022.md) for current main and CI. Unresolved findings and coverage limits below remain open; old merge-order/open-PR instructions are superseded.

The user supplied Argon's detailed Priority 1/2 verification after the F1–F4 excerpt. This record preserves the additional review scope and the requested unchanged context. It supplements [the findings and repair plan](05-argon-review-triage.md); it does not supersede those defects or declare all integration review complete.

## Additional review evidence

- **PR #8:** Argon traced main-targeted triggering and local reusable workflow calls, absent secret inheritance, PR signing/artifact guards, instrumentation compilation input, and the failed/successful hosted gate examples. Top-level permissions/concurrency and unchanged aggregate logic were listed for further context review.
- **PR #9:** Argon traced explicit scalar-type extraction, unchanged declared strings and unknown/union schemas, structured argument preservation, nested literals/null positions, valid boolean conversion, removal of malformed-array repair and conversion-value logs, and seven production-boundary regression cases. Full dispatch and transport context remained requested.
- **PR #10:** Argon traced both production role overloads, cancellation rethrow before logging/null conversion, service acquisition inside the protected callback, ordinary failure handling, and ten parameterized cases. Actual scheduler caller behavior remained requested.
- **PR #4/#5:** Argon traced correctionApplicable=false, restoration's callInTx, novelty normalization, previous-content capture before in-place mutation, and removal of the propagation floor. The reviewed source hunks do not overlap. This reduces textual collision risk but does not establish combined runtime behavior or eliminate shared-documentation conflicts.

The submitted detail reports no additional code defect beyond F1–F4. It is not an unconditional merge verdict. PR #6 remains blocked by F1; #4 restoration remains blocked for activation; #5's zero-threshold candidate issue remains open. No code was changed, tests rerun, or PR merged for this source export.

## Nonblocking coverage gaps, distinct from demonstrated defects

1. Required Candidate checks and up-to-date branch administration on main — configured on 2026-10-24 (`Candidate checks` required, strict, include administrators).
2. Live remote MCP transport and full JSON Schema evaluation, including $ref, oneOf and anyOf.
3. Android service initialization and ObjectBox-persisted scheduler retry counters.
4. ObjectBox retirement/restoration persistence and rollback behavior. Compilation checks do not establish runtime transaction correctness.
5. D04 salience query batching and measured query counts.
6. G02 project/invocation context and execution routes below the process-wide handler.
7. A01 upgrade preservation on a populated historical device baseline.

These are the limitations reported by the reviewer, not newly completed acceptance criteria. F2's concrete restoration defect and F1's production identity defect remain defects, even though related broader coverage is also missing.

## Complete source supplied to Drive

The [existing Drive review document](https://docs.google.com/document/d/1ZwP8pvLp74JIkQ8bC7OqT_ASH_VlC_8ju0ocZRkrJtI) now has an **Argon — coverage and source map** tab plus five **Base 5b3277cf** tabs: CI workflows, MCP transports, ReviewScheduler, MemoryRepository and MemoryLibrary.

All nine requested files were fetched in full at exact base `5b3277cf844c0c198307c60ac0e9c8f79f1b88bf`. Each exported file carries its path, commit, Git blob ID, source URL and original line numbers. The display prefixes are not part of the source. These are base snapshots, not PR heads or an integrated tree: map old-side diff hunks to these lines and new-side hunks to the PR head. This avoids assuming a head's line numbers still apply to the base.

| Base source | Git blob |
| --- | --- |
| [.github/workflows/pr-check.yml](https://github.com/Mavaebrook/HandleIT-Android/blob/5b3277cf844c0c198307c60ac0e9c8f79f1b88bf/.github/workflows/pr-check.yml) | `17e0e59ef1636c3a2fbc4c0e67dbf04ebeaba4b8` |
| [.github/workflows/android-build.yml](https://github.com/Mavaebrook/HandleIT-Android/blob/5b3277cf844c0c198307c60ac0e9c8f79f1b88bf/.github/workflows/android-build.yml) | `9a024a62d74656a5e9ec993a3a462f57ae59227f` |
| [.github/workflows/android-tests.yml](https://github.com/Mavaebrook/HandleIT-Android/blob/5b3277cf844c0c198307c60ac0e9c8f79f1b88bf/.github/workflows/android-tests.yml) | `1b867256eb7190cd6d6a24ef132b99dcb5035b68` |
| [app/src/main/java/com/ai/assistance/operit/core/tools/mcp/MCPToolExecutor.kt](https://github.com/Mavaebrook/HandleIT-Android/blob/5b3277cf844c0c198307c60ac0e9c8f79f1b88bf/app/src/main/java/com/ai/assistance/operit/core/tools/mcp/MCPToolExecutor.kt) | `404d9bcc243a67e557288c7948fceb81ede179b9` |
| [app/src/main/java/com/ai/assistance/operit/data/mcp/plugins/BridgeMcpRuntimeSession.kt](https://github.com/Mavaebrook/HandleIT-Android/blob/5b3277cf844c0c198307c60ac0e9c8f79f1b88bf/app/src/main/java/com/ai/assistance/operit/data/mcp/plugins/BridgeMcpRuntimeSession.kt) | `e1cca237ee3706d4f4938f85105760be8be6b11c` |
| [app/src/main/java/com/ai/assistance/operit/data/mcp/plugins/RemoteMcpRuntimeSession.kt](https://github.com/Mavaebrook/HandleIT-Android/blob/5b3277cf844c0c198307c60ac0e9c8f79f1b88bf/app/src/main/java/com/ai/assistance/operit/data/mcp/plugins/RemoteMcpRuntimeSession.kt) | `411c593d88ba51c9d9c3f63f4f0ffeabc9ae77d4` |
| [app/src/main/java/com/ai/assistance/operit/api/chat/library/ReviewScheduler.kt](https://github.com/Mavaebrook/HandleIT-Android/blob/5b3277cf844c0c198307c60ac0e9c8f79f1b88bf/app/src/main/java/com/ai/assistance/operit/api/chat/library/ReviewScheduler.kt) | `38e0684bac1a6509767cba69c40b17ceb67cda08` |
| [app/src/main/java/com/ai/assistance/operit/data/repository/MemoryRepository.kt](https://github.com/Mavaebrook/HandleIT-Android/blob/5b3277cf844c0c198307c60ac0e9c8f79f1b88bf/app/src/main/java/com/ai/assistance/operit/data/repository/MemoryRepository.kt) | `176cca0f489865c9922b839eef5feab953ce3d98` |
| [app/src/main/java/com/ai/assistance/operit/api/chat/library/MemoryLibrary.kt](https://github.com/Mavaebrook/HandleIT-Android/blob/5b3277cf844c0c198307c60ac0e9c8f79f1b88bf/app/src/main/java/com/ai/assistance/operit/api/chat/library/MemoryLibrary.kt) | `0c553ef6486046e22e0fbf9dfd3593e8744fcd64` |

## Remaining review instructions

Use the supplied files with the pinned PR diffs to finish the unchanged-context checks. Review scheduler cancellation/null handling, MCP conversion call sites and both serializers, CI permissions/concurrency/checkout/gate logic, and memory property writes, inactive filtering, ranking and mutation behavior.

For the graph issue, the primary assistant already inspected the PR #5 head beyond the diff: threshold 0.0 admits zero-only candidates, whereas the default 0.025 excludes them. Preserve that source-derived result and its limitations from F3; do not revert it to an unresolved downstream-filter question.

Report any new finding with exact commit, path/line, trigger and consequence. If no new blocker appears, say so while retaining F1–F4 and the coverage limits. The excerpt alone is not grounds to rerun existing green tests. A repaired code head needs appropriate hosted validation. All merges remain unauthorized by this handoff.
