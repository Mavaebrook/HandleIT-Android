# HandleIT / Bridge — Modular Self-Learning PRD
## Draft requirements baseline — 2026-10-28
Status: Initial requirements draft for review. This records the user's agreed direction; it does not approve a migration implementation, stack choice, merge, installation, or deletion. Migration planning follows review of this draft.

The accompanying architecture notes are proposals. Where they prescribe a language, database, deletion list, or migration sequence, those choices remain open. This requirements baseline distinguishes desired behavior from verified current behavior.

## 1. Product objective
Build a modular learning system that captures useful evidence, preserves user corrections, and makes relevant knowledge available at the right moment. The bridge should ultimately own tool control and learning/context orchestration, while HandleIT's chat UI and PRoot become replaceable adapters.

Better intake is a valuable improvement independently of task-speed or model-performance gains. Reliable recall should make that knowledge useful, but downstream gains remain a hypothesis to measure rather than an assumed outcome.

## 2. Current evidence and limits
The reported build-85 observation established 17 persisted episodes, an empty review queue, and naturally completed scheduler passes. It did not establish the quality of those episodes, successful processing of a nonempty queue, useful recall, or improved task performance.

The existing source review identified overlapping completion-time enqueue paths, limited review inputs, and workspace-gated episode recall. That supports consolidating ownership and validating intake/recall separately. It does not justify discarding all existing learning work or treating every legacy component as defective.

The inbound pipeline may be close to usable; preserve and validate its working components. Outbound recall is a separate unfinished product surface. An unsuccessful end-to-end experiment is not a gold-standard behavior baseline.

The MCP visibility failure remains a separate repair track. Cached tools and a “loaded” UI label do not prove current network reachability or availability to the conversation. Neither rebranding nor a security gate should be declared the sole cause without a demonstrated failing boundary.

## 3. Scope and boundaries
In scope: explicit knowledge domains, one policy owner, evidence-based intake, scoped recall, skill lifecycle, inspectable tool availability, portable service interfaces, and criteria for later migration and evaluation.

Out of scope for this draft: executing migration; deleting Android/ObjectBox data or schemas; choosing a bridge runtime/database; replacing the separately installed Operit app; model fine-tuning; and launching A/B trials now.

“Bridge” means the intended central controller, not automatically the existing local MCP subprocess bridge. A model API proxy may be part of that controller. Forwarding requests alone cannot prevent a harness from independently injecting memories or skills.

## 4. Knowledge domains
### Environment awareness
ENV-01: Maintain scoped knowledge of devices, hosts, projects, services, tools, dependencies, capabilities, and operating constraints. Distinguish intended configuration, observed state, and historical state.

ENV-02: Record source, observation time, scope, confidence, and freshness. Distinguish configured tools, reachable tools, and tools authorized/exposed to the current conversation. Historical success must not imply current availability. Revalidate volatile facts when the action requires it.

### User identity and preferences
USER-01: Separate explicit user statements and corrections from model-inferred preferences. Preserve provenance and correction history. A model summary must not silently overwrite an explicit correction.

USER-02: Apply user knowledge only within its authorized identity and sharing scope. Preferences do not grant execution permission. Support inspection, correction, retention choices, and deletion without silently recreating deleted information from stale indexes or summaries.

### Project and task knowledge
PROJECT-01: Store durable project decisions, constraints, and current task state with clear lifetimes and scope. Keep temporary task progress distinct from general knowledge.

PROJECT-02: Support useful recall for ordinary conversations that have no local workspace path. Resolve identity and scope from trusted controller context; do not guess access rights from model text, directory labels, or memory contents.

### Episodic memory
EPISODE-01: Record what happened: objective, relevant context, actions, tool observations, outcome evidence, failures, corrections, and unresolved uncertainty. Separate observed facts from the agent's claims about success.

EPISODE-02: Link related episodes and later corrections without rewriting historical evidence as though the correction had always been known. An episode is evidence for learning; saving one does not automatically create a durable fact or approved skill.

### Skills and reusable procedures
SKILL-01: Maintain versioned procedures with applicability, prerequisites, evidence, limitations, and lifecycle states such as draft, approved, superseded, and retired.

SKILL-02: Define a review/publication policy before learned procedures become active skills. Preserve links to supporting episodes. Retrieve only suitable approved versions for execution; speculative or unreviewed drafts must remain distinguishable.

