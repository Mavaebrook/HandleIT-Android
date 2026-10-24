---
repository: https://github.com/Mavaebrook/HandleIT-Android
created: 2026-10-24
status: in-progress
branch: feat/stage-d-g02-d06
base: d945330fca1d679a700a797f3a5f11c041d4c049
---

# Stage D close-out — G02 hard project-constraint gate + D06 verification (2026-10-24)

## 1. Status

in-progress. Information gathering and design resolution are COMPLETE (~95%); zero product code has
been written at plan-write time. This is the versioned plan required by the handleit-architect skill
§3 before editing product code. The controlling directive is the formal written work order "DIRECTIVE:
Close Out Stage D (G02 + D06) on Mavaebrook/HandleIT-Android".

## 2. Goal

Implement, unit-test, CI-verify and merge **G02** — a hard project-constraint gate at tool-dispatch
time via the existing `AIToolHandler.checkToolInterception` hook, plus an audited inventory of the
direct shell-execution paths — and confirm **D06** (provenance, sanitization, length cap on episodes
injected into system prompts), then close out Stage D of
`docs/TODO/learning-pipeline-hardening-20261017/`.

## 3. Canonical repo / base / working branch / starting commit

- Canonical repo: https://github.com/Mavaebrook/HandleIT-Android
- Base (baseline): `d945330fca1d679a700a797f3a5f11c041d4c049` — "Merge PR #13: docs(learning): record PR #6 merge (D05 live)"
- Working branch: `feat/stage-d-g02-d06` (cut from `d945330f`)
- Working clone: `/tmp/g02-work` (clean, HEAD detached at `d945330f`)

## 4. APK workflow / artifact

No Gradle/Android build or test runs on the VPS, any local machine, or PRoot (constraint 1).
Compilation and JVM unit tests run ONLY through GitHub-hosted Actions (`Candidate checks` on the PR).
The APK artifact is produced by the hosted Android Build workflow, never locally.

## 5. Requirements and acceptance

| # | Requirement | Acceptance criteria |
| --- | --- | --- |
| R1 | Enforce at tool-dispatch time through the existing `checkToolInterception` hook | `ProjectConstraintHook` is registered on the `AIToolHandler` singleton and a context-bearing overload of `checkToolInterception` is invoked from `ToolExecutionManager.executeInvocations` |
| R2 | Scope enforcement to the trusted HandleIT project only | `ProjectConstraintPolicy.decide` returns `Allow` when `context.projectIdentity.kind != HANDLEIT`; unit test pins `OTHER` and `UNKNOWN` pass-through |
| R3 | Block `./gradlew` / `gradle ` / `gradlew[.bat]` execution | `LOCAL_BUILD_FORBIDDEN` denial on the four command routes; unit tests for `./gradlew`, `gradle build`, `gradlew`, `gradlew.bat` |
| R4 | Block protected-path writes | `PROTECTED_PATH_MUTATION` denial for mutation tools targeting `objectbox-models/default.json`, signing stores/config, and `.git/`; unit tests |
| R5 | Deterministic structured denial | Denial reason is a bounded code (`LOCAL_BUILD_FORBIDDEN` / `PROTECTED_PATH_MUTATION`), never the full command/arguments/secrets |
| R6 | Audit the direct shell/command entrypoints | UNGUARDED inventory carried forward from `07-step1-g02-execution-route-audit.md`; recorded here and in the final report |
| R7 | Deterministic unit tests | New `ProjectConstraintPolicyTest` covering blocked commands, permitted passes, protected paths, and identity scoping |
| R8 | D06 verification (already merged) | Confirm no new code is required; D06 already shipped at `49ad9977` via PR #3 |

## 6. Constraints and preserved behavior

1. No Gradle/Android build or test on the VPS, local machine, or PRoot. All compilation/testing only
   through GitHub-hosted Actions `Candidate checks` on PRs.
2. No changes to `objectbox-models/default.json`, ObjectBox entity/column IDs (`entity12`/
   `MemoryLedger`), the `com.ai.assistance.operit` package/application IDs, or signing configs.
   (The schema file lives at `app/objectbox-models/default.json`.)
3. `main` is protected (`Candidate checks` required, `enforce_admins: true`). No direct pushes; all
   changes land via a feature branch + PR, merged only when `mergeable_state=clean`.
4. No silent scope expansion. `C04`–`C06`, `C03d`, `H01` are untouched. The directive's phrase
   "Step 2 items" is interpreted conservatively (see §8): this directive's own Step 2 is D06, which
   is already merged, so Step 2 reduces to verification/bookkeeping — no new product code.
