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
- [x] B01 Make `drainPendingReviews` delete only the reviews that succeeded, cap retry attempts, and recover rows stuck in `PROCESSING` after a crash. (R04) — implemented and CI-verified at commit `79a17f54`; tests `ReviewSchedulerBatchTest` (9 tests: batchOfThreeWithOneFailure_deletesExactlyTwo, reviewFailingPastMaxAttempts_isDeadLettered, deadLetteredReview_isNotRetried, reviewBelowMaxAttempts_staysRetryable, staleProcessingRow_isRecovered, freshProcessingRow_isLeftAlone, processingRowExactlyAtStalenessBoundary_isNotStale, nonProcessingStatus_isNeverStale, succeededReview_isDeleted).
- [x] B02 Stop hardcoding `useEnglish = true` in the scheduler; follow the user's locale. (R04) — implemented and CI-verified at commit `68931d45`; tests `ReviewLessonLanguageTest` (7 tests: chineseLocale_requestsChinese, chineseRegionVariant_requestsChinese, englishLocale_requestsEnglish, otherSupportedLocale_requestsEnglish, uppercaseChineseCode_isStillChinese, blankCode_requestsEnglish, lessonLanguage_matchesUseEnglish).
- [x] C01 Pass a real task description as `currentTaskContext` instead of `workspacePath`, and call `missingPrerequisites` in production. (R05) — implemented and CI-verified at commit `07cda3ea` (Android Tests success on `55151c83`, run https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39122619106; the `07cda3ea` runs were cancelled by the concurrency group and are covered by that green run). Tests: `TaskPreflightFormatterTest` (6 tests) and 4 `satisfiedPrerequisites` tests in `TaskEpisodeRetrievalTest`.
- [x] C02 Replace single-token prerequisite matching with a stopword-aware, threshold-based match. (R05) — implemented and CI-verified at commit `55151c83` (Android Tests success, run https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39122619106). Tests: 6 added to `TaskEpisodeRetrievalTest` (fresh-install vs in-place-update with deliberately shared tokens, path-like context, all-stopword condition, Chinese update-vs-fresh-install, stopword-only relevance).
- [x] C03 Tokenize non-Latin text correctly and use the vector index plus the `MemoryLink` graph for episode retrieval. (R06) — parts (a), (b) and (c) implemented and CI-verified at commits `a364a032` (a+b) and `b0bd7705` (c); Android Tests success on `b0bd7705`, run https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39123568673. Part (d), graph expansion over `MemoryLink`, is NOT implemented and remains a follow-up: `MemoryLink.source`/`target` are `ToOne<Memory>` and `TaskEpisodeEntity` has no link field, so episode-to-memory and episode-to-episode edges need a new ObjectBox entity or new fields. See the follow-up comment in `TaskEpisodeRepository`.
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
- [x] R14 Separate the raw observed event from its interpreted meaning in ReviewSignals; rename or re-document the complexity signal so it only claims what it measures; add an explicit user-initiated correction signal and treat the lexical marker match as a weaker candidate. (R14) — Stage A refinement. — implemented and CI-verified at commit `72c98edc` (test fix `d3535a5a`); tests `ReviewSignalDeriverTest` (23) and `ReviewTriggerPolicyTest` (10). The explicit-user-action UI remains an UNCHECKED follow-up (see below).
- [x] R15 Record the release that first shipped entities 12-14 and keep default.json lineage from that release onward; add a CI step that fails on a non-byte-identical regeneration; A01 stays unchecked until the update-over-existing-install check runs. (R15) — Stage A refinement. — implemented and CI-verified at commit `2703b216`; ObjectBox Model run 39096814734 succeeded and its byte-identical step printed the success line at runtime. A01 stays unchecked.
- [ ] R14-UI Add an explicit user-initiated "this reply is wrong" control (for example a thumbs-down or mark-wrong action on an assistant message) and wire it to `ReviewSignalDeriver.TurnObservationAccumulator.explicitCorrectionAction`, so the explicit-correction signal is set by a deliberate user action rather than only by lexical markers. (R14 follow-up) — NOT DONE. No such control exists today; `regenerate` is a retry, not a correction marker. The deriver input is implemented and unit-tested but nothing in production sets it.
- [ ] R16 Keep review TRIGGER, lesson CONFIDENCE and PROMOTION EVIDENCE separate in the schema and in every later stage. (R16) — design rule, referenced from Stages C, D and F.
- [ ] H01 Add a dedicated `REVIEW` function type and route the Reflector and Curator through it instead of `MEMORY` and `SUMMARY`; add its branches to the three exhaustive `when` expressions in `FunctionalConfigScreen.kt` and its string resources; give it a meaningful test-connection branch; state in the settings text that the reviewer uses the DEFAULT config until the user picks one and that review text is sent to the selected provider; record the model config id, model name and provider type on every review so the Stage F baseline/same-model/stronger-model comparison can be measured (leave that comparison UNMEASURED until replays exist). Reuse model configs; do not build a separate API url or key store. A reviewer may propose lessons but can never override explicit user constraints. (R17) — placed after Stage B and before Stage D, because Stage D's Curator wiring depends on it.
- [ ] R14b Remove or justify the neutral retry phrases `"try again"` and `"redo"` (and any similarly neutral request phrases) in `ReviewSignalDeriver.CORRECTION_MARKERS`, keep genuinely corrective phrases, and add tests that a plain retry request and an ordinary instruction containing those words do not set the candidate signal while a clear push-back such as "that's wrong" still does. (R14 follow-up) — NOT DONE. `d3535a5a` changed only the retry test input, not the marker list; `"try again"` and `"redo"` remain at `ReviewSignalDeriver.kt:73-74`.

