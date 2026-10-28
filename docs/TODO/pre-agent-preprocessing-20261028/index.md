# Task: Pre-agent preprocessing (scaled-down LLM pre-step before the main model)

Status: in progress

Goal: Add a bounded, advisory LLM preprocessing step that runs BEFORE the main model. It produces an intent -> framework -> tailored plan plus scoped retrieval queries, runs scoped retrieval through the existing `TaskEpisodeRetrieval` layer, and injects a bounded advisory block into the main model's system prompt via a synchronous hook that reads a pre-computed metadata block. Reuses the existing DeepSeek `default` model config and the proven `EnhancedAIService.callFunctionModel` route. No tool execution, no memory mutation, no permission grants, no invented capabilities.

Canonical repo / base branch / working branch / starting commit:
- Canonical repo: Mavaebrook/HandleIT-Android
- Base branch: main @ 17089b41b4f192968deeb0d36163333c53e222aa
- Working branch: feat/pre-agent-preprocessing
- Starting commit: 17089b41b4f192968deeb0d36163333c53e222aa

APK workflow / expected artifact: GitHub Actions only (never build/test on VPS, local machine, phone, or PRoot). Expected artifact: debug APK from the Android build workflow; record repo, full commit SHA, workflow + run URL/ID, successful run status, artifact name, and a file hash when available.

## Requirements and acceptance

| ID | Requirement | Source | Affected component | Acceptance check | Status/evidence |
|----|-------------|--------|--------------------|------------------|-----------------|
| R1 | Scaled-down LLM pre-step runs before the main model | handoff prompt | data/learning/PreAgentProvider.kt + call site | Pre-step invoked before the main call and emits an advisory block | planned |
| R2 | Intent -> framework -> tailored plan + scoped retrieval queries | handoff prompt | PreAgentProvider.kt | Model JSON parses into a structured plan; JVM test asserts parse | planned |
| R3 | Scoped retrieval delegates to TaskEpisodeRetrieval (no reinvented ranking) | handoff prompt + AGENTS.md no-fallback | PreAgentProvider.kt | Calls TaskEpisodeRetrieval ranking/applicability, does not re-implement | planned |
| R4 | Bounded and advisory; no tool execution / memory mutation / permission grants / invented capabilities | handoff prompt | PreAgentProvider.kt + hook | Output is text-only advisory block; no side effects | planned |
| R5 | Suggested tool IDs filtered through CharacterCardToolAccessResolver | handoff prompt | PreAgentProvider.kt | Tool IDs pass the resolver filter before surfacing | planned |
| R6 | Reuse MEMORY/DeepSeek config (no new key) | durable memory | PreAgentProvider.kt | Uses EnhancedAIService.callFunctionModel + existing `default` config | planned |
| R7 | Synchronous hook injects pre-computed block (mirrors architectPreflightBlock) | PromptHookRegistry is synchronous | core/chat/hooks/PreAgentSystemPromptHook.kt | Hook reads context.metadata and appends block; no suspend call inside hook | planned |
| R8 | Explicit failure contract, not silent fallback | AGENTS.md no-fallback | PreAgentProvider.kt | On failure emit a defined empty/advisory block; documented as a contract | planned |
| R9 | Bilingual prompts + pure-JVM test | FunctionalPrompts convention | PreAgentProvider.kt + PreAgentProviderTest.kt | EN/ZH selectors; JVM test passes without device | planned |
| R10 | Zero edits to InputProcessor.kt; independent of PR #20 | resolved design | — | No diff to InputProcessor.kt; branch based on main | planned |

## Constraints and preserved behavior

