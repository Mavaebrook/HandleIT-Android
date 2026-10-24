---
repository: https://github.com/Mavaebrook/HandleIT-Android
created: 2026-10-21
status: in-progress
branch: fix/project-scoped-architect-policy
---

# D05 project isolation + bounded G02 enforcement slice — plan and checkpoint (2026-10-21)

## Job

Create `fix/project-scoped-architect-policy` from freshly read `origin/main` (`5b3277cf844c0c198307c60ac0e9c8f79f1b88bf`). Deliver:

- **Priority 1 — D05**: stop injecting HandleIT-specific canonical-repo / build-location / protected-path instructions into every project's system prompt and summary. Scope those requirements to the actual HandleIT project using trusted application/workspace configuration, not model text, retrieved memories, a claimed repo string, or a directory basename heuristic. Both the prompt hook and the summary hook must agree.
- **Priority 2 — G02 first slice**: only after identity/policy authority is established, add a shared project-constraint decision at the existing `AIToolHandler.checkToolInterception` routes. Enforce the authoritative HandleIT constraint against an agent attempting local/VPS/phone/PRoot Gradle/build/test execution and protected-path mutation through covered tools; keep GitHub-hosted workflow dispatch permitted.

This job does NOT authorize expansion into direct UI, root/shower/process/PhoneAgent/MCP deployment rewrites or an external-agent security system. G02 stays unchecked until the full route inventory is enforced.

## Discovery evidence (re-verified against origin/main 5b3277cf)

### D05 — the defect

`ArchitectRulesProvider.isDevelopmentTask` (`app/src/main/java/com/ai/assistance/operit/core/chat/hooks/ArchitectRulesHook.kt:47-50`) returns true iff any `workspacePath` is non-blank:

```kotlin
fun isDevelopmentTask(workspacePath: String?, metadata: Map<String, Any?>): Boolean {
    val resolved = workspacePath ?: metadata["workspacePath"] as? String
    return !resolved.isNullOrBlank()
}
```

Both `ArchitectRulesSystemPromptHook.onEvent` (`:70-91`, stage `compose_system_prompt_sections`) and `ArchitectRulesSummaryHook.onEvent` (`:97-109`, stage `before_prepare_summary_prompt`) gate on this single predicate, so every workspace task in every project receives the HandleIT rules block (`RULES_BLOCK_EN`/`RULES_BLOCK_ZH`, `:17-37`). This is finding R11.

### D05 — existing identity/context plumbing (what is actually available)

- `workspacePath` is the only per-task project-ish coordinate. It flows chat record `chat.workspace` (`services/ChatServiceCore.kt:154`) → `EnhancedAIService` options → `ConversationService.prepareConversationHistory` (`:501`) → `SystemPromptConfig.getSystemPromptWithCustomPrompts` (`:577`) → `PromptHookContext.metadata["workspacePath"]` (`SystemPromptConfig.kt:611`, `ConversationService.kt:534`). It is a free-form filesystem path, not a canonical project identity.
- `PromptHookContext.metadata` (`core/chat/hooks/PromptHookRegistry.kt:22`) is the extension seam; `SystemPromptConfig.getSystemPromptWithCustomPrompts` merges `hookMetadata` last (`SystemPromptConfig.kt:599,632`), so a caller can inject a project-identity key without changing the signature.
- `projectScope` exists as a defined-but-unpopulated seam: `ObservationRecord.projectScope` (`data/learning/ObservationRecord.kt:30`), `ObservationScope.projectScope` (`data/learning/ToolObservationProducer.kt:15`), `TaskContextSnapshotStore` (`:78,87,96`), `TaskPreflightProvider` (`:85,102`), `PrerequisiteEvaluator` (`:50`). The only production write site is `EnhancedAIService.kt:2326-2336`, which sets `projectScope = ""` with a documented limitation comment. Nothing sets a real value today.
- There is **no** `ProjectContext` class, **no** DI framework (manual singletons only), and **no** build-config constant naming the canonical repo. `GITHUB_PROJECT_URL` (`ui/features/about/screens/AboutScreen.kt:61`) is a `private const` in a UI file, not reachable from `core/`. `UpdateManager` hardcodes `Pair("Mavaebrook", "HandleIT-Android")` (`data/updates/UpdateManager.kt:174`) parsed from `R.string.about_website`, not a shared constant.

**Conclusion: trusted project identity is unavailable in production today.** Per the job rules, D05 therefore implements only a supportable additive context seam and reports the unconnected production prerequisite. It must not call D05 done with all rules accidentally disabled.

