# Changelog

All notable changes to HandleIT-Android are recorded here. Entries are grouped by date and reference the relevant TODO checklist where applicable.

## 2026-10-18 — Learning pipeline hardening: R17 (dedicated reviewer model) and R14b (retry markers) recorded (docs only)

Recorded two new findings and their checklist items in `docs/TODO/learning-pipeline-hardening-20261017/`. Documentation only: no source code, resource, workflow or checkbox changed; no build or test run.

### Added
- R17 in `01-findings.md`: reviews run on the `MEMORY` and `SUMMARY` models (`ReflectorCurator.kt:42` and `:69`) and there is no dedicated reviewer/teacher model. Records the proposed `REVIEW` function type, the three exhaustive `when` branches in `FunctionalConfigScreen.kt` that must gain a branch, the string resources, the settings privacy text, and the requirement to record the model config id/name/provider on every review so the Stage F baseline/same-model/stronger-model comparison can be measured (left UNMEASURED until replays exist).
- R14b in `01-findings.md`: `"try again"` and `"redo"` remain in `ReviewSignalDeriver.CORRECTION_MARKERS` (`ReviewSignalDeriver.kt:73-74`); `d3535a5a` changed only the retry test input, not the marker list, so a plain retry request is still flagged as a candidate correction.
- Checklist items H01 (R17) and R14b (R14 follow-up) in `index.md`, both unchecked.
- Stage H01 text in `02-implementation.md`, placed after Stage B and before Stage D because Stage D's Curator wiring depends on it.

### Changed
- `index.md` checkpoint refreshed: exact commit `f2547e57`, next action Stage C unless the user reprioritizes H01, blockers unchanged (A01 device verification pending; R14-UI and R14b open follow-ups).

### Notes
- Verified at HEAD `f2547e57`: the Reflector/Curator routing, the absence of a review-specific model setting, `normalizeMapping` defaulting and unknown-key skipping, the three exhaustive `when` expressions, the `ROLE_RESPONSE_PLANNER` precedent, and the `CORRECTION_MARKERS` contents. One claim was corrected: `ApiProviderType` has 38 provider types, not "about 40".
- Known unresolved security item (unchanged): earlier signing-document redaction does not erase exposed passwords from git history. No rotation or history rewrite is performed under this task.


## 2026-10-18 — Learning pipeline hardening: Stage B, R14 and R15

Implemented Stage B (B01, B02) and the Stage A review refinements R14 and R15 of `docs/TODO/learning-pipeline-hardening-20261017/`.

### Added
- `PendingReviewBatchPolicy` (`app/src/main/java/com/ai/assistance/operit/data/repository/PendingReviewBatchPolicy.kt`): a pure decision core for the review drain — `MAX_ATTEMPTS = 3`, `PROCESSING_STALE_AFTER_MS = 10 min`, `dispositionFor`, `isStaleProcessing`.
- `ReviewLessonLanguage` (`app/src/main/java/com/ai/assistance/operit/data/learning/ReviewLessonLanguage.kt`): a pure locale decision that derives `useEnglish` from the user's locale, mirroring `LocaleUtils.usesChineseContent`.
- Terminal `STATUS_DEAD_LETTER` on `PendingReviewEntity`, with `getDeadLettered()` for inspection.
- A recovery sweep in `ReviewScheduler.start()` that returns stale `PROCESSING` rows to `PENDING`.
- A `Verify committed ObjectBox model is byte-identical` step in `.github/workflows/objectbox-model.yml` that fails the workflow when a regeneration differs from the committed model.

### Changed
- B01 (`79a17f54`): `drainPendingReviewsForSpace` now decides each review individually and deletes only the ids that produced an episode; a review whose `reflect()` returned null is marked failed on its own and stays retryable; a review reaching `MAX_ATTEMPTS` (3) is dead-lettered and never retried or deleted; stale `PROCESSING` rows are recovered on start. Per-space failure isolation from `58bedd67` is preserved.
- B02 (`68931d45`): `useEnglish` is no longer hardcoded; it is derived from the user's locale once per batch.
- R14 (`72c98edc`): `ReviewSignals` now carries the raw `ObservedTurnEvents` in `ReviewSignals.observed` alongside the interpreted booleans; `complexSuccessWithReusableLesson` was renamed to `complexSuccessfulTurn` and the reason string is now "complex successful turn warranted review" and no longer claims a lesson; correction detection uses word-boundary matching, the bare `"wrong"` marker was removed and replaced by specific phrases, a lexical match now sets `candidateCorrection` only, and only a deliberate action sets `explicitUserCorrection`. `ReviewTriggerPolicy` remains the single decision point; a candidate alone may trigger a review, checked last and bounded by the preceding-assistant-turn plus matched-marker requirement.
- R15 (`2703b216`): the ObjectBox Model workflow now fails on a non-byte-identical regeneration (and when the model was not generated); the step has no `if:` condition and no `continue-on-error`, and the print/upload steps keep `if: always()`.

