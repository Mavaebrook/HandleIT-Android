---
repository: https://github.com/Mavaebrook/HandleIT-Android
created: 2026-10-27
status: design-and-cutover-plan
source_snapshot: 17089b41b4f192968deeb0d36163333c53e222aa
---

# One owner for learning and model context

## User direction

Retire the competing Operit memory-management paths from HandleIT and make the system modular. Android chat UI and PRoot are replaceable clients/execution adapters, not authorities for what is important or where learned knowledge belongs. The destination is bridge-owned tools, model-context assembly and learning.

This draft makes that direction implementable. No runtime code has been removed or disabled by this PR. Existing knowledge, ObjectBox schema/IDs, signing identity, and the separately installed Operit application remain intact.

## Problem demonstrated in the current source

Completed replies can enqueue both MemoryAutoSaveCandidate and PendingReviewEntity. Review enqueue is nested under the original enableMemoryAutoUpdate gate. The review payload is limited to the first 2,000 characters of the final response and an empty contextSummary. Episode preflight retrieval requires a nonblank workspacePath. Skill revisions and installed SKILL.md packages are separate facilities.

Consequently, two writers can process the same interaction, while a saved lesson may never be retrieved for an ordinary server-management chat. This does NOT prove duplicate database rows or that last night's task exercised either path. See [the source inventory](01-current-boundaries.md).

The user's report of 17 persisted episodes and four completed empty passes proves storage/count access and scheduler execution for that observation window, not learning effectiveness.

## Ownership decisions

One authority owns model context and learned-knowledge policy for each conversation. Ownership is selected by the application/controller, scoped to the authenticated user, client, conversation and memory space; model text cannot choose it.

The final bridge controller consists of separate modules:

- Conversation controller: turn lifecycle, cancellations, sequencing, durable event IDs and model/tool loop.
- Context assembler: one ordered model request assembled from policy, user input, selected history, retrieved evidence and approved skills.
- Knowledge service: authoritative facts/corrections, provenance, memory versions, retrieval and explicit retention/retirement policy.
- Learning worker: consumes completed trajectories, proposes evidence-backed episodes and skill revisions, and submits them to acceptance policy.
- Skill service: versioned drafts/approved skills, applicability and publication. Saving an episode is NOT publishing a skill.
- Tool gateway: authoritative names, schemas, authorization, remote MCP connections, routing and execution receipts.
- Client adapters: Android UI, Android/Shizuku and PRoot executors, plus any later clients. They provide capabilities and authenticated context; they do not independently select memories or modify policy.
- Storage adapters: initially expose existing ObjectBox knowledge through a controlled interface, later support bridge storage/import without tying learning policy to Android.

One owner does not mean one giant module. These boundaries must be testable independently and must not recreate multiple competing policy engines.

## Meaning of bridge

This document means the future central controller, not merely the repository's local stdio MCP bridge in tools/mcp_bridge. A forwarding API proxy does not acquire context ownership by itself. The bridge repository/deployment and protocol must be identified before implementing its server-side portion; no endpoint or credentials are invented here.

## Priority and next implementation

1. Finish the narrowly demonstrated MCP activation/registration repair; do not start a broad Android path migration based on the disproven missing-assets/orphan-directory claims.
2. Implement the first boundary-extraction task in [the cutover plan](02-cutover-and-acceptance.md): one completion-event entry point and explicit ownership, preserving current behavior until the handoff is validated.
3. Implement evidence-rich review inputs and a trace from an actual completed task to subsequent context inclusion.
4. Move context/learning ownership to the bridge for the selected conversations, disable the competing Android paths, and then remove unreachable orchestration code.

PR #18 is open for D04b batching. It is not merged, cancelled or superseded by this document. Reassess it against the chosen storage adapter before spending more effort optimizing code that may be retired. PR #16 contains the separate build-85 device handoff; this design does not duplicate or replace its evidence record.

## Completion criteria

- Exactly one selected owner consumes each completed-turn event and commits learning decisions.
- Android adds no hidden memory/skill context in bridge-managed conversations.
- A deliberate user correction has versioned provenance and is not overwritten by a model summary.
- Replayed events do not create duplicate learning updates; tool side effects have their own explicit retry policy.
- Users can distinguish configured tools, reachable tools and tools exposed to this conversation.
- Existing memories remain accessible under the correct profile; no silent import of the separate Operit app's files.
- A real task can be traced from its evidence through accepted knowledge to a later model request, without leaking secrets into diagnostics.
- The old auto-save/review orchestration is removed only after its replacement passes the behavior and data-preservation checks.

No declaration of completion based only on green compilation, an empty queue, or the existence of episode rows.
