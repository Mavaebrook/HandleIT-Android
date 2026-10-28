# Cutover contract and implementation slices

## First Android implementation task

Extract completion-to-learning orchestration from EnhancedAIService into one interface, provisionally LearningCoordinator. This is a bounded first PR, not a new Android memory engine.

Inputs must include authenticated scope, stable conversation/turn IDs, completed/cancelled status, user request and corrections, tool-execution references, observed outcomes and the assistant response. Evidence must be bounded/redacted at the capture boundary. Do not substitute only the first 2,000 characters of the assistant's answer.

Separate data collection, review eligibility and persistence. Make the intended memory setting explicit; the new policy must not inherit the old enableMemoryAutoUpdate gate accidentally. User opt-out remains meaningful. Subtasks need parent attribution/deduplication before becoming independent learning events.

Initially characterize and preserve existing execution behavior through an adapter. Do not turn this extraction PR into immediate dual delivery to Android and bridge. The migration is not complete while both old policies remain the active implementation.

Tests must exercise the real finalization entry point and coordinator contract, including memory opt-out, subtask handling, cancellation and duplicate delivery. Tests of a formatter or injected Boolean alone do not establish integration.

## Structured bridge contract

A versioned request envelope carries client identity, conversation/turn IDs, memory/profile scope, trusted workspace identity, user input, user-configured persona/settings, device capabilities and structured history references. Prompt strings must not be reparsed to recover those boundaries.

The bridge returns streaming content and execution requests with stable IDs. A completion/evidence envelope records tool identity/schema version, sanitized arguments or secure references, result status, verification evidence, user corrections and timestamps.

The API proxy, if used, must preserve these envelopes or be paired with an explicit controller API. Arbitrarily rewriting HandleIT's already assembled prompt would preserve ambiguity and risk duplicate injection.

The knowledge service returns a context manifest: selected memory/skill IDs and versions, applicability, source/provenance, and reasons for inclusion/exclusion. Diagnostics expose that manifest without dumping private contents. Authoritative project policy and user corrections must remain distinguishable from untrusted retrieved material and tool output.

## Ownership transfer

1. Assign one owner to each conversation and memory namespace; capture its generation/epoch in event acknowledgements.
2. Quiesce the old writer for that namespace. Inventory pending auto-save/review rows and record which event IDs it has already processed.
3. Export/import only the selected records through supported interfaces with stable IDs/provenance. Preserve the ObjectBox model and historical data; do not drop or repurpose entity IDs.
4. Activate the new owner only after it acknowledges the cutover checkpoint. The old process must reject later writes for the transferred generation.
5. Disable Android automatic memory enqueue/drain, learned-context injection and direct skill publication for bridge-owned conversations. Device permissions and tool-execution safety remain enforced locally.
6. Remove unused orchestration and UI controls after callers have migrated; retain only necessary data-read/compatibility adapters.

If the bridge is unavailable, show unavailability and, if implemented, retain a bounded durable event outbox. Do not silently reactivate legacy learning or run a second model loop. Retrying learning delivery uses idempotency keys and atomic commits; do not promise exactly-once remote tool side effects.

Two independently running installed apps are separate clients. Share memory only through an explicitly selected namespace. Neither app may auto-adopt the other's Download tree.

## Preserve useful functions without preserving competing authority

- UI: messages, streaming, cancellation, approvals and access to explicit memory corrections.
- Android adapter: filesystem/device/Shizuku operations, permissions and execution receipts.
- PRoot adapter: terminal sessions and process execution; it does not choose memory policy.
- Storage adapter: legacy records, supported querying and migration accounting.
- Conversation history: context-window management with explicit provenance and controller ownership.

General memory, task episodes and approved skills have different purposes. Unify policy and access, not their meaning. Indexing may be a separate worker but cannot become another authority for relevance, deletion or promotion.

## Validation and exit gates

- One real user task produces a traceable completion event containing actual tool/correction evidence.
- Duplicate delivery, restart and ownership transfer do not duplicate accepted updates.
- A follow-up task, including one without an Android workspace binding, selects the appropriate scoped lesson and records its inclusion in the outgoing model request.
- Wrong-profile/wrong-project records are excluded; disabled tools and unapproved skill revisions are not exposed.
- Old Android auto-save, old review drain and prompt hooks are proven inactive for bridge-owned conversations, including manual/tool entry points.
- Conversation compression still works without storing a second independent set of long-term facts.
- Existing populated-device data remains readable; source-only/JVM results are not labelled upgrade preservation.
- Android builds/tests use the hosted workflows. No merge, deployment or device install is part of this design PR.

Before implementing server code, identify the real bridge repository/deployment and supported client protocol. This is the remaining environment dependency; it is not a reason to invent a second bridge implementation inside the Android app.