### G02 — the interception mechanism (from 07-step1-g02-execution-route-audit.md, re-verified)

- Single shared point: `AIToolHandler.checkToolInterception(tool: AITool): AIToolHookDecision` (`core/tools/AIToolHandler.kt:100-117`). Iterates registered `AIToolHook`s, returns `Block(reason)` on first block, and **fails closed** (returns `Block`) when a hook throws (`:105-110`).
- Called from exactly three places: `executeTool` (`:365`), `executeToolAndStream` (`:421`), and `ToolExecutionManager.executeInvocations` (`api/chat/enhance/ToolExecutionManager.kt:577`, which passes the proxy-unwrapped target tool via `resolveToolTarget`, `:67-88`).
- `AIToolHook.onToolCallIntercept(tool: AITool)` (`core/tools/AIToolHook.kt:21`) receives **only** the `AITool` (name + parameters). There is **no** chat/workspace/project context parameter. `AITool` is `data class AITool(name, parameters, description)` (`data/model/AITool.kt:12-16`).
- Only two hook implementations are registered: `ToolPkgToolLifecycleBridge` and the per-message `WorkspaceToolHookSession` (which does not implement `onToolCallIntercept`). Neither enforces project constraints.
- Zero tests exercise the hook mechanism (`grep` over `app/src/test` and `app/src/androidTest` returns no matches).

### G02 — covered tool routes (dispatch-time, reachable by a hook)

| Tool name | Registration | Executor | Reads |
| --- | --- | --- | --- |
| `execute_shell` | `ToolRegistration.kt:270` | `StandardShellToolExecutor.invoke` → `AndroidShellExecutor.executeShellCommand(command)` | `command` |
| `execute_in_terminal_session` | `ToolRegistration.kt:318` | `StandardTerminalCommandExecutor.executeCommandInSession` → `terminal.executeCommandFlow` | `command`, `session_id` |
| `execute_in_terminal_session_streaming` | `ToolRegistration.kt:331` | `StandardTerminalCommandExecutor.executeCommandInSessionStream` | `command`, `session_id` |
| `execute_hidden_terminal_command` | `ToolRegistration.kt:354` | `StandardTerminalCommandExecutor.executeHiddenCommand` → `terminal.executeHiddenCommand` | `command`, `executor_key` |

These four are the only tool-dispatched routes that carry a free-form `command` string. They are the honest scope of a first G02 slice.

### G02 — routes that remain UNGUARDED (from the audit; not covered by this slice)

Every raw `Runtime.exec`/`ProcessBuilder` below the tool layer, and every direct UI→shell path, bypasses `checkToolInterception`:

- `StandardShellExecutor.executeCommand`/`executeWithShell`/`startProcess`; `RootShellExecutor.*`; `RootAuthorizer.*`; `PortProcessKiller.killListeners`; `ShowerServerManager.*`; `MCPBridge.deployBridge`/`startBridge`; `MCPDeployer.executeDeployCommands`; `ShellCommandManager.executeCommand`; `AppPermissionsScreen`/`ProcessLimitRemoverScreen`; `PhoneAgent.ActionHandler` (UI tools invoked directly); `RootActionListener`/`DebuggerActionListener`; raw `rm` in `DebuggerUITools`.
- External/remote executors: `MCPBridgeClient.callTool`, `RemoteMcpRuntimeSession.callTool`, `ShowerServerManager` (`app_process` + Binder).

These are recorded as UNGUARDED, not "accepted" on the user's behalf. G02 stays unchecked.

## Design

### D05 — typed project identity and a single scope decision

1. Add a small pure value type `ProjectIdentity` (in `core/chat/hooks/` or a new `core/project/` package) with a `kind` enum: `HANDLEIT`, `OTHER`, `UNKNOWN`. It is constructed only from trusted application/workspace configuration, never from model text, retrieved memory, a claimed repo string, or a directory basename.
2. Add a pure `ProjectPolicyScope` decision function that maps `(ProjectIdentity, workspacePath)` to a scope: `HANDLEIT_DEVELOPMENT`, `GENERIC_DEVELOPMENT`, `NONE`. Both `ArchitectRulesSystemPromptHook` and `ArchitectRulesSummaryHook` call this **same** function, so prompt and summary agree by construction.
3. Split the rules block: HandleIT-specific requirements (canonical repo, build-location, protected paths, invariants) are emitted only for `HANDLEIT_DEVELOPMENT`. A separate, optional generic preflight/memory block remains available for `GENERIC_DEVELOPMENT` so fixing scope does not silently disable valid preflight for other projects.
4. The identity is carried through `PromptHookContext.metadata` under a single typed key (e.g. `"projectIdentity"`), populated from `hookMetadata` at the `SystemPromptConfig`/`ConversationService` seam. Because production does not yet supply a trusted identity, the seam is additive and the production wiring is reported as an unconnected prerequisite.
5. **No accidental disable**: when identity is `UNKNOWN`, the decision is explicit and documented. The plan records the exact behavior chosen (see "Open decision" below) so the rules are never silently dropped for HandleIT while also never leaking to other projects.

