# Step 1 — G02 execution-route audit (audit only, no enforcement)

Status: Audit complete. Enforcement NOT implemented. G02 checkbox remains unchecked.

## Scope

This document inventories every route by which a shell command or process execution can be triggered in this codebase, and records whether the shared policy interception hook `AIToolHandler.checkToolInterception` runs on that route.

It does NOT implement enforcement. No hook, policy engine or shell-path guard is added by this document, and the G02 checkbox in [index.md](index.md) stays unchecked.

It distinguishes app-runtime routes from external and remote agent routes, because the two have different trust boundaries and only the first is reachable from the app's own tool dispatch.

## The central interception mechanism

The single shared interception point is `AIToolHandler.checkToolInterception(tool: AITool): AIToolHookDecision` at `app/src/main/java/com/ai/assistance/operit/core/tools/AIToolHandler.kt:100`.

It iterates the registered `AIToolHook`s, calls `onToolCallIntercept` on each, and returns `AIToolHookDecision.Block(reason)` on the first block. It also returns `Block` when a hook throws, so a failing hook fails closed rather than silently allowing the call.

It is called from exactly three places:

- `AIToolHandler.executeTool` at `AIToolHandler.kt:363`, with the check at `:365`
- `AIToolHandler.executeToolAndStream` at `AIToolHandler.kt:419`, with the check at `:421`
- `ToolExecutionManager.executeInvocations` at `app/src/main/java/com/ai/assistance/operit/api/chat/enhance/ToolExecutionManager.kt:577`, which is the chat path's own check and is separate from `executeTool`

`AIToolHook` and `AIToolHookDecision` are defined in `app/src/main/java/com/ai/assistance/operit/core/tools/AIToolHook.kt`. `AIToolHookDecision` is a sealed class with `Allow` and `Block(reason)`.

Only two hook implementations are registered: `ToolPkgToolLifecycleBridge` and the workspace hook session in `MessageProcessingDelegate`. Neither enforces project constraints, so neither blocks a build or a test on the VPS.

## Route inventory