5. No global singleton for task policy. The policy is a pure function over a per-invocation context
   threaded as a parameter (never read from a ThreadLocal at interception time — see §8 correction).
6. Never log full commands, arguments, or secrets. Denial reasons are bounded codes only.
7. Preserve all existing tests and acceptance criteria; add, never delete or weaken.

## 7. Architecture and execution

### 7.1 The corrected seam (supersedes the prior BLOCKED checkpoint)

The prior plan recorded G02 as BLOCKED because `AIToolHook.onToolCallIntercept(tool)` carries no
invocation context and `AIToolHandler` is a process-wide singleton. That checkpoint is SUPERSEDED by
the following verified facts (recorded as `CORR-2026-10-24-G02-CARRIER`, status active):

- `ToolExecutionManager.executeInvocations` (`ToolExecutionManager.kt:504`) constructs
  `ToolRuntimeContext(callerCardId, toolExposureMode)` as a local val at `:536`, **before** the
  interception loop at `:577`.
- The ThreadLocal is installed only later at `:677` inside `executeTool`, so
  `currentToolRuntimeContext()` returns null during interception — a ThreadLocal read is therefore
  NOT the seam.
- The correct seam is an explicit **parameter**: extend `checkToolInterception` with a defaulted
  context argument and pass the already-in-scope identity from `:577`. No global singleton is needed.

### 7.2 New files (package `com.ai.assistance.operit.core.tools`)

1. `ToolInvocationContext.kt` — `data class ToolInvocationContext(projectIdentity: ProjectIdentity =
   ProjectIdentity.UNKNOWN, chatId: String? = null, workspacePath: String? = null)`. Carries the
   trusted identity into interception. Placed in `core.tools` (not `api.chat.enhance`) so
   `AIToolHook`/`AIToolHandler` can reference it without a package cycle.
2. `ProjectConstraintPolicy.kt` — pure `object ProjectConstraintPolicy` with
   `fun decide(context: ToolInvocationContext, tool: AITool): ProjectConstraintDecision` and a
   `sealed class ProjectConstraintDecision { Allow; Deny(reasonCode) }`. Reason codes are bounded:
   `LOCAL_BUILD_FORBIDDEN`, `PROTECTED_PATH_MUTATION`.
3. `ProjectConstraintHook.kt` — `class ProjectConstraintHook : AIToolHook` overriding the
   context-bearing `onToolCallIntercept(tool, context)`; maps `Deny(code)` to `AIToolHookDecision.Block(code)`.

### 7.3 Modified files

1. `core/tools/AIToolHook.kt` — add a context-bearing overload with a default that delegates to the
   existing single-arg method, preserving all existing implementers:
   `fun onToolCallIntercept(tool: AITool, context: ToolInvocationContext?): AIToolHookDecision = onToolCallIntercept(tool)`.
2. `core/tools/AIToolHandler.kt` — add `checkToolInterception(tool, context)`; the existing
   `checkToolInterception(tool)` delegates with `null`. Register `ProjectConstraintHook()` once inside
   `getInstance` (the constructor is private), so the gate is always active and idempotent via the
   existing `addToolHook` `contains` guard.
3. `api/chat/enhance/ToolExecutionManager.kt` — in `executeInvocations`, resolve the per-chat
   workspace from `callerChatId`, derive `ProjectIdentity.resolveFromWorkspace(...)`, build a
   `ToolInvocationContext`, and pass it to `checkToolInterception(interceptionTool, context)` at `:577`.
4. `data/repository/ChatHistoryManager.kt` — add a small public `suspend fun getChatWorkspace(chatId:
   String): String?` wrapping `chatDao.getChatById(chatId)?.workspace` (single source of truth).

### 7.4 Policy logic

`ProjectConstraintPolicy.decide`:
1. If `context.projectIdentity.kind != HANDLEIT` → `Allow`.
2. If the tool is one of the four command routes and its `command` parameter contains a local gradle
   invocation → `Deny(LOCAL_BUILD_FORBIDDEN)`.
3. If the tool is a mutation tool and any of its path parameters targets a protected path →
   `Deny(PROTECTED_PATH_MUTATION)`.
4. Otherwise → `Allow`.

Command routes (read `command` param): `execute_shell`, `execute_in_terminal_session`,
`execute_in_terminal_session_streaming`, `execute_hidden_terminal_command`. Local-build detection is a
deterministic token-boundary regex matching `./gradlew`, `gradlew`, `gradlew.bat`, and bare `gradle`
(followed by a separator/end, so `gradlew` is not double-matched and `cat build.gradle` is not blocked).

