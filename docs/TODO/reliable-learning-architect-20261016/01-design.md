# Design: reliable learning and persistent architect workflow

## Brief reason
The agent should carry forward useful discoveries, avoid previously corrected mistakes, and notice prerequisites that were essential to similar tasks. Architect instructions and the progress checklist must survive long threads. A stronger model can review selected experiences without training model weights or reviewing every message.

## Baseline and evidence
Reviewed baseline: 51efac1eb50ec1377e296fded6953ebb2cbeed85. Reinspect current main before implementing.
MemoryRepository combines keywords/tags, vectors and graph expansion with salience weighting. MemoryLibrary reinforces changed text and marks CORRECTS/SUPERSEDES targets inactive.
Confirmed static-review problem: inactive memories are filtered before initial scoring, but outgoing/incoming graph expansion can add them again; the final result path lacks an active/scope filter.
Text inequality is not semantic novelty. Occurrence counts are not evidence of usefulness. The recency floor does not guarantee survival of the relevance threshold.
The reviewed memory commit adds no dedicated regression tests. Existing app skills/tool support must be inspected and reused rather than duplicated.

## 1. Persistent architect rules
Reason: mandatory development rules must not depend on an old message being remembered.
Technical: preserve existing AGENTS.md and add concise project requirements: canonical repository and Actions builds, preserve explicit corrections, plan multistep tasks, update active TODO and changelog with meaningful milestones, and update relevant README documentation. Keep agent execution rules in AGENTS.md rather than confusing them with product features.
Within the app's agent runtime, identify existing skill-loading and context construction hooks. Load applicable architect rules at development-task start, task resume and context compaction. Pin these separately from salience-ranked optional memories; do not allow decay or semantic retrieval to remove mandatory constraints. Scope them to development tasks, not every casual conversation.
Inspect action dispatch for deterministic enforcement of applicable build/repository restrictions. A prompt alone is not enforcement. If a runtime restriction cannot be enforced reliably, explicitly document the gap rather than claiming a hard gate.
Completion checks must reconcile acceptance criteria with changes, tests and documentation. Report unfinished or blocked items; never manufacture a documentation edit for a task where it is irrelevant.

## 2. Memory correctness
Reason: a corrected fact must not return through a related memory.
Technical: enforce permitted IDs, active state, folder/time/profile scope on graph targets and final returned results. Retain obsolete facts for deliberate historical inspection, not normal active retrieval. Add regressions for both outgoing and incoming links, SUPERSEDES chains and scope boundaries.
Preserve schema identity, existing ObjectBox IDs and persisted user data. Prefer compatible metadata extensions; inspect entity12/MemoryLedger before any migration and do not modify them in this task. Treat one incident repeated in summaries as one evidence source. Do not equate paraphrasing with new evidence.
Separate factual validity from procedural usefulness: retain source/episode references, applicable scope and supersession history; avoid importing a complete replacement database.

## 3. Structured task episodes and preflight
Reason: retrieve the few lessons that matter before repeating a task.
Technical: persist compact episodes with task/context, outcome, failure, confirmed cause or explicitly marked hypothesis, resolution, required prerequisites, applicable constraints, source evidence and related skill version. Avoid secrets, private payload dumps and unnecessary full transcript retention.
Before substantial tasks retrieve a small configurable top-k of relevant episodes (initial target 3-5) and applicable skills. Use vector relevance plus graph links for failures, fixes and prerequisites. Bound tokens, graph expansion and latency; avoid full-history scans and raw-trace analysis by default. Profile current metadata-loading behavior before introducing caches.
Identify recurring independent mistakes and candidate omitted requirements. Verify applicability against the present task. For example signing continuity applies to an in-place APK update, not automatically to every installation. Infer from evidence where safe; ask only for materially blocking unknowns. Never convert historical assumptions into new user mandates.
Use existing planning calls when feasible instead of an unconditional extra model call per turn.

## 4. Teacher review and skill evolution
Reason: turn successful fixes into reusable procedures, rather than merely storing more facts.
Technical: implement optional logical Reflector and Curator roles using existing configured model providers. They need not be separate agents or models. Permit a stronger configured reviewer, but do not require a new subscription or local training.
Trigger bounded post-task review on explicit corrections, failures/repeated repairs, or complex successes with reusable lessons. No every-turn teacher call. Persist pending reviews for resumability; reuse existing background infrastructure where available.
Reflector proposes evidence-backed lessons; Curator produces localized changes to versioned skills. Do not rewrite entire skill libraries or system prompts. A skill revision records scope, prerequisites, steps, checks, pitfalls, evidence, parent version and promotion status.
Explicit user instructions become applicable constraints immediately. Teacher-generated inferences remain candidates. No reviewer may weaken or override user constraints. Preserve provenance and reversible version history.
Facts that change belong in memory; stable procedures belong in skills. Reuse existing skill format, discoverability and execution machinery.
Promotion lifecycle: draft -> evaluate -> promote or reject; retain prior version and explicit rollback capability. Deduplicate evidence and distinguish semantic paraphrases from independent outcomes. Track helpful/harmful outcomes, not just occurrence counts.
Automatic creation of executable tools/code is deferred; this phase updates instructional skills only.

## 5. Evaluation and acceptance
Reason: prove that lessons help instead of assuming a teacher is correct.
Technical: create a small fixture set based on real failure patterns: canonical GitHub build versus local APK rebuild, corrected fact through graph backlinks, signing prerequisite for update versus fresh installation, repeated summary of one incident, and skill persistence after resume/compaction.
Compare baseline, same-model review and stronger-model review on matched cases with the same student and inputs. Record success, instruction violations, recurrence, latency and tokens/cost if available. No invented benchmark numbers. If a stronger configured model is unavailable, leave that comparison pending.
Run Android build/unit tests only through GitHub-hosted Actions; never on VPS/phone/PRoot. Do not delete or disable tests to obtain green status; review deleted XaiProviderReasoningTest coverage and restore equivalent valid coverage if applicable.
Use backward-compatible changes for installed builds. Preserve package identity, signing certificate, notification snapshot patch, and attribution. Keep versioning changes explicit and document them before delivery.

## Deferred work and outstanding security note
Full GEPA prompt search, autonomous executable-skill synthesis and Graphiti backend migration are future options, not deliverables in this job.
Earlier signing-document redaction does not erase exposed passwords from git history. Report this unresolved security item without reproducing values. Do not rotate credentials, replace keys, or rewrite history under this task.

## References and adaptation
ACE supplies Generator/Reflector/Curator roles and incremental playbooks: https://arxiv.org/abs/2510.04618
Reflexion supplies feedback-to-lesson loops: https://arxiv.org/abs/2303.11366
Dynamic Cheatsheet supplies reusable strategies: https://arxiv.org/abs/2504.07952
A-MEM supplies evolving linked notes: https://arxiv.org/abs/2502.12110
Graphiti supplies temporal validity/provenance concepts: https://github.com/getzep/graphiti
Voyager supplies the future verified executable-skill pattern: https://voyager.minedojo.org/
GEPA supplies evaluation-driven candidate selection: https://arxiv.org/abs/2507.19457
These are design references, not claims that their complete frameworks are installed. ACE's cited experiment used the same model across roles; stronger-teacher benefit must be measured.