## 5. Memory-use policy and ownership
POLICY-01: Provide a consistently available routing policy explaining when to use environment, user, project, episodic, and skill knowledge; when to retrieve; when to record a candidate; and when additional evidence or a user correction is needed. The agent must not first recall a memory to learn how to use memory.

POLICY-02: Treat retrieved material as evidence with scope and trust metadata. It cannot override controller policy, grant permissions, or promote instructions embedded in tool output into authority.

OWN-01: Select one authoritative learning/context owner for each authenticated conversation and memory namespace. Multiple specialized modules may cooperate behind that owner, but independent harness and bridge policies must not compete over saving, importance, retirement, or injection.

OWN-02: Separate conversation control, tool gateway, intake/learning workers, knowledge storage, retrieval/context assembly, and skill publication. Android UI, Shizuku, PRoot, model providers, and storage engines connect through explicit adapters.

OWN-03: In bridge-managed conversations, the harness must not secretly add its own memory/skill blocks or run a second automatic learning writer. Define ownership transfer and report an ownership mismatch. History compression is a separate function with an explicit contract, not an additional durable-memory authority.

OWN-04: Prune redundant Operit orchestration from HandleIT only after mapping its responsibilities and validating replacements. Preserve useful components. The separately installed Operit app and its files remain independently owned; no automatic cross-app adoption or copying.

## 6. Inbound learning requirements
IN-01: Capture sufficient task evidence from the actual interaction and tool results, not only a truncated final assistant summary. Preserve links to source events, corrections, uncertainty, and outcome evidence.

IN-02: Classify accepted knowledge by domain and scope. Several domain records may refer to the same evidence without duplicating the underlying event. Explicitly distinguish candidates, accepted facts, episodes, and skill drafts.

IN-03: Use stable event IDs and idempotent processing so retries, restarts, and replay do not duplicate learning effects. Expose pending, processing, failure, cancellation, and terminal outcomes with attributable reasons.

IN-04: Attribute delegated/subtask evidence to the correct task and namespace. Respect explicit learning controls and user corrections. Define retention and retirement decisions; cosmetic edits alone must not inflate confidence or reinforcement.

IN-05: Exclude credentials and unnecessary sensitive payloads from durable knowledge and diagnostic logs. Preserve useful evidence through redaction and source references without flattening uncertainty into invented facts.

IN-06: Validate intake on representative task records independently of recall or task efficiency: capture fidelity, correct scope/domain, provenance, correction preservation, duplicate prevention, and useful outcome classification. Empty successful scheduler passes are insufficient acceptance evidence.

## 7. Outbound recall and context requirements
OUT-01: Retrieve by task relevance, identity, environment, project, freshness, and permitted knowledge domains. Make recall available beyond coding workspaces.

OUT-02: Resolve contradictions and supersession explicitly. Avoid presenting stale environment facts or superseded preferences as current truth. Preserve access to historical evidence when history is the task.

OUT-03: Assemble a bounded context containing relevant knowledge and appropriate approved skills. Account for token budgets, redundancy, uncertainty, and source trust. Retrieved content must not elevate itself to system authority.

OUT-04: Record a redacted trace from retrieval candidates to selected records and the actual outbound model request. Report what was selected, omitted, superseded, or unavailable and why. “Stored” and “retrieved” are separate milestones; neither proves the model applied the knowledge correctly.

OUT-05: Distinguish no relevant results from unavailable storage, failed retrieval, or policy denial. Do not silently activate a competing legacy memory engine when the selected owner cannot serve a request.

OUT-06: Test relevance, scope isolation, freshness, contradiction handling, and inclusion in the model request before claiming task-performance improvement.

## 8. Bridge portability and tool control
PORT-01: Keep learning and retrieval policy independent of Android lifecycle classes, package names, PRoot paths, ObjectBox APIs, and a specific model provider. Use replaceable adapters and versioned contracts.

PORT-02: Portable records must preserve stable IDs, provenance, timestamps, scope, correction/supersession links, lifecycle state, and relationships. Treat vector indexes as derived data where feasible; retain enough source information to rebuild them.

PORT-03: Define interfaces for task evidence intake, knowledge inspection/correction, scoped recall, skill publication, capability discovery, and execution receipts. Specify authentication, authorization, cancellation, retry semantics, and ownership in these contracts.

PORT-04: The tool gateway owns authoritative tool identities and schemas, access policy, discovery state, routing, and execution receipts. A harness can display or request tools through an adapter. UI discovery must not be mistaken for model exposure or successful invocation.

