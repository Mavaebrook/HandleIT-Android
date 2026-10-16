# Changelog

All notable changes to HandleIT-Android are recorded here. Entries are grouped by date and reference the relevant TODO checklist where applicable.

## 2026-10-16

### Added
- Tracked TODO for reliable learning and persistent architect workflow: `docs/TODO/reliable-learning-architect-20261016/` (design, implementation sequence, and progress checklist).
- Durable project requirements section in `AGENTS.md`, pinned separately from salience-ranked memory so development constraints survive task resume and context compaction.
- Runtime architect-rules preflight/resume/compaction hook and completion gate (see `docs/TODO/reliable-learning-architect-20261016/`).

### Notes
- Full GEPA prompt search, automatic executable-skill synthesis, wholesale memory-backend replacement, new model subscriptions, credential rotation, history rewriting, and signing-key replacement remain deferred.
- Known unresolved security item: earlier signing-document redaction does not erase exposed passwords from git history. No rotation or history rewrite is performed under this task.
