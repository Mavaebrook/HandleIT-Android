---
repository: https://github.com/Mavaebrook/HandleIT-Android
created: 2026-10-17
status: planned
---
# Learning pipeline hardening

## Plain-English reason
The learning pipeline that was built in `docs/TODO/reliable-learning-architect-20261016/` is mostly present as code but is not actually running: the review queue is never filled, the scheduler drains the wrong memory space, the Curator and the skill-revision lifecycle have no production callers, and retrieval ignores the vector index and the graph. This checklist records an independent static review of that pipeline as an ordered, checkable TODO so later jobs can fix it in sequence. Every finding below was re-verified against the code at commit `28b53abd` before being recorded; findings that were already fixed or wrong at HEAD are marked as such in [01-findings.md](01-findings.md) instead of being copied forward.

## Progress rules
Read this file on task start, resume, and after context compaction. Keep a current checkpoint with owner/job, exact commit, next action, and blockers. Update after each meaningful verified milestone, not only at the end. Check an item only with commit and verification evidence. Distinguish implemented, CI-verified, and device-verified. Pending or blocked items remain unchecked. Never mark the entire job complete merely because a build passes. This job is documentation only: it records findings and a plan, it does not implement them.

## Ordered implementation checklist
- [ ] A01 Commit the ObjectBox model so `TaskEpisodeEntity`, `SkillRevisionEntity` and `PendingReviewEntity` have stable ids and UIDs, and verify update-over-existing-install. (R01) — model committed and CI-verified at commit `9afd9627` (ids 12/13/14, purely additive). Device update-over-existing-install verification PENDING; checkbox stays unchecked until that check has actually run.
- [x] A02 Derive `ReviewSignals` from deterministic outcomes instead of hardcoded `false`, so reviews are actually enqueued. (R02) — implemented and CI-verified at commit `cddaa1e0`; tests `ReviewSignalDeriverTest` (13 tests: one per signal mapping, `turnWithNoSignals_enqueuesNothing`, `correctionTurn_enqueuesExactlyOneReview`).
- [x] A03 Drain reviews from the active memory space instead of the hardcoded `"default"` profile. (R03) — implemented and CI-verified at commit `27a6cef1`; tests `ReviewSchedulerMemorySpaceTest` (5 tests incl. `reviewEnqueuedInNonDefaultSpace_isDrainedInThatSpace`).
- [ ] B01 Make `drainPendingReviews` delete only the reviews that succeeded, cap retry attempts, and recover rows stuck in `PROCESSING` after a crash. (R04)
- [ ] B02 Stop hardcoding `useEnglish = true` in the scheduler; follow the user's locale. (R04)
- [ ] C01 Pass a real task description as `currentTaskContext` instead of `workspacePath`, and call `missingPrerequisites` in production. (R05)
- [ ] C02 Replace single-token prerequisite matching with a stopword-aware, threshold-based match. (R05)
- [ ] C03 Tokenize non-Latin text correctly and use the vector index plus the `MemoryLink` graph for episode retrieval. (R06)
- [ ] C04 Extend `TaskEpisode` with `taskSignature`, `omittedFromRequest`, `applicabilityConditions`, `trajectoryRef`, `warningsHelpful`, `warningsDismissed`. (N01)
- [ ] C05 Extend the Reflector prompt to emit `omitted_from_request` and `applicability_conditions`. (N01)
- [ ] C06 Add the plan-time gap check: retrieve 3-5 episodes, test conditions, emit at most 3 cautions, record accept/dismiss, retire repeatedly dismissed lessons. (N01)
- [ ] G01 Log which episodes and skills were injected for a task, then update helpful and harmful counters from the deterministic outcome (not model self-judgement); counters inform promotion and retirement but are one input, not the whole decision. (G01) — placed after Stage C and before Stage F, because F depends on it.
- [ ] D01 Wire `ReflectorCurator.curate`, `SkillRevisionLifecycle` and `SkillRevisionRepository` into production, or record them as library-only. (R07)
- [ ] D02 Add a governance gate before retiring a `CORRECTS`/`SUPERSEDES` target: verify direction and recency, check active user constraints, make retirement reversible. (R08)
- [ ] D03 Raise `MIN_INDEPENDENT_EVIDENCE`, require helpful outcomes, collapse paraphrased evidence, and check candidate steps rather than self-reported constraints. (R09)
- [ ] D04 Fix memory reinforcement on raw text inequality, the per-memory salience N+1 query, and the unconditional `basePropagationScore`. (R10)
- [ ] D05 Scope `ArchitectRulesHook` per project, or document the prompt-only enforcement gap explicitly. (R11)
- [ ] G02 Enforce hard project constraints (never run Gradle or builds on VPS, local machine or PRoot; do not modify protected paths) at tool-dispatch time through the existing checkToolInterception hook, and guard the direct shell-execution paths or record them as an accepted gap. Do not claim a hard gate until every path is covered. (G02) — placed with Stage D, alongside D05.
- [ ] D06 Add provenance, sanitization and a length cap to episodes injected into system prompts. (R12)
- [ ] D07 Replace newline-joined list fields with a lossless encoding. (R13)
- [ ] E01 Add an append-only JSONL trajectory log with schema version, model id, prompt/skill versions, retrieved ids, tool calls, outcome label and correction pairs. (N02)
- [ ] E02 Treat raw trajectories as the primary retrieval source and distilled skills as a supplement; gate injection to hard or pattern-matching tasks. (N03)
- [ ] F01 Build replay fixtures from real tasks and compare baseline / with-lesson / without-lesson before promoting a lesson. (N04)
- [ ] F02 Tune retrieval weights and thresholds against the replays with automatic revert on regression. (N04)
- [ ] R14 Separate the raw observed event from its interpreted meaning in ReviewSignals; rename or re-document the complexity signal so it only claims what it measures; add an explicit user-initiated correction signal and treat the lexical marker match as a weaker candidate. (R14) — Stage A refinement.
- [ ] R15 Record the release that first shipped entities 12-14 and keep default.json lineage from that release onward; add a CI step that fails on a non-byte-identical regeneration; A01 stays unchecked until the update-over-existing-install check runs. (R15) — Stage A refinement.
- [ ] R16 Keep review TRIGGER, lesson CONFIDENCE and PROMOTION EVIDENCE separate in the schema and in every later stage. (R16) — design rule, referenced from Stages C, D and F.

