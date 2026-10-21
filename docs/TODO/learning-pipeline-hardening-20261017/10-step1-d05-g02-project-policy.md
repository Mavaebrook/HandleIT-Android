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

## Open decisions (to resolve during implementation)

- **D05 UNKNOWN-identity behavior**: whether `UNKNOWN` maps to `GENERIC_DEVELOPMENT` (rules suppressed, preflight kept) or to a documented "legacy" mode. The job forbids calling D05 done with all rules accidentally disabled, so the chosen behavior must be explicit, tested, and recorded here.
- **G02 trusted-context carrier**: `onToolCallIntercept` has no context parameter. Options: (a) extend `AIToolHook` with an overload that receives a typed context, wired at the three call sites; (b) a per-invocation context passed through `ToolExecutionManager`/`AIToolHandler`. A global singleton is explicitly forbidden. If neither is bounded and non-bypassable from existing APIs, G02 stops at a design/blocked checkpoint.

## Acceptance criteria

- Meaningful hosted JVM regressions for: HandleIT vs unrelated projects vs missing/ambiguous identity; project switching does not leak rules/policy; summary and prompt use the same scope decision; missing provenance/memory cannot grant authority.
- For any G02 implementation: exercise the real dispatcher integration as well as pure allow/block examples; prove the covered build attempt is stopped and the allowed hosted-workflow path still works; enumerate remaining bypass paths from the audit.
- G02 stays unchecked until the full route inventory is enforced.
- No false G02 completion or lower-trust-message-boundary claim.

## Checkpoint

Owner: VPS orchestrator via the bridge, at Teddy's explicit request.
State: PLAN ONLY. Branch created from `origin/main` at `5b3277cf844c0c198307c60ac0e9c8f79f1b88bf`. No source changes yet.
Exact commit: `5b3277cf844c0c198307c60ac0e9c8f79f1b88bf` (branch point; no implementation commits yet).
Next: implement D05 typed identity + scope decision and tests; then evaluate the G02 trusted-context carrier and either implement the bounded slice or record a blocked checkpoint.
Blockers: trusted project identity is unavailable in production (no `ProjectContext`, no DI, no build-config constant); `projectScope` is always `""`. G02 hook signature carries no invocation context.
