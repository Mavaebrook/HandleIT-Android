# Review pipeline audit — handoff documentation

Owners: anyone adding diagnostics around the review pipeline. Audience: the operator installing
DEBUG builds to answer the three runtime-audit questions, and future maintainers of
`ReviewScheduler` / `PendingReviewRepository` / `ObjectBoxManager` / `ReviewPipelineAuditReporter`.

## The three questions and where their answers come from

1. **Does ReviewScheduler start?** → `schedulerConstructed` in the per-launch
   `[ReviewPipelineAudit]` block, plus the pre-existing `【启动计时】复习调度器启动完成`
   OperitApplication line.
2. **Does it execute passes, and with what outcomes?** → `ReviewPassAuditRecorder` inside the
   scheduler records every pass start/completion/outcome (including passes over an empty queue)
   and the startup recovery sweep; DEBUG builds also write one `D/ReviewScheduler:` line per pass
   start and one canonical `复习调度器 pass完成:` terminal line per outcome — for EVERY outcome:
   `completed`, `partial` (some profiles failed), `failed` (every profile failed), `error`,
   `cancelled`. Each terminal line carries `outcome`, `pending`, `succeeded`, `failedMarked` and
   `failedProfiles`, so an all-failed pass can never be mistaken for an empty completed one, and
   the startup-recovery line carries `recovered` and `failedProfiles` for the same reason.
   Timing: the startup recovery sweep runs immediately when the scheduler starts; the first full
   pass runs after `LOOP_TICK_MS` (60 s) and every 60 s thereafter. A quiet pass (empty queue)
   still logs its completion line, so execution is observable even with nothing pending.
3. **How many reviews by status / how many episodes?** → the `[ReviewPipelineAudit]` block,
   read from ONE all-status query (`PendingReviewRepository.getAll()`) plus
   `TaskEpisodeRepository.count()`.

## Store access contract (do not weaken)

- **Directory naming**: profile `"default"` → `files/objectbox`; any other profile →
  `files/objectbox_<profileId>` (`ObjectBoxManager.buildStore`). The audit reporter duplicates
  this rule in `ReviewPipelineAuditReporter.expectedStoreDirName` — keep the two in sync.
- **Two-stage existence gate**: a store is only read when BOTH hold — (1) `<dir>/data.mdb`
  already exists, and (2) `ObjectBoxManager.getExisting(profileId)` returns an already-open
  store. `getExisting` NEVER builds or opens a store and is synchronized on the same lock as
  `get`/`close`/`delete`/`closeAll`, so acquisition is atomic with respect to a concurrent
  profile deletion: diagnostics either get an open store or null — never a freshly created one.
  If the store vanishes between the gate and the read, the failure lands in `readErrors`; the
  audit path never falls back to a store-creating open. Checking only the directory is NOT
  enough: `ObjectBoxManager.get` (and `PendingReviewRepository`'s constructor) creates a
  database when opening a directory without one.
- **Read-only reads only, through existing-store-only accessors**: `PendingReviewRepository.getAllFrom(store)` and `TaskEpisodeRepository.countFrom(store)` against the store returned by `getExisting`. Never `recoverStaleProcessing`, `markProcessing`, `markFailed*`, `delete`, `runOnce()`, `ObjectBoxManager.get` / `delete()` / `closeAll()` from the audit path.
- **Consistency**: counts come from ONE query (`getAll()`), not a merge of per-status queries,
  so a status transition cannot skew the grouped counts. PROCESSING rows are included.

## Evidence extraction

From `files/logs/operit.log` (AppLogger format: `YYYY-MM-DD HH:mm:ss.SSS X/TAG: message`). Tag
filters are mandatory — the log also contains assistant request bodies that can contain any
keyword you search for.

Launch block (one per launch):

```sh
grep -aE '^[0-9-]+ [0-9:.]+ [A-Z]/OperitApplication: \[ReviewPipelineAudit\]' files/logs/operit.log
```

Scheduler pass evidence (per pass, DEBUG builds):

```sh
grep -aE '^[0-9-]+ [0-9:.]+ [A-Z]/ReviewScheduler: (复习调度器|恢复滞留)' files/logs/operit.log
```

Every field is logged as its own AppLogger line (AppLogger only prefixes the first line of a
multiline message), so the anchored grep keeps all fields.

Every pass outcome — `completed`, `partial`, `failed`, `error`, `cancelled` — emits exactly one
canonical `复习调度器 pass完成:` line under `D/ReviewScheduler:`, so terminal events for all
outcomes survive the pass-evidence grep above. Cancellation still propagates to the caller; the
terminal line is logged before the rethrow.

## State semantics

- observed number (`0` included) — a real observation;
- `UNAVAILABLE (store absent)` — no LMDB data file; answering would require creating the store;
- `UNAVAILABLE (store not open)` — the data file exists but no store is open for the profile
  (concurrently closed/deleted); answering would require opening — and possibly creating — the
  store;
- `not observed` — the value could not be obtained at snapshot time.

Pass outcomes: `completed` = every attempted profile drained (complete observation); `partial`
= at least one profile drained and at least one failed (counts are partial, `failedProfiles`
tells how many failed); `failed` = every attempted profile failed (no counts available);
`error` = the pass threw; `cancelled` = the pass was cancelled. `schedulerLastPassFailedProfiles`
and `schedulerStartupRecoveryFailedProfiles` carry the failed-profile counts.

Read failures are never silent: they land in `readErrors` of the block, and partial results
(e.g. review counts when only the episode count failed) are kept.

## CI

`PR Check` runs on every `pull_request` to `main`; `Android Build` / `Android Tests` are not
directly PR-triggered — the PR Check planner (`ci/script/pr_check.py`) selects them inside the
run (Kotlin-only change sets classify as `android_jvm`, which runs the JVM unit tests;
`android_full` would also assemble the APK). Merge only on green PR Check runs for the exact
head being merged; "mergeable" only means GitHub sees no conflict.

## Limitations (by design)

- Pass audit is in-memory only: evidence persists in the log lines, not across process restarts.
- Telemetry is forward-looking only: it **cannot establish whether the scheduler executed passes
  or how many rows existed before this instrumentation was added**. Do not infer "never
  executed" from missing historical lines.

## Handoff checklist for the next operator

1. Merge only after hosted CI is green on the head.
2. Install the DEBUG build from the merged commit; do not clear data.
3. Launch once; immediately extract the `[ReviewPipelineAudit]` block (Q1-start / Q2 / Q3).
4. Wait >= `LOOP_TICK_MS` (60 s) for the first full pass; extract the `D/ReviewScheduler:` lines
   (Q1-pass execution and outcomes: `completed`/`partial`/`failed`/`error`/`cancelled`).
5. Record the results together with the build's commit SHA.