## Scope boundaries
Documentation only. This job records findings and an ordered plan; it does not change source code, does not run builds or tests, and does not modify `entity12`/`MemoryLedger` or any ObjectBox id. The findings are recorded in [01-findings.md](01-findings.md) and the staged order in [02-implementation.md](02-implementation.md). Full GEPA prompt search, automatic executable-skill synthesis, wholesale memory-backend replacement, new model subscriptions, credential rotation, history rewriting and signing-key replacement remain deferred. Do not silently expand scope. The deferred backlog recorded in [01-findings.md](01-findings.md) (hypergraph subtask and skill links, multi-attempt sampling with a verifier, GEPA-style prompt optimization, validity-window facts, retrieval keyed on the current step or error, duplicate consolidation and pruning, Mem0-style add/update/delete/no-op decisions) is explicitly not scheduled and waits until the basic loop works and is measurable.

## Checkpoint
Owner: VPS orchestrator via the bridge, at Teddy's explicit request.
State: Stage A implemented and CI-verified. A01 model committed (`9afd9627`), A02 signals derived (`cddaa1e0`), A03 multi-space drain (`27a6cef1`), A03 per-space error isolation and Stage A docs (`58bedd67`). Final CI on `58bedd67`: Android Build success (https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39051121196), Android Tests success (https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39051121190). A01 device update-over-existing-install verification is PENDING and its checkbox remains unchecked.
Docs amendment (2026-10-17): an independent review of Stage A raised refinements R14-R16, promoted G01 and G02 into the checklist, amended E02/N03, C06 and F02, and recorded a deferred backlog. This amendment is documentation only; no source code, workflow or checkbox was changed.
Exact commit: the docs-only amendment commit that carries this checkpoint (see CHANGELOG.md entry dated 2026-10-17).
Next: start Stage B (B01: make `drainPendingReviews` delete only succeeded reviews, cap retry attempts, recover rows stuck in `PROCESSING`; B02: stop hardcoding `useEnglish = true`).
Blockers: none for Stage B. A01 device verification needs a real device with an existing install and cannot be performed from CI. Amendment (2026-10-17): A01 device verification is pending and requires a device with an older build installed; it cannot be performed from CI. No blocker for Stage B.

## Stage A CI evidence

All runs are GitHub-hosted Actions. No build or test was run on the VPS or any local machine.

| Item | Commit | Android Build | Android Tests |
| --- | --- | --- | --- |
| A01 ObjectBox model | `9afd9627` | superseded by the next push (cancelled); covered by the green run on `58bedd67` | same |
| A02 derived signals | `cddaa1e0` | success (https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39049257681) | success (https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39049257792) |
| A03 multi-space drain | `27a6cef1` | success (https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39049278206) | success (https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39049278262) |
| A03 error isolation + docs | `58bedd67` | success (https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39051121196) | success (https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39051121190) |

The `58bedd67` tree contains A01, A02 and A03 together, so its green Android Build and Android Tests runs verify the whole of Stage A compiles and its JVM tests pass.

ObjectBox model generation: `ObjectBox Model` workflow run 39048652619 (https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39048652619), artifact `objectbox-model-1`, generated from commit `043ee519`.

Not verified: update-over-existing-install on a real device (A01). This cannot be performed from CI and remains PENDING.
