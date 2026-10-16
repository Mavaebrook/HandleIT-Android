# Implementation sequence

Follow [the design](01-design.md) and update [the checklist](index.md) at every checkpoint.

## Stage A — inspect and make rules durable (T03-T05)
Old: architect instructions can disappear from conversational context; no root changelog.
Change: inspect active jobs, current source and skills integration; extend AGENTS.md without deleting prior rules, connect runtime preflight/resume and completion checks, create CHANGELOG.md and README link.
Expected: development constraints remain available across resume/compaction; changes have visible dated evidence.
Checkpoint: exact touched components, commit, tests/limitations, next action.

## Stage B — correct retrieval (T06)
Old: graph targets can bypass initial inactive/scope filtering.
Change: enforce allowed active targets during both graph directions and final results, preserve historical access.
Expected: corrected facts and out-of-scope nodes cannot appear in normal results.
Checkpoint: regression cases, CI run and commit. Do not mark verified before tests finish.

## Stage C — episodes and similar-task preflight (T07-T08)
Old: recency/occurrence metadata does not explain failures, fixes or required prerequisites.
Change: introduce compatible compact episode records and bounded retrieval, independent evidence deduplication and applicability checks.
Expected: prior discoveries inform plans without treating all historic details as mandatory.
Checkpoint: demonstrate update-versus-fresh-install prerequisite distinction and bounded retrieval.

## Stage D — teacher review and skill updates (T09-T10)
Old: reviewed learning path does not evaluate versioned skill proposals against task outcomes.
Change: integrate selective review, incremental candidate revisions and evidence-based promotion with the existing skill/provider machinery.
Expected: explicit user constraints stay authoritative; untested inferred procedures remain candidates; revisions are resumable and reversible.
Checkpoint: candidate/promotion/rejection examples, replay tests and model-call budget.

## Stage E — verify and hand off (T11-T14)
Run supported build/tests only in GitHub-hosted Actions. Report baseline/same-model/stronger-model evaluation results only when actually measured. Do not invent access to devices or emulators.
Record installed-device acceptance as pending if unavailable. Update changelog and relevant README, include exact commits and CI URLs, outstanding credentials-history issue, limitations and next action.
Append [DONE] to a stage only once its defined work and evidence are complete. A partial implementation must remain visibly partial.

## Turn-budget checkpoint
Before exhaustion or a blocker, commit safe progress and update index.md plus the bridge issue with current state, remaining unchecked IDs and an exact resume instruction. Do not start duplicate workers or silently abandon the task.
