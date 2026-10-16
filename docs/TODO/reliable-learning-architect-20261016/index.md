---
repository: https://github.com/Mavaebrook/HandleIT-Android
created: 2026-10-16
status: planned
---
# Reliable learning and persistent architect workflow

## Plain-English reason
HandleIT should remember what made earlier tasks succeed, stop repeating corrected mistakes, and keep its architect rules and progress visible even in long conversations. This checklist is the durable source of progress, not chat history.

## Progress rules
Read this file on task start, resume, and after context compaction. Keep a current checkpoint with owner/job, exact commit, next action, and blockers. Update after each meaningful verified milestone, not only at the end. Check an item only with commit and verification evidence. Distinguish implemented, CI-verified, and device-verified. Pending or blocked items remain unchecked. Never mark the entire job complete merely because a build passes.

## Ordered implementation checklist
- [x] T00 Create this repository checklist before specification and dispatch.
- [x] T01 Commit the technical specification and numbered implementation steps.
- [ ] T02 Dispatch bridge job and record its issue link.
- [ ] T03 Inspect latest code and active jobs; map integration points and preserve concurrent work.
- [ ] T04 Persist architect requirements in AGENTS.md and runtime start/resume/compaction handling.
- [ ] T05 Add completion checks and root CHANGELOG.md; link active TODO from README.
- [ ] T06 Fix inactive-memory resurrection and scope leakage in graph expansion and final retrieval.
- [ ] T07 Record structured task episodes, independent evidence, outcomes, and required prerequisites.
- [ ] T08 Add bounded similar-task preflight and context-sensitive missing-prerequisite detection.
- [ ] T09 Add selective Reflector/Curator review and versioned candidate skill updates.
- [ ] T10 Validate skill revisions before promotion; preserve user constraints and rollback history.
- [ ] T11 Add meaningful regression coverage and evaluate current versus same-model versus stronger-teacher configurations.
- [ ] T12 Verify Android Build and Android Tests through GitHub-hosted Actions; report exact SHA/run URLs.
- [ ] T13 Perform or explicitly hand off device acceptance: update compatibility, notification snapshot, memory correction, skill preflight.
- [ ] T14 Finish README/changelog/checklist evidence and report remaining blockers honestly.

## Scope boundaries
Implement the minimal end-to-end learning and architect-persistence path described in [01-design.md](01-design.md), following [02-implementation.md](02-implementation.md). Full GEPA search, automatic executable skill synthesis, wholesale memory replacement, new model subscriptions, credential rotation, history rewriting, and signing-key replacement are deferred. Do not silently expand scope.

## Checkpoint
Owner: ChatGPT preparing bridge dispatch.
State: checklist and technical specification committed; implementation not started.
Next: dispatch bridge job.
Evidence: T00 commit 7d1623d; design commit 7354f04; numbered implementation plan committed alongside this milestone.