### Fixed
- Reviews are no longer silently lost when one review in a batch fails, and permanently failing reviews no longer retry forever.
- Rows stranded in `PROCESSING` after a crash are recovered instead of leaking permanently.
- Chinese-locale users no longer receive English lessons.
- CI failure on `72c98edc`: Android Tests run 39094387036 failed one JUnit assertion, `ReviewSignalDeriverTest.retry_doesNotSetExplicitCorrection` at `ReviewSignalDeriverTest.kt:222`, because the test used "try again" as its input while "try again" is itself a `CORRECTION_MARKERS` entry, so `candidateCorrection` was legitimately set (1423 tests ran, 1 failed, 6 skipped). The root cause was the test's input, not the implementation; fixed in `d3535a5a` by using marker-free text.

### Tests
- New `ReviewSchedulerBatchTest` (9 tests): `batchOfThreeWithOneFailure_deletesExactlyTwo`, `reviewFailingPastMaxAttempts_isDeadLettered`, `deadLetteredReview_isNotRetried`, `reviewBelowMaxAttempts_staysRetryable`, `staleProcessingRow_isRecovered`, `freshProcessingRow_isLeftAlone`, `processingRowExactlyAtStalenessBoundary_isNotStale`, `nonProcessingStatus_isNeverStale`, `succeededReview_isDeleted`.
- New `ReviewLessonLanguageTest` (7 tests): `chineseLocale_requestsChinese`, `chineseRegionVariant_requestsChinese`, `englishLocale_requestsEnglish`, `otherSupportedLocale_requestsEnglish`, `uppercaseChineseCode_isStillChinese`, `blankCode_requestsEnglish`, `lessonLanguage_matchesUseEnglish`.
- `ReviewSignalDeriverTest` grew 13 → 23 tests: 4 complexity tests renamed to the `complexSuccessfulTurn` name, 3 correction tests updated to candidate semantics, and 10 R14 tests added (`retry_doesNotSetExplicitCorrection`, `edit_doesNotSetExplicitCorrection`, `normalReplyBeginningWithNo_doesNotSetExplicitCorrection`, `wrongInsideAnotherWord_doesNotSetCandidate`, `wrongAsUnrelatedTopic_doesNotSetCandidate`, `lexicalMatch_setsCandidateOnly`, `explicitInput_setsExplicit`, `complexButUnremarkableTurn_triggersReviewWithoutAssertingLesson`, `observedEvents_areRetainedAlongsideSignals`, `candidateCorrection_aloneTriggersBoundedReview`).
- `ReviewTriggerPolicyTest` grew 8 → 10 tests: `complexSuccessWithReusableLesson_triggersReview` renamed to `complexSuccessfulTurn_triggersReview` (now asserting the reason does NOT contain "lesson"), plus `candidateCorrection_triggersReview` and `explicitCorrection_takesPrecedenceOverCandidate`.
- No test was deleted. `ReviewSchedulerMemorySpaceTest` (5 tests) is unchanged and still green.

### Verification
- Final green CI on `d3535a5a`: Android Build success (https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39095530659), Android Tests success (https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39095530635). That tree contains B01, B02, R14, R15 and the R14 test fix together, so its green runs verify the whole of Stage B plus R14 and R15 compile and their JVM tests pass.
- B01 (`79a17f54`) and B02 (`68931d45`): Android Build and Android Tests runs were cancelled by the concurrency group (superseded by the next push), not by a code failure; zero failing steps. Covered by the green run on `d3535a5a`.
- R14 (`72c98edc`): Android Build success (https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39094387037); Android Tests failure (https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39094387036), fixed in `d3535a5a`.
- R15 (`2703b216`) touches only `.github/workflows/objectbox-model.yml`, which is outside the `paths:` filter of Android Build and Android Tests, so neither workflow ran for that SHA; ObjectBox Model is workflow_dispatch-only.
- ObjectBox Model run 39096814734 (https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39096814734) succeeded on `d3535a5a`; the `Verify committed ObjectBox model is byte-identical` step concluded success and printed `ObjectBox model is byte-identical to the committed file.` at runtime, proving `git diff --exit-code` returned 0.
- All runs are GitHub-hosted Actions. No build or test was run on the VPS or any local machine.

