# Argon findings: verified triage and repair plan

> Historical pre-integration record. PRs #8–#10 subsequently merged with authorization reported by the user. Read [the integration update](07-integration-20261022.md) for current main and CI. Unresolved findings and coverage limits below remain open; old merge-order/open-PR instructions are superseded.

Verified 2026-10-22 at 15:17 UTC. This record incorporates the concrete F1–F4 findings the user pasted from Argon, plus bounded source verification by the primary assistant. The shared Gemini response could not be retrieved; this is not a reconstruction of the full review or a new exhaustive review of every PR.

No implementation was changed, tests rerun, or PR merged during this triage. Findings below remain open unless explicitly marked otherwise.

## Reviewed revisions

| PR | Head | Disposition from these findings |
| --- | --- | --- |
| #4 | `1323cb2ecd130e99c10ef3254a5a5eb4dc345837` | Conservative prevention remains eligible for merge review; restoration activation blocked by F2 |
| #5 | `7a329587b3867b9f3528495a7c1afd74a5ede1d8` | F3 confirmed for a zero relevance threshold; bounded repair recommended before integration |
| #6 | `d1ab20f998d18b036a3e539451f008dce876cfb7` | **Blocked for merge by F1** |
| #8 | `b1719809182e146d94210302b155c6e8b7f0c899` | No new code blocker reported in this excerpt; F4 documentation correction open |
| #9 | `ae43e58292027c8acc0238f545ab600392acdddc` | No new code blocker reported in this excerpt; F4 documentation correction open |
| #10 | `9597124b7390baa26c8545f6129b2e22de2c930a` | No new code blocker reported in this excerpt; F4 documentation correction open |

The bases still point to main `5b3277cf844c0c198307c60ac0e9c8f79f1b88bf`. Existing green runs are listed in [the dispatch record](02-dispatch-and-next-tasks.md). These findings grant no merge authorization.

## F1 — high: PR #6 suppresses HandleIT prompt requirements