| Route | file:symbol:line | Caller(s) | Interception runs? | Notes |
| --- | --- | --- | --- | --- |
| Tool dispatch (executeTool) | `AIToolHandler.kt:executeTool:363` | tool callers | YES | Central path; check at `:365` |
| Tool dispatch (streaming) | `AIToolHandler.kt:executeToolAndStream:419` | tool callers | YES | Check at `:421` |
| Chat tool invocation | `ToolExecutionManager.kt:executeInvocations:504` (check `:577`) | `EnhancedAIService` | YES | Separate check, not via `executeTool` |
| UI shell tools (input/uiautomator/cat) | `DebuggerUITools.kt:executeUiShellCommand:39` -> `AndroidShellExecutor.executeShellCommand` | `ToolGetter.getUITools` -> `AIToolHandler` | YES (when invoked as a tool) | Registered executors go through dispatch |
| Debugger FS tools (ls/cat/mv/rm/mkdir) | `DebuggerFileSystemTools.kt` (many `executeShellCommand` calls) | `ToolGetter.getFileSystemTools` | YES (tool path) | |
| Temp-file cleanup rm | `DebuggerUITools.kt:clickElementWithUiautomator:1091` (exec `:1319`) | `DebuggerUITools:345`, `RootUITools:254` | YES (tool path) | Raw `Runtime.exec` inside a tool executor; bypasses `executeUiShellCommand` but still under dispatch |
| getprop cpu abi | `StandardDeviceInfoToolExecutor.kt:invoke:22` (`ProcessBuilder` `:84`) | `ToolGetter.getDeviceInfoToolExecutor` | YES | Fixed argv, read-only |
| Standard shell (no operators) | `StandardShellExecutor.kt:executeCommand:40` (exec `:54`) | `AndroidShellExecutor.kt:100` | NO | Below tool layer |
| Standard shell via sh -c | `StandardShellExecutor.kt:executeWithShell:104` (exec `:108`) | `StandardShellExecutor.kt:50` | NO | |
| Standard process | `StandardShellExecutor.kt:startProcess:99` -> `StandardShellProcess:223` (exec `:225`/`:227`) | `AndroidShellExecutor.kt:120` | NO | |
| Root cmd via exec | `RootShellExecutor.kt:executeCommandWithExec:301` (exec `:307`) | `RootShellExecutor.kt:448` in `executeCommand:356` | NO (unless caller is a tool) | |
| Root SHELL-identity cmd | `RootShellExecutor.kt:executeCommand:356` (exec `:386`) | `AndroidShellExecutor:100`, `RootAuthorizer:400`, `AdminShellExecutor:172`, `RootActionListener:213/236/252` | NO | |
| Root interactive process | `RootShellExecutor.kt:startProcess:477` -> `ExecRootShellProcess:555` (exec `:556`) | `AndroidShellExecutor:120`, `RootActionListener:150`, `DebuggerActionListener:184/207` | NO | |
| Root probe which su | `RootAuthorizer.kt:isDeviceRooted:188` (exec `:240`) | `ShizukuDemoViewModel:90`, `RootAuthorizer:144` | NO | Detection only, fixed argv |
| KernelSU probe | `RootAuthorizer.kt:checkKernelSu:265` (exec `:270`) | `RootAuthorizer:131` `checkRootStatus` | NO | Fixed argv |
| exec-su probe | `RootAuthorizer.kt:checkExecSuAccess:302` (exec `:307`) | `RootAuthorizer:131` | NO | Fixed argv |
| Root permission request | `RootAuthorizer.kt:requestRootPermission:333` (exec `:340`) | `ShizukuDemoViewModel:101` <- `ShizukuDemoScreen:382` (UI) | NO | Fixed argv |
| Port kill | `PortProcessKiller.kt:killListeners:9` (exec `:32`) | `MCPBridge.kt:332` `startBridge:298` <- `MCPStarter:137` | NO | `sh -c kill -9` on arbitrary PIDs |
| Shower server start/stop | `showerclient/ShowerServerManager.kt:ensureServerStarted:31`, `stopServer:115` | `OperitShowerShellRunner:16` -> `AndroidShellExecutor:22`; `AutoGlmViewModel:62`, `PhoneAgent:195/297/1189` | NO | `pkill -f`, `app_process` |
| MCP bridge deploy/start | `MCPBridge.kt:deployBridge:205`, `startBridge:298` | `MCPStarter:131/137` | NO (terminal commands) | `copy_file` inside does go through `executeTool` |
| MCP plugin deploy commands | `MCPDeployer.kt:executeDeployCommands:265` | `MCPDeployer:206` <- `deployPluginWithCommands:94` <- `MCPStarter:216/568`, `MCPDeployViewModel:193`, `MCPConfigScreen:517` | NO (terminal commands) | `copy_file` at `:327` goes through `executeTool` |
| MCP runtime-ready probe | `MCPLocalServer.kt:isPluginRuntimeReady:954` | `MCPStarter:185/555`, `MCPConfigScreen:374/1259/1267` | YES | Uses `toolHandler.executeTool` |
| Terminal toolbox (user-typed) | `ShellCommandManager.kt:executeCommand:169` -> `AndroidShellExecutor:173` | `ShellExecutorScreen:129` (UI) | NO | Direct UI -> shell |
| App-permissions / process-limit screens | `AndroidShellExecutor.executeShellCommand` | `AppPermissionsScreen:168/195/728/1144/1148`, `ProcessLimitRemoverScreen:83/122/124/250` | NO | Direct UI -> shell |
| PhoneAgent ActionHandler | `PhoneAgent.kt:ActionHandler:762` (calls `ToolImplementations` tap/setInputText/swipe/pressKey/captureScreenshotBitmap at `:848/1040/1081/1102/1116/1130`) | `AutoGlmViewModel:111-120` (`ToolGetter.getUITools` -> `ActionHandler`) | NO | Critical gap: UI tools invoked directly, never through `AIToolHandler` |
| Root action listener | `RootActionListener.kt:150/213/236/252` | internal listeners | NO | `getevent`/`dmesg`/`ps` |
| Debugger action listener | `DebuggerActionListener.kt:184/207` | internal listeners | NO | window/activity monitors |