### Notes
- A01 device update-over-existing-install verification is still PENDING and its checklist item remains unchecked. Nothing in this job is device-verified.
- R14 finding: there is no explicit user-initiated "this reply is wrong" control in the app. Searches for `markWrong`, `markAsWrong`, `userCorrection`, `explicitCorrection`, `reportIncorrect`, `flagWrong`, `thumbsDown`, `dislike`, `isWrong` return zero hits; the only user-initiated re-run action is `regenerate`, which is a retry, not a correction marker. The explicit-signal input (`TurnObservations.explicitCorrectionAction`) is implemented but set by nothing in production. No UI was built; the R14-UI follow-up is open.
- B02 limitation: the generation language is not persisted with the lesson. `TaskEpisode`/`TaskEpisodeEntity` have no language column; adding one would change the ObjectBox schema and require regenerating `default.json`, which was out of scope. Recording it is a follow-up requiring a model regeneration.
- Byte-identical regeneration is necessary but is not proof of upgrade compatibility.
- Stage C is not started.

## 2026-10-17 — Learning pipeline hardening: Stage A review amendments (docs only)

- Recorded R14 (review signals conflate the observed event with its interpreted meaning), R15 (ObjectBox schema lineage recorded but upgrade compatibility not yet proven) and R16 (keep review trigger, lesson confidence and promotion evidence separate) in docs/TODO/learning-pipeline-hardening-20261017/01-findings.md.
- Promoted G01 (per-lesson outcome tracking) and G02 (dispatch-level action guard) into the ordered checklist with finding text, acceptance tests and stage placement.
- Amended E02/N03 (retrieve compact episode indexes first, expand only relevant trace segments), C06 (dismissal is not evidence a lesson is wrong) and F02 (tune only on held-out replay cases) by adding superseded-wording notes; no existing text was deleted; the checkpoint Blockers line was extended, not reworded.
- Recorded an explicitly deferred backlog: hypergraph subtask/skill links, multi-attempt sampling with a verifier, GEPA-style prompt optimization, validity-window facts, retrieval keyed on current step or error, duplicate consolidation and pruning, Mem0-style add/update/delete/no-op decisions.
- Updated the index.md checkpoint (owner, state, next action Stage B, blocker A01 device verification pending).
- Documentation only: no source code, workflow or checkbox changed; no build or test run.

## 2026-10-17 — learning pipeline hardening, Stage A

Implemented Stage A of `docs/TODO/learning-pipeline-hardening-20261017/`: the review queue now fills, and it drains from the memory space the review was written to.

### Added
- New CI workflow `.github/workflows/objectbox-model.yml` (workflow_dispatch only) that regenerates and uploads the ObjectBox model.

### Changed
- A01: committed the ObjectBox model for `TaskEpisodeEntity`, `SkillRevisionEntity` and `PendingReviewEntity` (ids 12/13/14), generated by the new `ObjectBox Model` workflow; purely additive, no existing entity changed. Device update-over-existing-install verification pending.
- A02: `ReviewSignals` are now derived from deterministic turn outcomes (tool results, model hops, conversation turns) instead of hardcoded `false`, so the review queue actually fills.
- A03: `ReviewScheduler` drains every memory space with pending reviews instead of the hardcoded `"default"` space, and saves each episode into the space it was enqueued in.

### Notes
- A01's device update-over-existing-install verification has not been performed and remains pending; its checklist item stays unchecked.
- Stage B (batch-delete correctness, retry cap, `PROCESSING` recovery, locale-aware `useEnglish`) is not started.


## 2026-10-17 — learning pipeline hardening review

Recorded an independent static review of the learning pipeline as an ordered, checkable TODO in `docs/TODO/learning-pipeline-hardening-20261017/`. Documentation only; no source code changed.

### Added
- `docs/TODO/learning-pipeline-hardening-20261017/` with `index.md` (ordered checklist A01-F02), `01-findings.md` (per-finding evidence, impact, fix and acceptance test) and `02-implementation.md` (staged order A-F).
- README link to the new TODO alongside the existing active TODO link.