### G02 — pure policy seam at the existing interception point

1. Add a pure `ProjectConstraintPolicy` that takes a typed `ToolInvocationContext` (tool name + parameters + the trusted `ProjectIdentity`) and returns `Allow` or `Deny(reasonCode)`.
2. A thin `AIToolHook` implementation (`ProjectConstraintHook`) adapts the existing `onToolCallIntercept(tool: AITool)` to the policy. Because the hook signature carries no project context, the trusted identity must be supplied through a typed, non-global carrier (see "Open decision" below) — not a global singleton task policy.
3. Denial records a bounded reason code (e.g. `LOCAL_BUILD_FORBIDDEN`, `PROTECTED_PATH_MUTATION`) without logging full commands/arguments or secrets.
4. Cancellation propagates: the hook is synchronous and returns a decision; the streaming path already returns `return@flow` on `Block`, so cancellation semantics are preserved.
5. The policy is applied **only** when the trusted identity is `HANDLEIT`; other projects are unaffected.

## Resolved decisions

- **D05 UNKNOWN-identity behavior — RESOLVED.** `ProjectPolicyScope.resolve` maps a workspace with `UNKNOWN` identity to `GENERIC_DEVELOPMENT` (generic preflight preserved, HandleIT rules suppressed), and reserves `NONE` strictly for "no workspace at all" (casual chat). This satisfies two requirements at once: an unestablished identity can never grant HandleIT authority, and fixing scope does not silently disable valid preflight for other projects. `HANDLEIT` + workspace → `HANDLEIT_DEVELOPMENT` (rules + preflight); `OTHER` + workspace → `GENERIC_DEVELOPMENT` (preflight only).
- **D05 summary-hook plumbing — RESOLVED.** The summary hook previously never received `workspacePath` (it was absent from `baseSummaryMetadata`), so its gate was dead code. `workspacePath` is now threaded from the chat record through `AIMessageManager.summarizeMemory` → `EnhancedAIService.generateSummary`/`generateSummaryFromPromptTurns` → `ConversationService.generateSummaryFromPromptTurns` and placed in `baseSummaryMetadata`. Both hooks now call the same `ArchitectRulesProvider.resolveScope` on real data.
- **G02 trusted-context carrier — BLOCKED (see G02 section below).**

## Acceptance criteria

- Meaningful hosted JVM regressions for: HandleIT vs unrelated projects vs missing/ambiguous identity; project switching does not leak rules/policy; summary and prompt use the same scope decision; missing provenance/memory cannot grant authority.
- For any G02 implementation: exercise the real dispatcher integration as well as pure allow/block examples; prove the covered build attempt is stopped and the allowed hosted-workflow path still works; enumerate remaining bypass paths from the audit.
- G02 stays unchecked until the full route inventory is enforced.
- No false G02 completion or lower-trust-message-boundary claim.

## Checkpoint

Owner: VPS orchestrator via the bridge, at Teddy's explicit request.
State: D05 + F1 IMPLEMENTED (code + tests; CI running, not yet green). G02 BLOCKED at design checkpoint (see below).
Exact commit: `217fcff58a5941bcf3b11efa877293410f87c8f9` (F1 identity-from-git-config, parent `d680292fb8cb0f429ce232635a8f73aadffe7ae7`).
Next: verify the hosted PR Check is green on `217fcff5`; record CI evidence; post the evidence comment; await Argon sign-off before merge. G02 requires a trusted invocation-context carrier that does not exist (see G02 section).
Blockers: (1) RESOLVED — the unconnected production prerequisite is now wired: `ConversationService` populates `ProjectIdentity.METADATA_KEY` from `ProjectIdentity.resolveFromWorkspace(workspacePath)` in both the summary and system-prompt hook metadata, so the HandleIT identity is established from the workspace git remote rather than any prompt claim. (2) G02 cannot be made non-bypassable from existing dispatch/context APIs (see G02 section).

## G02 — first slice: BLOCKED at design checkpoint