**Accepted as a merge blocker.** [ProjectIdentity.fromMetadata](https://github.com/Mavaebrook/HandleIT-Android/blob/d1ab20f998d18b036a3e539451f008dce876cfb7/app/src/main/java/com/ai/assistance/operit/core/chat/hooks/ProjectIdentity.kt#L42) produces UNKNOWN unless supplied a typed identity. [ProjectPolicyScope.resolve](https://github.com/Mavaebrook/HandleIT-Android/blob/d1ab20f998d18b036a3e539451f008dce876cfb7/app/src/main/java/com/ai/assistance/operit/core/chat/hooks/ProjectPolicyScope.kt#L33) maps UNKNOWN plus a workspace to GENERIC_DEVELOPMENT, for which appliesHandleItRules is false. Both [architect hooks](https://github.com/Mavaebrook/HandleIT-Android/blob/d1ab20f998d18b036a3e539451f008dce876cfb7/app/src/main/java/com/ai/assistance/operit/core/chat/hooks/ArchitectRulesHook.kt#L79) omit the HandleIT block in that scope.

The inspected production producers do not supply the identity: [EnhancedAIService metadata](https://github.com/Mavaebrook/HandleIT-Android/blob/d1ab20f998d18b036a3e539451f008dce876cfb7/app/src/main/java/com/ai/assistance/operit/api/chat/EnhancedAIService.kt#L748) supplies workspace and invocation fields; [ConversationService summary metadata](https://github.com/Mavaebrook/HandleIT-Android/blob/d1ab20f998d18b036a3e539451f008dce876cfb7/app/src/main/java/com/ai/assistance/operit/api/chat/enhance/ConversationService.kt#L165) supplies provider, count and workspace; [buildActivePromptHookMetadata](https://github.com/Mavaebrook/HandleIT-Android/blob/d1ab20f998d18b036a3e539451f008dce876cfb7/app/src/main/java/com/ai/assistance/operit/core/chat/hooks/ActivePromptHookMetadata.kt#L15) only supplies activePrompt. The identity class itself documents the missing production source.

This removes the in-app pinned prompt guidance even for a real HandleIT workspace. Clarification to the review wording: these prompt rules are not deterministic enforcement of AGENTS.md. G02 is unimplemented, so neither the old nor new prompt path proves a hard execution gate.

Repair acceptance:

1. Establish a trusted application/workspace identity source and pass the same scoped identity to both prompt and summary producers.
2. Verify HandleIT, OTHER, UNKNOWN and no-workspace behavior through the production wiring; also cover workspace switching/concurrent contexts so identity cannot leak between tasks.
3. Do not infer trusted identity from model text, retrieved content, directory basenames, or a process-wide HandleIT default.
4. Keep valid generic preflight for other/unknown projects, preserve D06's current behavior, and correct the changelog's claim that production HandleIT injection already works.
5. Run hosted validation on the repair. D05 remains partial until production wiring is established; do not claim this implements G02.

## F2 — medium: deliberate deactivation does not invalidate restoration

**Accepted as an activation blocker, not a current production prevention regression.** [markMemoryInactive](https://github.com/Mavaebrook/HandleIT-Android/blob/1323cb2ecd130e99c10ef3254a5a5eb4dc345837/app/src/main/java/com/ai/assistance/operit/data/repository/MemoryRepository.kt#L362) writes INACTIVE without clearing the restorable marker. [retireMemoryWithAudit](https://github.com/Mavaebrook/HandleIT-Android/blob/1323cb2ecd130e99c10ef3254a5a5eb4dc345837/app/src/main/java/com/ai/assistance/operit/data/repository/MemoryRepository.kt#L380) leaves prior status and timestamp; [restoreMemory](https://github.com/Mavaebrook/HandleIT-Android/blob/1323cb2ecd130e99c10ef3254a5a5eb4dc345837/app/src/main/java/com/ai/assistance/operit/data/repository/MemoryRepository.kt#L423) asks [evaluateRestore](https://github.com/Mavaebrook/HandleIT-Android/blob/1323cb2ecd130e99c10ef3254a5a5eb4dc345837/app/src/main/java/com/ai/assistance/operit/data/repository/MemoryRetirementGovernance.kt#L167) only about current status, prior status and retirement timestamp. A later intentional INACTIVE write is indistinguishable from the retired state and can be restored to ACTIVE.

The “complete retirement audit” claim also exceeds the actual eligibility inputs: reason, link type and source ID are not inspected. Production [MemoryLibrary](https://github.com/Mavaebrook/HandleIT-Android/blob/1323cb2ecd130e99c10ef3254a5a5eb4dc345837/app/src/main/java/com/ai/assistance/operit/api/chat/library/MemoryLibrary.kt#L577) still uses correctionApplicable=false, and no production restore caller was identified in the review. Do not enable either feature while this remains unresolved.

Repair acceptance:

1. Enumerate every deliberate status writer, not only markMemoryInactive.
2. Atomically invalidate the restorable marker when deliberately changing status, including same-status INACTIVE writes; retain historical provenance.
3. Validate the claimed required provenance in restoration or explicitly narrow the API contract, without inventing missing authority.
4. Cover retire → deliberate INACTIVE → restore refusal, untouched retirement → successful restore, repeated restore refusal, missing provenance, and transaction/persistence behavior.
5. Keep automatic retirement disabled and avoid ObjectBox schema/ID changes.

## F3 — low: zero contribution can still create a result candidate

**Confirmed with a concrete boundary condition.** Argon correctly identified unconditional map insertion in both outgoing/incoming loops at [MemoryRepository](https://github.com/Mavaebrook/HandleIT-Android/blob/7a329587b3867b9f3528495a7c1afd74a5ede1d8/app/src/main/java/com/ai/assistance/operit/data/repository/MemoryRepository.kt#L1586). A zero/negative link weight produces 0.0, but scores and score parts are still populated.

The previously uninspected downstream path resolves the uncertainty:

- [Line 1323](https://github.com/Mavaebrook/HandleIT-Android/blob/7a329587b3867b9f3528495a7c1afd74a5ede1d8/app/src/main/java/com/ai/assistance/operit/data/repository/MemoryRepository.kt#L1323) clamps the supplied relevance threshold to at least 0.0; a zero threshold is valid at the repository API.
- [Line 1642](https://github.com/Mavaebrook/HandleIT-Android/blob/7a329587b3867b9f3528495a7c1afd74a5ede1d8/app/src/main/java/com/ai/assistance/operit/data/repository/MemoryRepository.kt#L1642) retains entries with score >= effectiveRelevanceThreshold.
- [Final selection](https://github.com/Mavaebrook/HandleIT-Android/blob/7a329587b3867b9f3528495a7c1afd74a5ede1d8/app/src/main/java/com/ai/assistance/operit/data/repository/MemoryRepository.kt#L1697) sorts those entries and filters scope, not positive score.
- The normal default threshold is 0.025, so the same zero-only neighbour is excluded with that default.

Therefore an in-scope neighbour with no other contribution can enter returned results at threshold 0.0, assuming finite salience weighting. This is a source-derived scenario, not an executed device or ObjectBox reproduction. Whether a current UI caller selects zero was not established. Positive thresholds still exclude zero-only candidates, while debug candidate counts include them.

Repair acceptance:

1. Skip score-map and score-parts mutation when propagatedScore is not positive in both graph directions.
2. Preserve positive propagation and existing scores from other retrieval signals.
3. Test that zero/negative edges do not create candidates at threshold 0.0, in both directions, and that a positive edge still contributes.
4. Keep the diagnostic meaning of edgesTraversed explicit. Counting an inspected edge is not itself a ranking bug; do not silently change it to “contributing edges” without updating its contract.
5. Validate on hosted CI. This is independent of the deferred D04 batching work.

## F4 — low: conflicting or stale documentation

**Accepted; branch-local cleanup is still open.** Representative claims were verified against source and the existing hosted evidence. This handoff clarifies current status but does not silently repair the other PR branches.

| Branch / affected records | Required correction |
| --- | --- |
| #4 CHANGELOG, learning index and 06 D02 plan | Only PendingDecision outcomes persist a pending reason; LeaveActive paths log. Restore uses callInTx, retains audit history, and has narrower eligibility than claimed. Distinguish pure policy tests from repository/persistence tests. |
| #4 checkpoint 09 | Preserve old CI results as historical and add the successful 1323cb2e Build/Tests evidence; remove misleading current PENDING wording. |
| #5 CHANGELOG, findings and index | Replace the unsupported “schema blocks batching” assertion with unimplemented/unmeasured relation-query work. PR #3 is merged. |
| #6 CHANGELOG and index | Production identity is not wired; HandleIT injection is not active. PR #3 is merged. G02 remains unimplemented. |
| #8 CHANGELOG and main-pr-runtime progress | Record success of run 39707962414 on b1719809; keep the earlier failed run historical. |
| #9 MCP implementation record and CHANGELOG | Record success of run 39708024114 on ae43e582; do not claim remote-network acceptance. |
| #10 cancellation index and CHANGELOG | Record success of run 39708029350 on 9597124b; keep scheduler-persistence/device limitations explicit. |

After a documentation correction, distinguish the new documentation head from the exact tested code SHA. If PR checks rerun automatically, report their actual outcome. Do not relabel an older run as testing a new head.

## Next work and integration order

1. Keep #6 blocked. Resolve the trusted-identity design and production wiring under F1 before a merge recommendation.
2. Complete bounded branch documentation corrections from F4 and the small F3 candidate-insertion repair; keep them distinct from the larger identity work.
3. Repair F2 before any restoration/auto-retirement activation. The current no-auto-retirement mitigation can be reviewed on its bounded merits.
4. Final integration review remains #8 first, then #9/#10, which include #8's prerequisite. Reconcile shared CHANGELOG/TODO edits and validate code changes against the integrated base.
5. Reassess #4/#5 together when either changes MemoryRepository/MemoryLibrary. Keep A01, D04 batching, D05 production identity, D06's SYSTEM trust limitation, and G02 enforcement explicit.
6. Administrator-required Candidate checks and up-to-date-branch enforcement remain separate from functioning workflow YAML. Main protection was configured on 2026-10-24 (`Candidate checks` required, strict, include administrators).

No unconditional merge authorization, new Claude bridge dispatch, schema change, or orchestrator model-selection work follows from this triage.