### Notes
- Every finding was re-verified against commit `28b53abd` before being recorded. Three findings were corrected rather than copied forward: there is no `entity12`/`MemoryLedger` in the schema (the strings appear only in prose), `ReflectorCurator.reflect` is wired but unreachable rather than entirely unwired, and `getPendingAndFailed` excludes `PROCESSING` rows rather than including them.
- The T09/T10 checklist marks in `docs/TODO/reliable-learning-architect-20261016/index.md` overstate the wiring; a visible note was added under T07-T10 without deleting the history.
- Known unresolved security item: earlier signing-document redaction does not erase exposed passwords from git history. No rotation or history rewrite is performed under this task.


## 2026-10-16 — reliable learning and persistent architect workflow

Implemented the bounded specification in `docs/TODO/reliable-learning-architect-20261016/` (stages A–E).

### Added
- Durable project requirements in `AGENTS.md` and a runtime architect-rules hook pinned into the existing `SystemPromptComposeHook` (task start/resume) and `SummaryGenerateHook` (compaction) registries, scoped to development/workspace tasks. Completion-gate helper (`ArchitectCompletionGate`).
- Root `CHANGELOG.md` and README links to the changelog and active TODO.
- Structured task episodes (`TaskEpisode`) with independent-evidence deduplication, bounded similar-task retrieval (top-k 3–5), and conditional missing-prerequisite applicability checks (`TaskEpisodeRetrieval`).
- Bounded similar-task preflight for development tasks (`TaskPreflightProvider`/`TaskPreflightFormatter`), pure retrieval with no extra model call.
- Versioned candidate skill revisions (`SkillRevision`) with a constraint-preserving promotion lifecycle (draft → evaluate → promote/reject) and rollback (`SkillRevisionLifecycle`).
- Logical Reflector/Curator roles (`ReflectorCurator`) reusing existing configured model providers; bounded review-trigger policy (`ReviewTriggerPolicy`); resumable review scheduler (`ReviewScheduler`).

### Fixed
- Inactive/out-of-scope memory resurrection: graph expansion (both outgoing links and backlinks) and the final result path now enforce the allowed active + in-scope id set (`MemoryGraphScopeFilter`). Historical access is preserved; only normal active retrieval is filtered.

### Tests
- 47 new JVM regression tests: `MemoryGraphScopeFilterTest` (8), `TaskEpisodeRetrievalTest` (14), `SkillRevisionLifecycleTest` (12), `ReviewTriggerPolicyTest` (7), `ReflectorCuratorParseTest` (6).

### Verification
- GitHub-hosted CI on commit `c0bba97a`: Android Build success (https://github.com/Mavaebrook/HandleIT-Android/actions/runs/38958024774) and Android Tests success (https://github.com/Mavaebrook/HandleIT-Android/actions/runs/38958024652).
- An earlier CI run on `ef2f3bc5` failed at `:app:compileDebugKotlin` (4 Kotlin compile errors); fixed in `c0bba97a`.
- Device acceptance remains pending (no actual device evidence available).

### Notes
- Full GEPA prompt search, automatic executable-skill synthesis, wholesale memory-backend replacement, new model subscriptions, credential rotation, history rewriting, and signing-key replacement remain deferred.
- Teacher-comparison evaluation (baseline vs same-model vs stronger-model) was not measured; no stronger configured model access was confirmed.
- Device acceptance remains pending (no actual device evidence available).
- Known unresolved security item: earlier signing-document redaction does not erase exposed passwords from git history. No rotation or history rewrite is performed under this task.

## 2026-10-16

### Added
- Tracked TODO for reliable learning and persistent architect workflow: `docs/TODO/reliable-learning-architect-20261016/` (design, implementation sequence, and progress checklist).
- Durable project requirements section in `AGENTS.md`, pinned separately from salience-ranked memory so development constraints survive task resume and context compaction.
- Runtime architect-rules preflight/resume/compaction hook and completion gate (see `docs/TODO/reliable-learning-architect-20261016/`).

### Notes
- Full GEPA prompt search, automatic executable-skill synthesis, wholesale memory-backend replacement, new model subscriptions, credential rotation, history rewriting, and signing-key replacement remain deferred.
- Known unresolved security item: earlier signing-document redaction does not erase exposed passwords from git history. No rotation or history rewrite is performed under this task.