## Additional routes discovered beyond the seven originally cited files

The original G02 wording named seven files. The inventory above found more, and they are recorded here so the gap is not understated:

- `ShowerServerManager`, which starts and stops a separate `app_process` server
- `PhoneAgent.ActionHandler`, which invokes UI tools directly
- `ToolExecutionManager`, a second dispatcher, covered by its own check
- `MCPBridge` and `MCPDeployer` terminal commands
- `PortProcessKiller`
- `ShellCommandManager` together with `ShellExecutorScreen`
- `AppPermissionsScreen` and `ProcessLimitRemoverScreen`
- `RootActionListener` and `DebuggerActionListener`
- the raw `rm` inside `DebuggerUITools`

## App runtime versus external and remote agents

App runtime: every route in the table above executes inside the app process through `Runtime.exec` or `ProcessBuilder`. The command string is built in-process and the process is a child of the app.

External and remote: `MCPBridge` runs a local Node.js bridge over a socket and exposes `MCPBridgeClient.callTool` and `MCPBridge.callTool`; `RemoteMcpRuntimeSession.callTool` reaches remote MCP servers; `ShowerServerManager` runs a separate `app_process` server with a Binder handoff. Commands that reach these are NOT hook-gated.

No literal VPS, external-agent or remote-executor strings exist in `app/src/main/java`. The only "bridge" hits are the local MCP stdio bridge, so there is no hidden remote executor to account for.

## Test coverage

`grep -rln "checkToolInterception|AIToolHookDecision|AIToolHook" app/src/test app/src/androidTest` returns NO matches.

Zero tests exercise the hook and interception mechanism, and zero tests exercise any shell-execution route. The mechanism is therefore unverified by the test suite as well as unenforced in policy.

## Bounded follow-up enforcement plan (NOT implemented)

This plan is recorded for a later, separately authorized job. Nothing below is implemented by this document.

- Add a project-constraint policy hook implementing `AIToolHook.onToolCallIntercept` that blocks tool-dispatched build and test commands, meaning `gradle`, `./gradlew`, `npm run build` and `pnpm build`, and blocks writes to protected paths, returning `AIToolHookDecision.Block` with a recorded reason.
- Acceptance: a tool-dispatched attempt to run a build is blocked at dispatch with a recorded reason, and a unit test asserts the `Block` decision.
- For each uncovered direct route, meaning `StandardShellExecutor`, `RootShellExecutor`, `RootAuthorizer`, the terminal toolbox, `PortProcessKiller`, the `DebuggerUITools` raw exec, `PhoneAgent.ActionHandler`, `ShowerServerManager`, the `MCPBridge` and `MCPDeployer` terminal commands, `ShellCommandManager`, and `AppPermissionsScreen` and `ProcessLimitRemoverScreen`, either route it through a shared guard or record it as an accepted gap with a reason.
- Acceptance: a direct shell-execution attempt through each listed path is either blocked or explicitly recorded as an uncovered gap, and the guard is exercised by a test that asserts the block decision.
- Do NOT claim a hard gate until every path is covered.

## Bottom line

`checkToolInterception` runs on the tool-dispatch paths and the chat invocation path only.

Every raw `Runtime.exec` and `ProcessBuilder` site sits below the tool layer, so interception applies only when the caller happens to be a registered `ToolExecutor` invoked through dispatch.

The `PhoneAgent` and `ActionHandler` path, the Shower server path, the MCP bridge and deployer terminal paths, `PortProcessKiller`, and all direct UI to `AndroidShellExecutor` screens bypass interception completely.

G02 remains unchecked.