### Why the slice cannot be shipped as a hard gate

The job requires a "bounded non-bypassable decision" at the existing `AIToolHandler.checkToolInterception` routes, using "a pure policy seam and typed/trusted invocation context." Two facts block that:

1. **The hook signature carries no invocation context.** `AIToolHook.onToolCallIntercept(tool: AITool)` (`core/tools/AIToolHook.kt:21`) receives only `AITool(name, parameters, description)`. It has no chat id, no workspace, no project identity. A policy hook therefore cannot know whether the current task is the HandleIT project — the exact authority D05 establishes. Enforcing "HandleIT constraints" without that authority would either (a) apply HandleIT restrictions globally (forbidden), or (b) guess from the command text (a claimed repo string / directory heuristic, forbidden).

2. **`AIToolHandler` is a process-wide singleton with no per-invocation context.** `AIToolHandler.getInstance(context)` (`AIToolHandler.kt:36-41`) is shared across all chats. `executeTool`/`executeToolAndStream` (`:363`, `:419`) have no chat/workspace parameter. `ToolExecutionManager.executeInvocations` (`:504`) has `callerChatId` but no project identity, and it calls the same singleton. A global singleton task policy is explicitly forbidden by the job.

A bounded, non-bypassable carrier would require either extending `AIToolHook` with a context-bearing overload wired at all three call sites (a signature change to a shared interface used by `ToolPkgToolLifecycleBridge` and `WorkspaceToolHookSession`), or threading a typed `ToolInvocationContext` through `AIToolHandler` and `ToolExecutionManager`. Both are larger than "a pure policy seam" and touch shared dispatcher code that other in-flight jobs (bridge #46/#47, Codex) may depend on. Per the job: "If a bounded non-bypassable decision is not possible from existing dispatch/context APIs, stop that slice at a concrete design/blocked checkpoint while delivering D05; do not ship a pretend hard gate."

### What a future G02 slice would need (design, not implemented)

- A typed `ToolInvocationContext(projectIdentity: ProjectIdentity, chatId: String?, workspacePath: String?)` carried from the chat dispatch site into the interception point — not a global.
- A pure `ProjectConstraintPolicy.decide(context, tool): Decision` returning `Allow` or `Deny(reasonCode)` with bounded codes (`LOCAL_BUILD_FORBIDDEN`, `PROTECTED_PATH_MUTATION`), never logging full commands/arguments/secrets.
- Application only when `context.projectIdentity.kind == HANDLEIT`; other projects unaffected.
- Coverage of the four tool-dispatched command routes (`execute_shell`, `execute_in_terminal_session`, `execute_in_terminal_session_streaming`, `execute_hidden_terminal_command`) plus an explicit UNGUARDED list for every raw-exec and direct-UI route from `07-step1-g02-execution-route-audit.md`.

### Remaining bypass paths (UNGUARDED, from the audit — not "accepted" on the user's behalf)

`StandardShellExecutor.*`, `RootShellExecutor.*`, `RootAuthorizer.*`, `PortProcessKiller.killListeners`, `ShowerServerManager.*`, `MCPBridge.deployBridge`/`startBridge`, `MCPDeployer.executeDeployCommands`, `ShellCommandManager.executeCommand`, `AppPermissionsScreen`/`ProcessLimitRemoverScreen`, `PhoneAgent.ActionHandler`, `RootActionListener`/`DebuggerActionListener`, raw `rm` in `DebuggerUITools`, and the external/remote executors (`MCPBridgeClient.callTool`, `RemoteMcpRuntimeSession.callTool`, `ShowerServerManager`). G02 stays unchecked until the full route inventory is enforced.

## CI evidence

All runs are GitHub-hosted Actions. No build or test was run on the VPS or any local machine.

| Item | Commit | PR Check (`pr-check.yml`) |
| --- | --- | --- | --- |
| D05 project-scoped architect rules (F1 identity-from-git-config) | `217fcff58a5941bcf3b11efa877293410f87c8f9` | running — https://github.com/Mavaebrook/HandleIT-Android/actions/runs/40089287238 |

The `pr-check.yml` workflow fires on `pull_request` (opened/synchronize/reopened/edited/ready_for_review) and runs the fast checks, then `android_build` and `android_tests` as reusable `workflow_call` jobs, finishing with the aggregate "Candidate checks" job. It was triggered automatically by the push to the PR head; the run above is RUNNING at the time of this checkpoint and is not yet green. CI verifies compilation and the JVM unit tests only, not device behavior. D05/F1 remains unchecked until this run is green.