Mutation tools and their path parameters:
- `path`: `write_file`, `write_file_binary`, `delete_file`, `make_directory`, `create_file`,
  `edit_file`, `apply_file`.
- `source` + `destination`: `move_file`, `copy_file`, `zip_files`, `unzip_files`.

Protected paths (normalized `/`, case-insensitive where noted):
- `objectbox-models/default.json` (ObjectBox schema — `entity12`/`MemoryLedger`, entity/column IDs).
- `.git/` and a trailing `.git` (never mutate git internals/history).
- Signing stores by extension: `.jks`, `.keystore`, `.p12`, `.pfx`.
- Signing config by basename: `local.properties`, `keystore.properties`, `signing.properties`.

### 7.5 Coverage caveat (must be disclosed)

Only model-dispatched calls through `EnhancedAIService.handleToolInvocation` → `executeInvocations`
carry identity. Direct `AIToolHandler.executeTool`/`executeToolAndStream` callers (UI, MCP deploy,
file manager, etc.) and every raw-exec route in the UNGUARDED inventory resolve to `UNKNOWN` → `Allow`.
G02 is therefore a real gate for the primary (model-dispatch) path, explicitly not an app-wide
sandbox. The `index.md` G02 item stays `[ ]` until the full route inventory is enforced.

UNGUARDED (carried from `07-step1-g02-execution-route-audit.md`, recorded as audited gaps):
`StandardShellExecutor.*`, `RootShellExecutor.*`, `RootAuthorizer.*`, `PortProcessKiller.killListeners`,
`ShowerServerManager.*`, `MCPBridge.deployBridge`/`startBridge`, `MCPDeployer.executeDeployCommands`,
`ShellCommandManager.executeCommand`, `AppPermissionsScreen`/`ProcessLimitRemoverScreen`,
`PhoneAgent.ActionHandler`, `RootActionListener`/`DebuggerActionListener`, raw `rm` in `DebuggerUITools`,
and remote executors `MCPBridgeClient.callTool`, `RemoteMcpRuntimeSession.callTool`.

## 8. Decisions and open questions

- **D1 (identity carrier) — RESOLVED.** Resolve the identity from the authoritative per-chat
  `ChatEntity.workspace` via a new `ChatHistoryManager.getChatWorkspace(chatId)` accessor, then
  `ProjectIdentity.resolveFromWorkspace(...)` inside `executeInvocations`. This reuses the exact
  D05/F1 trust mechanism (git-remote matching, never basename/prompt-claim) and is a single source of
  truth. Trade-off: one primary-key Room read per tool round; accepted.
- **D2 ("Step 2 items" in constraint 4) — interpretation.** This directive's Step 2 is D06, already
  merged at `5b3277cf` (PR #3, code `49ad9977`). Interpreted conservatively: no new D06 product code;
  D06 is reported as verification + bookkeeping only.
- **D3 (single branch) — confirmed.** One `feat/stage-d-g02-d06` branch carries the G02 implementation
  + tests + CHANGELOG + checklist update; D06 needs no code.
- **D4 (hook registration site) — resolved.** `AIToolHandler.getInstance` (private constructor forces
  this) registers `ProjectConstraintHook()` exactly once; idempotent via `addToolHook`.

## 9. Documentation and verification

- Update `docs/TODO/learning-pipeline-hardening-20261017/index.md` G02 line (implemented on this
  branch; item stays `[ ]` with the honest coverage caveat) and record the correction link.
- Add an `Unreleased` entry to `CHANGELOG.md` for G02; confirm D06 already recorded.
- Review `README.md`; record "no change needed" if no behavior/user-facing change.
- CI evidence: capture the `Candidate checks` run ID, `mergeable_state=clean`, merge commit SHA, and
  final `main` head SHA. No build/test is ever run locally.

## 10. Resume checkpoint

- Owner: VPS orchestrator via the bridge, at Teddy's explicit request.
- State: plan written; implementation next.
- Exact commit at plan-write: `d945330f` (clean, detached).
- Next: create the 3 new files, edit the 4 existing files, add `ProjectConstraintPolicyTest`, commit
  on `feat/stage-d-g02-d06`, push, open PR, wait for `Candidate checks` SUCCESS + `mergeable_state=clean`,
  merge via API, then land bookkeeping via a follow-up docs PR (PR #13 flow).