## Scope boundaries
Documentation only. This job records findings and an ordered plan; it does not change source code, does not run builds or tests, and does not modify `entity12`/`MemoryLedger` or any ObjectBox id. The findings are recorded in [01-findings.md](01-findings.md) and the staged order in [02-implementation.md](02-implementation.md). Full GEPA prompt search, automatic executable-skill synthesis, wholesale memory-backend replacement, new model subscriptions, credential rotation, history rewriting and signing-key replacement remain deferred. Do not silently expand scope. The deferred backlog recorded in [01-findings.md](01-findings.md) (hypergraph subtask and skill links, multi-attempt sampling with a verifier, GEPA-style prompt optimization, validity-window facts, retrieval keyed on the current step or error, duplicate consolidation and pruning, Mem0-style add/update/delete/no-op decisions) is explicitly not scheduled and waits until the basic loop works and is measurable.

## Checkpoint
Owner: VPS orchestrator via the bridge, at Teddy's explicit request.
State: Stage A, Stage B, R14, R15 and Stage C part 1 (C01, C02, C03 a/b/c) implemented and CI-verified. C03 part (d) graph expansion is NOT implemented and remains a follow-up.
Exact commit: `b0bd770547bf6383a6602bd1b67f728b55061a7a`.
Next: C04-C06 (episode schema extension, Reflector prompt changes, plan-time gap check), which are separate jobs.
Blockers: none for C04-C06. A01 device update-over-existing-install verification is still PENDING. C03(d) graph expansion is blocked on a schema change. Nothing in this job is device-verified.

## Stage B and R14/R15 CI evidence

All runs are GitHub-hosted Actions. No build or test was run on the VPS or any local machine.

| Item | Commit | Android Build | Android Tests |
| --- | --- | --- | --- |
| B01 per-review delete, retry cap, stale recovery | `79a17f54` | cancelled by concurrency group (superseded by the next push); covered by the green run on `d3535a5a` | same |
| B02 locale-derived lesson language | `68931d45` | cancelled by concurrency group; covered by the green run on `d3535a5a` | same |
| R14 observed/interpreted split, candidate correction | `72c98edc` | success (https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39094387037) | failure (https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39094387036) — one JUnit assertion, fixed in `d3535a5a` |
| R15 byte-identical model enforcement | `2703b216` | not triggered (workflow-only change outside the `paths:` filter) | not triggered (same) |
| R14 test fix | `d3535a5a` | success (https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39095530659) | success (https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39095530635) |

The `d3535a5a` tree contains B01, B02, R14, R15 and the R14 test fix together, so its green Android Build and Android Tests runs verify the whole of Stage B plus R14 and R15 compile and their JVM tests pass.

ObjectBox Model: run 39096814734 (https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39096814734) succeeded on `d3535a5a`. The new step `Verify committed ObjectBox model is byte-identical` concluded success and printed `ObjectBox model is byte-identical to the committed file.` at runtime, which proves `git diff --exit-code` returned 0 and the committed `default.json` is exactly what a clean regeneration produces.

Not verified: update-over-existing-install on a real device (A01). This cannot be performed from CI and remains PENDING. No item in this job is device-verified.

## Stage C part 1 CI evidence

All runs are GitHub-hosted Actions. No build or test was run on the VPS or any local machine.

| Item | Commit | Android Build | Android Tests |
| --- | --- | --- | --- |
| C01 real task context + missingPrerequisites | `07cda3ea` | cancelled by concurrency group (superseded by the C02 push); covered by the green run on `55151c83` | same |
| C02 stopword-aware applicability | `55151c83` | cancelled by concurrency group (superseded by the C03 push) | success (https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39122619106) |
| C03(a)+(b) unicode tokenizer, bounded query, merge | `a364a032` | cancelled by concurrency group | cancelled by concurrency group (mid-test-execution); covered by the green run on `b0bd7705` |
| C03(c) episode vector retrieval | `b0bd7705` | in progress at time of writing (https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39123568648) | success (https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39123568673) |

The `b0bd7705` tree contains C01, C02, C03(a), C03(b) and C03(c) together, so its green Android Tests run verifies that the whole of Stage C part 1 compiles and its JVM tests pass. All runs are GitHub-hosted Actions; no build or test was run on the VPS or any local machine. Not verified: update-over-existing-install on a real device (A01). No item in this job is device-verified.

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
