# Changelog

All notable changes to HandleIT-Android are recorded here. Entries are grouped by date and reference the relevant TODO checklist where applicable.

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