PORT-05: Select the intended bridge runtime before major extraction work to minimize rewriting Android-only machinery. Language, database, deployment location, protocol, and the extent of API-proxy/agent-loop ownership are implementation decisions for migration planning.

PORT-06: Migration checks compare preserved data and required behavior, not the old system's unproven importance rankings. Retiring a storage adapter does not authorize destroying historical schemas, records, corrections, or the other app's state.

## 9. Acceptance scenarios
AC-01 — Evidence intake: A representative headless-server/Hermes setup task produces correctly scoped environment records and an episode grounded in actual observations. Unsupported success claims remain uncertain. This can pass without any task-speed gain.

AC-02 — Correction: A user corrects a host address or preference. Subsequent intake and recall preserve the correction and expose the superseded value only as historical evidence.

AC-03 — Isolation: Similar tasks on a home server and VPS do not leak host-specific commands, credentials, permissions, or service state across scopes.

AC-04 — Fresh-session recall: A later related conversation without a workspace path receives the appropriate accepted knowledge in its actual model request, with a trace of selection and omissions.

AC-05 — Skill lifecycle: Repeated useful evidence can propose a procedure; it remains a draft until publication criteria are satisfied. Superseded versions are not silently injected as current guidance.

AC-06 — Tool awareness: A configured but unreachable MCP server is distinguishable from a reachable server excluded by conversation policy. The agent can identify available capabilities without pretending a cached list proves live execution.

AC-07 — Ownership and replay: Restarting, cancelling, or replaying task events does not create competing writers or duplicate accepted learning. A bridge-managed conversation receives one assembled memory/skill context.

AC-08 — Portability: Export/import through a candidate bridge adapter preserves record identity, scope, provenance, corrections, and relationships. Test data fidelity separately from recall quality and task outcomes.

## 10. Evaluation strategy
Evaluate three layers separately: intake quality, recall/context quality, and the model's application of recalled knowledge. Passing one layer does not establish the others.

Later, run paired duplicate tasks with and without this system. Hold model/version, tool permissions, task inputs, starting environment, and budgets constant except for the intended treatment. Isolate memory and environment state between conditions to prevent contamination and account for ordering effects.

Include a learning task followed by a related task in a fresh session; repeating one task without retained learning would not test the intended benefit. Compare correctness, repeated errors, user corrections, unnecessary tool calls, completion time, tokens, and cost. Choose representative tasks, scoring criteria, repetitions, and thresholds before running the comparison.

A/B evaluation is deferred until intake and recall meet their own acceptance criteria. Improvement is expected to be investigated, not guaranteed by architecture or unit-test counts.

## 11. Migration planning requirements — next phase
Plan the migration after this requirements review. Inventory current writers, readers, prompt injectors, schedulers, skills, and user controls; map each responsibility to retain, adapt, replace, or retire.

Identify the bridge target and define contracts and ownership boundaries before extracting code. Validate one narrow end-to-end path before broad pruning. Preserve accessible knowledge and explicit corrections throughout transition.

Specify evidence for safe handoff and for removing each competing path. Do not use successful historical HandleIT model performance as an acceptance baseline because it has not been established.

The MCP repair is a bounded prerequisite for dependable tool use, not authorization for a broad rebrand/path migration. Existing architecture cutover notes are planning inputs, not an approved execution sequence.

## 12. Open decisions
- Bridge repository, runtime, deployment, protocol, and whether model proxy and agent-loop ownership transfer together or in stages.
- Which existing intake components meet acceptance and can be reused; which need richer evidence or different interfaces.
- Retention, freshness, confidence, skill publication, deletion, and cross-device sharing policies.
- User inspection/correction controls, offline behavior, latency/token budgets, and operation when a selected owner is unavailable.
- Pilot task fixtures and measurable intake/recall thresholds; later A/B sample size and scoring.
- Storage migration format and a preservation check using actual historical data before any destructive change.

## 13. Document control
This draft records the agreed product direction and proposed testable requirements. Requirement IDs support review and implementation planning; they do not imply implementation is complete.

Repository working copy: https://github.com/Mavaebrook/HandleIT-Android/pull/19
Source architecture snapshot: 17089b41b4f192968deeb0d36163333c53e222aa
The Google Doc requirements tab is the exported review copy of this revision. Copies do not synchronize automatically. Subsequent edits should identify the new revision and reconcile both copies.
