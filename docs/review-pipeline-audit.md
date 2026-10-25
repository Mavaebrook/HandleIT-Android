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
   start and per pass completion. Timing: the startup recovery sweep runs immediately when the
   scheduler starts; the first full pass runs after `LOOP_TICK_MS` (60 s) and every 60 s
   thereafter. A quiet pass (empty queue) still logs its completion line, so execution is
   observable even with nothing pending.
3. **How many reviews by status / how many episodes?** → the `[ReviewPipelineAudit]` block,
   read from ONE all-status query (`PendingReviewRepository.getAll()`) plus
   `TaskEpisodeRepository.count()`.

## Store access contract (do not weaken)

- **Directory naming**: profile `"default"` → `files/objectbox`; any other profile →
  `files/objectbox_<profileId>` (`ObjectBoxManager.buildStore`). The audit reporter duplicates
  this rule in `ReviewPipelineAuditReporter.expectedStoreDirName` — keep the two in sync.
- **Existence guard**: a store is only touched when `<dir>/data.mdb` already exists. ObjectBox
  creates a database when opening a directory without one, and `PendingReviewRepository` opens
  the store in its field initializer — so checking only the directory would let a read
  materialize a database. The audit path must never create a store.
- **Read-only reads only**: `getAll()`, `count()`. Never `recoverStaleProcessing`,
  `markProcessing`, `markFailed*`, `delete`, `runOnce()`, `ObjectBoxManager.delete()` /
  `closeAll()` from the audit path.
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

## State semantics

- observed number (`0` included) — a real observation;
- `UNAVAILABLE (store absent)` — answering would require creating the store;
- `not observed` — the value could not be obtained at snapshot time.

Read failures are never silent: they land in `readErrors` of the block, and partial results
(e.g. review counts when only the episode count failed) are kept.

## CI

Pushes to `feature/review-pipeline-audit` run the repo's hosted workflows (`Android Build`,
`Android Tests`, `PR Check`). Merge only on green runs for the exact head being merged;
"mergeable" only means GitHub sees no conflict.

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
   (Q1-pass execution and outcomes).
5. Record the results together with the build's commit SHA.