- Builds/tests only via GitHub Actions; never on VPS, local machine, phone, or PRoot (AGENTS.md).
- Preserve package identity `com.ai.assistance.operit` / `.debug`, licensing, existing data and ObjectBox IDs.
- Never modify `entity12` / `MemoryLedger`.
- Preserve the a9d4849 notification snapshot and the signing identity / cert SHA-256.
- Never print secrets.
- Branch naming per docs/doc-src/dev-core/CONTRIBUTING.md; python via venv; no PowerShell editing; use `rg` not the Search-files tool.
- Surgical changes only; no broad audit / migration / exhaustive test campaign.
- PR #20 (fix/surgical-learning-mcp-repair) remains the separate, simpler test candidate; this work must not collide with its 12 changed files.
- Prompt-injection caveat: fencing untrusted retrieved text inside the SYSTEM message reduces but does not eliminate the chance it is read as instructions (same caveat as ArchitectRulesHook).

## Architecture and execution

Integration is split because `PromptHookRegistry.dispatch` is synchronous and cannot host a `suspend` model call:

1. Async producer — `data/learning/PreAgentProvider.kt`:
   - Builds a bounded bilingual prompt (intent -> framework -> tailored plan + scoped retrieval queries) following the FunctionalPrompts `useEnglish` selector convention.
   - Calls the model via `EnhancedAIService.getInstance(context).callFunctionModel(...)` (the ReflectorCurator-proven route, reusing the DeepSeek `default` config).
   - Parses the JSON response via `extractJsonObject` / forced-STATUS_DRAFT idioms; applies `MAX_BLOCK_CHARS` and field-truncation bounds.
   - Runs scoped retrieval by delegating to `TaskEpisodeRetrieval` (rank/merge/applicability), no reinvented ranking.
   - Filters suggested tool IDs through `CharacterCardToolAccessResolver`.
   - Returns a bounded advisory block; on `CancellationException` rethrows, on other exceptions logs and returns an explicitly defined empty/advisory block (documented contract, not a "fallback").
2. Synchronous hook — `core/chat/hooks/PreAgentSystemPromptHook.kt`:
   - Gates on `stage == "compose_system_prompt_sections"`.
   - Reads `context.metadata["preAgentBlock"]` and appends it to the system prompt (mirrors `architectPreflightBlock` handling in ArchitectRulesHook).
   - `object PreAgentHookRegistrar { fun register(); fun unregister() }`.

`InputProcessor.kt` needs NO edit: the block is pre-populated into metadata by the call site, and the hook is dispatched by the existing registry.

## Decisions and open questions

- Decision: prompts live in the new `PreAgentProvider.kt` (following the FunctionalPrompts bilingual shape) rather than editing the 82 KB `FunctionalPrompts.kt`, to minimize blast radius.
- Decision: working branch is based on `main` so PR #20 stays independent and non-colliding.
- Open (unverified, to report honestly): device MEMORY->DeepSeek mapping; PR #20 device behavior; whether the PR-Check exit-1 and build ECONNRESET were transient; APK artifact availability for device testing. No merge/installation authorization exists.

## Documentation and verification

- Plan: this file, docs/TODO/pre-agent-preprocessing-20261028/index.md (numbered sub-docs added as work proceeds).
- CHANGELOG.md: additive entry at completion.
- README review: no user-facing setup change required (internal pipeline step); reason recorded here.
- Tests: pure-JVM `PreAgentProviderTest.kt` covering prompt selection, JSON parse, tool-ID filter, bounds, cancellation rethrow, and metadata-block injection through the synchronous hook.
- Verification boundary (honest): source review / compilation / automated tests / device testing / user acceptance are distinct; "unavailable testing means unverified, not passed".

## Resume checkpoint

- Done: PR/CI triage; AGENTS.md full read; file inventory; full reads of TaskPreflightProvider, TaskPreflightFormatter, ReflectorCurator, InputProcessor, PromptHookRegistry, ArchitectRulesHook, CharacterCardToolAccessResolver, TaskEpisodeModels, TaskContextSnapshotStore, TaskEpisodeRetrieval; design resolved to synchronous-hook + async-producer; branch created; this plan written.
- Next: implement PreAgentProvider + PreAgentSystemPromptHook + registrar + PreAgentProviderTest; wire the call site; add CHANGELOG; dispatch Actions-only CI; report PR #20 device status as unverified.
