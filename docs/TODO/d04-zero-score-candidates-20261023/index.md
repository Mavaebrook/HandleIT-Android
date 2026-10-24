---
repository: Mavaebrook/HandleIT-Android
branch: fix/memory-reinforcement-d04
pr: 5
---

# D04 zero-score candidate follow-up

## Plan and scope — 2026-10-23

1. Reconcile PR #5 with main `f12cfe007cbf803fcac2ce21342992ca2995d286`, preserving both parents and all merged runtime/CI changes.
2. Fix Argon F3: graph expansion must not create candidate or score-part entries for a non-positive contribution. Both outgoing and incoming links must use the same mutation gate. Keep independently retrieved candidates, positive accumulation, scope filtering and the inspected-edge diagnostic.
3. Add JVM regressions at the production candidate-mutation seam: zero/negative edges at a zero relevance threshold, zero source/global multipliers, existing candidates, positive accumulation and score-part callbacks. Inspect both repository call sites; these JVM tests do not exercise ObjectBox relationship loading.
4. Reconcile D04 documentation: batching is unimplemented/unmeasured, not blocked by a demonstrated schema requirement; PR #3 is merged.
5. Validate through the hosted PR workflows, record exact commits/runs, and leave PR #5 open for independent integration/merge review.

## Evidence before this change

Main `f12cfe00`: Android Build [39809127467](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39809127467) and Android Tests [39809127516](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39809127516) both succeeded.

PR #5 began at `7a329587b3867b9f3528495a7c1afd74a5ede1d8`. Unconditional map writes after a zero contribution admit an otherwise absent neighbour when the caller selects threshold 0. The default positive threshold excludes that row. No device reproduction is claimed.

## Remaining work

D04b salience query batching remains unimplemented and unmeasured. ObjectBox 5.3.0 supports relation queries via `QueryBuilder.link(RelationInfo)`; a schema change has not been established as necessary. A01 historical phone-data preservation, D02 restore eligibility and D05/G02 policy work remain separate tasks.

## Validation

Implemented: shared positive-contribution gate, both repository call sites, four candidate-mutation regressions, D04 documentation corrections and reconciliation with main. Hosted validation pending for the new branch head. No local Gradle build or test. Pure JVM coverage does not establish ObjectBox or device acceptance.
