# HandleIT-Android — status, remaining tasks, and recommendations

Reviewed: 2026-10-21 UTC. Repository: [Mavaebrook/HandleIT-Android](https://github.com/Mavaebrook/HandleIT-Android).

Update at 09:44 UTC: this review preserves the earlier snapshot below. PR #3 has since merged into main at `5b3277cf`; PR #4 needs conflict resolution, and PR #5 now has successful build and test runs on its current head. See [the current task and dispatch record](02-dispatch-and-next-tasks.md) for the verified status, bridge jobs #46–#48, ownership, and remaining gates.

The repository has a working Android build and passing JVM tests. The recent learning-data integrity repair is merged. The next memory-rendering improvement is implemented and CI-verified in an open PR. The learning system still needs device data-preservation evidence, project-scoped execution controls, governance, and measured end-to-end evaluation before it can be described as complete.

The most immediate engineering issues are the PR workflow targeting the wrong base branch, MCP argument conversion changing declared strings, and incomplete memory safeguards. The recommended next milestone is a verified stabilization pass, followed by the remaining learning features.

## Historical snapshot

| Area | Verified status |
| --- | --- |
| Canonical branch | `main` at `838deba5c3f7ea86c71265046a09a039e86a0d94` |
| Latest main change | [PR #2](https://github.com/Mavaebrook/HandleIT-Android/pull/2), codec integrity and diagnostic redaction, merged 2026-10-21 at 00:11:56 UTC |
| App configuration | `1.12.2`, versionCode `51`; minSdk 26, targetSdk 34, compileSdk 36; ARM64 only ([Gradle configuration][gradle]) |
| Open PR | [#3](https://github.com/Mavaebrook/HandleIT-Android/pull/3), D06 rendering and G02 route audit; open and mergeable when checked |
| PR #3 head | `832a4246fb44d34fbd80a033fade0d823fd35622`; last code change `49ad9977732bd57566fd78aa7933c65ef5f766f4` |
| Open issue | [#1](https://github.com/Mavaebrook/HandleIT-Android/issues/1), MCP `smartConvert` missing explicit string handling; the defect remains in main |
| GitHub releases | The repository's [releases API](https://api.github.com/repos/Mavaebrook/HandleIT-Android/releases) returned no releases. This does not establish whether APKs are distributed elsewhere. |
| Branch protection | `main` reports `protected: false`; the repository rulesets API returned an empty list (2026-10-21 snapshot; main protection was configured on 2026-10-24 — `Candidate checks` required, strict, include administrators) |

The codebase includes the Android/Compose app, local MNN and llama.cpp integrations, QuickJS, terminal integration, avatar modules, Shower client, ToolPkg/MCP tooling, and a React/Vite Web Chat frontend. This is a substantial existing application; the active work is primarily reliability and learning integration. This review did not exercise every product feature on a device.

### Build and test evidence

| Revision | Android Build | Android Tests |
| --- | --- | --- |
| Main `838deba5` | [39519848651 — success](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39519848651) | [39519848630 — success](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39519848630) |
| PR #3 code `49ad9977` | [39534113610 — success](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39534113610) | [39534111508 — success](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39534111508) |

The [comparison from PR #3's tested code to its current head][pr-doc-diff] contains only CHANGELOG and TODO documentation changes. The green results therefore cover its current code and workflows, although they did not run on the documentation head itself. Earlier branch failures are superseded by this green pair.

The Android Tests job runs `:app:testDebugUnitTest`; its latest log confirms `BUILD SUCCESSFUL`. These results establish compilation/JVM test success, not local ObjectBox upgrade integrity, device behavior, or learning effectiveness. No new builds, tests, or device sessions were initiated for this review.

## Completed and partially completed work

The [main hardening checklist][hardening] has 11 checked task IDs and 20 unchecked task IDs. These counts describe the checklist, not a percentage of engineering effort or product readiness.

Completed on main, with implementation and historical CI evidence recorded in that checklist:

- A02/A03: deterministic review triggers and review draining across memory spaces
- B01/B02: per-review success handling, bounded retries/dead-letter behavior, stale-processing recovery, and locale-aware review generation
- C01/C02/C03a: task-context preflight, improved prerequisite matching, Unicode tokenization, bounded lexical candidates, and vector candidate retrieval; newly reviewed episodes are wired into indexing
- D07: versioned list encoding, integrity metadata, preservation of unresolved stored bytes, atomic row migration planning, and redacted recovery-byte diagnostics
- R14/R14b: observed events separated from interpretation, with neutral retry phrases removed from correction markers
- R15: committed ObjectBox model lineage and a hosted regeneration check

Additional foundations exist: typed observations, a shared observation coordinator with TTL bounds, and synthetic replay fixtures. Their presence does not complete the full observation lifecycle, relational memory, real-task evaluation, or skill-promotion loop.

PR #3 adds bounded, escaped memory rendering with provenance, preserves the unresolved-integrity gate, and records an execution-route inventory. D06 remains **partial** because the data is still appended to the SYSTEM message. G02 remains **audit-only**. A01 remains **partial**. These changes are not yet in main.

The older [reliable-learning checklist][original-plan] marks T09/T10 complete for code and unit-test scaffolding, with an explicit wiring caveat. Its old opening/checkpoint statements should not be interpreted as evidence that automatic skill learning is fully operational. Conversely, the hardening index's opening claim that the queue is never populated is historical: A02/A03 and indexing repairs have since been implemented.

## Findings to prioritize

Priority 1 means the next stabilization work or a condition for the relevant readiness claim. It does not mean every item blocks merging PR #3.

### 1. PR validation misses the canonical branch — Priority 1

**Evidence:** [pr-check.yml lines 3–6][pr-trigger] only runs for PRs targeting `development`. The repository's default branch is `main`, and its current branch list contains no `development` branch. The [pull-request workflow query](https://api.github.com/repos/Mavaebrook/HandleIT-Android/actions/runs?event=pull_request&per_page=10) returned zero runs. Android Build and Android Tests run automatically on selected main pushes and support manual dispatch, so the current feature branch has depended on manual runs. Main is also unprotected.

**Recommendation:** include `main` in the PR workflow target, retain the existing merge-candidate validation and secret-free PR jobs, then require the appropriate aggregate check through branch protection or a ruleset. Keep the signed artifact workflow restricted to trusted contexts. Attach ObjectBox regeneration verification to relevant model/entity changes; currently that check is a separate manual workflow.

**Done when:** opening and updating a PR against main triggers candidate checks; a deliberately failing required check prevents merge; the hosted model check detects model drift. GitHub settings changes remain a follow-up task, not a change made by this review.

### 2. MCP argument conversion can alter user data — Priority 1

**Evidence:** [MCPToolParameter.smartConvert][mcp-convert] has no `"string"` branch. Its `else` branch guesses JSON, numeric, and boolean types. [MCPToolExecutor][mcp-call] calls it with the schema type, so an explicitly declared string still reaches that guesser. For example, source inspection shows `"00123"` with type `"string"` becomes a number, and `"false"` becomes a boolean. The same helpers recursively reinterpret quoted strings inside objects/arrays; `parseArray` also omits null elements. This confirms [issue #1](https://github.com/Mavaebrook/HandleIT-Android/issues/1) and identifies adjacent preservation cases.

The conversion diagnostic at `MCPToolExecutor.kt:399–403` logs both the original and converted parameter values. The recovery-byte redaction added by PR #2 does not cover this logging path.

**Recommendation:** make conversion follow the declared schema, preserve explicit strings and structured JSON values, preserve array null positions, and define the behavior for absent/unsupported types without guessing from arbitrary content. Log parameter names/types and safe diagnostics instead of payload values.

**Done when:** hosted regressions cover leading-zero strings, boolean-looking strings, JSON-looking text, nested string values, null array entries, and a logging sentinel; a mock MCP endpoint receives the intended JSON types and content.

### 3. A01 device preservation remains unverified — Priority 1 for upgrade readiness

**Evidence:** the [PR checkpoint][d06-checkpoint] and its [updated A01 entry][pr-hardening] record a user-reported in-place installation, unchanged UID, and clean startup. The marker-memory exercise used server-side memory. Phone-local TaskEpisode/SkillRevision counts and content preservation were not demonstrated, and no compatible populated historical baseline is available.

**Recommendation:** establish a compatible historical baseline with known local records, preserve it, and document before/after IDs, counts, and representative contents through supported inspection. Cover healthy legacy/versioned rows and unresolved data, plus reopen and explicit migration behavior. A new empty install can add smoke evidence but cannot establish historical preservation.

**Done when:** a compatible populated upgrade and content round-trip have reproducible device evidence. Until then, retain A01's partial status. This limitation does not by itself invalidate the bounded renderer PR.

### 4. Memory trust and project-rule scope need further work — Priority 1

**Evidence:** [PR #3's formatter][pr-formatter] sanitizes and fences retrieved content, but [ArchitectRulesHook][architect-hook] still appends it to the SYSTEM prompt. The same hook selects every non-empty workspace path and injects HandleIT-specific repository/build rules into unrelated projects. The [G02 audit][g02-audit] lists execution paths outside the tool interception boundary, including PhoneAgent actions, Shower startup, MCP deployment commands, and direct UI-to-shell routes. Dispatcher checks and the unguarded low-level [standard shell executor][shell] were corroborated in source.

**Recommendation:** review PR #3 as a useful partial mitigation, scope project rules to the actual project, and design provider-aware handling of retrieved content outside the authoritative system instructions. Implement a shared project-constraint guard covering the execution paths appropriate to that project; explicitly record any remaining uncovered routes. An XML fence or a single dispatcher check is insufficient evidence of complete enforcement.

**Done when:** unrelated workspaces receive no HandleIT rules, supported providers preserve the intended trust separation, and each inventoried execution route has a tested enforcement decision or an explicit documented gap. Keep D06 partial and G02 incomplete until their respective criteria are met.

### 5. Model-generated memory links can deactivate facts without governance — Priority 1

**Evidence:** [MemoryLibrary.kt:532–560][retirement] resolves link endpoints by title and directly calls `markMemoryInactive(target)` for `CORRECTS` and `SUPERSEDES`. The path does not verify recency, direction, or protected user constraints before retirement. The existing D02/R08 review documents the missing reactivation mechanism.

**Recommendation:** establish stable target identity, validate direction and applicability, protect explicit user corrections, and record enough state for an intentional restoration operation. Keep this governance repair ahead of automatic promotion.

**Done when:** reversed or ambiguous links cannot retire the wrong fact, protected corrections remain retrievable, and an authorized restoration returns a retired memory to retrieval. See D02 in the backlog.

### 6. Review model calls still swallow cancellation — Priority 1 follow-up

**Evidence:** [ReflectorCurator.kt:38–47 and 62–74][reflector] wraps suspending model calls in `runCatching`, logs every failure, and returns null. This also catches `CancellationException`. The scheduler's outer cancellation handling cannot rethrow an exception already converted into null; its ordinary no-episode path treats that result as a failed review.

**Recommendation:** explicitly propagate coroutine cancellation through Reflector and Curator, and keep cancellation distinct from a failed review attempt.

**Done when:** cancellation during either model call reaches the caller and does not consume a retry/dead-letter attempt or create a misleading ordinary-failure record. This is a source finding; it was not reproduced in a new test run during this review.

## Remaining learning roadmap

This table accounts for all 20 unchecked IDs on main. It preserves the amended requirements in [02-implementation.md][implementation], including scoped dismissal reasons and selective trace expansion.

| IDs | Current state and remaining work | Acceptance / dependency |
| --- | --- | --- |
| A01 | Schema committed; full device preservation partial | Compatible populated upgrade evidence as described above |
| D06 | Implementation in PR #3; trust separation partial | Review/integrate the bounded mitigation; verify provider-level trust handling separately |
| D05, G02 | Global project prompt; route audit exists on PR branch; enforcement incomplete | Project isolation and execution-route coverage |
| D02 | Memory retirement governance incomplete | Direction/identity/constraint checks and intentional restoration |
| R14-UI | Explicit-correction input exists; deliberate user control remains absent | A “this reply is wrong” action reaches the observation accumulator; regeneration alone remains a retry |
| H01 | Reflector uses MEMORY; Curator uses SUMMARY; no REVIEW function type | Reuse model configuration, add reviewer selection and connection handling, record provider/model identity, explain where review text is sent |
| C03d, C04 | Episode graph expansion and richer episode metadata absent | Decide compatible schema/UID design first; add relation, signature, applicability, trace and feedback metadata without damaging existing data |
| C05, C06 | Extended extraction and full plan-time gap check incomplete | Parse omissions/conditions; emit at most three applicable cautions; record feedback reason and scope; “not applicable here” must not retire a valid lesson |
| G01, R16 | End-to-end attribution missing; trigger/confidence/evidence separation remains a cross-cutting rule | Log injected episode/skill IDs and update helpful/harmful outcomes from deterministic evidence; a trigger alone never justifies promotion |
| D01, D03 | Curator/revision production integration outstanding; current lifecycle gate allows one evidence source and does not require helpful outcomes | Wire persisted draft revisions after H01; require independent outcome evidence and inspect candidate steps against authoritative constraints before promotion |
| D04 | Reinforcement and retrieval-quality/performance repairs outstanding | Cosmetic edits must not inflate reinforcement; batch salience reads; eliminate unjustified propagation scores; measure query counts and retrieval effects |
| E01, E02 | No completed trajectory recording/retrieval feature | Versioned, redacted, bounded records with exclusions and rotation; retrieve compact indexes, then relevant trace segments, following the amended plan |
| F01, F02 | Synthetic replay harness exists; real-task/model comparisons unmeasured | Held-out real-task baseline/with-lesson/without-lesson comparisons; measure reviewer alternatives; version tuning and define how regressions are rejected or reverted |

Suggested dependency order: resolve the immediate integrity and enforcement gaps; design schema/provenance and H01; complete feedback and outcome attribution; connect governed draft-revision processing; then evaluate and tune promotion with held-out evidence. Record the intended order in the active plan before implementation, since existing documents contain several historical sequences.

### Follow-ups that checkboxes alone understate

- **D07 compatibility and recovery:** a legacy literal that is itself a valid versioned JSON envelope is indistinguishable from a real envelope under the current schema. [StringListCodec][codec] explicitly records this as partial/blocked. More parsing heuristics cannot recover provenance. A persistent discriminator and a compatible migration decision are needed for a durable solution.
- **Unresolved field editing:** current writes preserve captured raw bytes and ignore edits to unresolved lists. Provide an explicit resolution operation and a clear user-facing outcome before offering those fields as normally editable. Keep unresolved fields from contributing evidence; decide whether unresolved episodes should occupy scarce retrieval slots.
- **Observation lifecycle:** [ObservationCoordinator][observations] documents live rotation on execution invalidation, while chat-release has no live callers and executor/workspace/PRoot/SSH replacement boundaries remain unwired. Project/executor/environment coordinates are incomplete. Wire actual lifecycle events before relying on observations across those boundaries; keep unknown facts unknown.
- **Promotion semantics:** [SkillRevisionLifecycle][lifecycle] has `MIN_INDEPENDENT_EVIDENCE = 1`, checks declared constraint strings, and omits helpful count from its pass predicate. Integrity rejection is improved, but this is not yet a sufficient automatic-promotion gate.
- **Measured benefit:** the [test-only replay seam][replay] contains no model calls or promotion decision. Preserve it as useful regression infrastructure while completing F01/F02 and original T11. Original T13 device acceptance and T14 final handoff also remain open.

## Delivery and maintenance recommendations

### Make build inputs reproducible — Priority 2

[download_android_dependencies.sh][dependencies] fetches three Google Drive archives and checks that they are nonempty. The preparation script validates archive structure and expected outputs, but these two scripts do not pin archive content hashes. Cache identity currently follows script changes rather than an explicit artifact manifest.

Create an artifact manifest with versions, SHA-256 values, provenance and ownership; verify downloads and restored cache content against it. The existing STT manifest already demonstrates content verification. Preserve the completed ZIP path-safety checks. Treat the broader inherited build-system draft as a separate program, not a prerequisite for this small reproducibility improvement.

### Align installation guidance with available artifacts — Priority 2

README directs users to GitHub Releases, while this repository currently has none. Successful Actions artifacts have a 14-day retention period in the build workflow. Decide and document the supported distribution channel. For a public release, retain the exact source SHA, signed APK, checksum, signing-certificate identity, and upgrade evidence; publishing is a separate follow-up action.

The configured target SDK is 34 despite compiling against 36. Assess distribution requirements and device behavior before planning a target-SDK change; do not infer Android 15/16 behavior from compileSdk alone.

### Preserve the distinction between test lanes — Priority 2

The trusted Android Tests workflow runs JVM tests. PR Check can compile instrumentation sources when selected, but the inspected workflows do not run connected device instrumentation. Add targeted hosted/device acceptance evidence for storage, notifications, provider message construction and permission-sensitive flows as those areas change. Android lint was deliberately removed by earlier work; any reintroduction should be a scoped decision with an agreed baseline, rather than treating it as an accidentally missing check.

### Reconcile documentation and branch status — Priority 2

- Update PR #3's description to the current code SHA and green run links. It still points at the first dispatch and says CI was pending. The newer checkpoint is more accurate. No submitted PR reviews were returned; the automated review attempt stopped because of a usage limit, which is not a completed review or a new code finding.
- Give the hardening index a short current-state summary and label old findings/checkpoints as historical. Reconcile the opening “pipeline not running” statement with completed queue/indexing repairs and keep implementation, CI, device, and measured-effectiveness statuses separate.
- Correct architectural documentation that says QuickJS is not connected to the app: `app/build.gradle.kts` already contains `implementation(project(":quickjs"))`.
- Review [feature/self-learning-memory][feature-diff] independently. It is 20 commits ahead and 97 behind main and includes `MemoryLedger.kt`, memory logic, application startup, resources and tests. Do not equate it with main's current TaskEpisode work or bulk-merge it based on its name. Establish intended ownership and schema compatibility first.
- Reconcile older TODO directories before treating them as current commitments. Several inspected inherited tasks already say DONE/completed, including dependency updates, PR job separation and database-health work.

## Other recorded future work

The [interface redesign PRD][ui-plan] is marked `future-design-reference` and `implementation_authorized: false`. U01–U10 remain planned: screen inventory, navigation/mode mapping, mockups, accessibility tokens, reusable components, Simple/Advanced mode, truthful compact activity, compatibility verification and documentation. Its next useful deliverable is an inventory and reviewed mockups; this review does not activate a UI rewrite.

The original learning plan's research/expert-review protocol F01–F08 is also an explicitly deferred backlog. Those IDs are a different namespace from the hardening plan's evaluation F01/F02. GEPA search, executable-skill synthesis and a wholesale memory-backend replacement remain later decisions.

The inherited [floating-window investigation][floating] records two unresolved investigations: the first source of non-finite persisted dimensions and reroll/variant switching behavior. Its mitigations are marked done, but those root causes require current device evidence before selecting further fixes. They were not re-reproduced here.

## Recommended next milestone

- [ ] Repair the main-targeted PR gate and establish required checks
- [ ] Complete PR #3 review/handoff using code `49ad9977` and the verified documentation-only head; merge only through the repository's normal authorization process
- [ ] Fix issue #1 and its nested-value/null preservation cases, including payload logging
- [ ] Correct review cancellation handling
- [ ] Establish the A01 populated baseline and record device evidence, or keep the exact blocker visible
- [ ] Scope project rules and turn the G02 route inventory into a bounded enforcement implementation
- [ ] Add memory-retirement governance before extending automatic learning
- [ ] Reconcile the active roadmap and distribution documentation with these verified states

After that milestone, schedule schema/provenance, H01, feedback/outcome attribution and measured learning in explicit increments. Future UI design can proceed as its own planned workstream when selected.

## Review scope and evidence

This document combines live GitHub branch/PR/issue/workflow/release metadata, pinned source inspection, the active learning plans, selected inherited TODOs, and existing hosted CI logs. It is a static status and prioritization review, not an exhaustive audit of every module. The workspace checkout attempt was unavailable; source files were inspected through the GitHub connector and saved as local review evidence. At the time of that initial review, no repository code, GitHub settings, issues, PRs, releases or branches were changed. Subsequent actions are recorded in the handoff and dispatch record.

Main references below are pinned to `838deba5`; PR references are pinned to `832a4246`. Historical plans are treated as backlog evidence where stated, and device observations are attributed to the existing user-reported checkpoint.

[gradle]: https://github.com/Mavaebrook/HandleIT-Android/blob/838deba5c3f7ea86c71265046a09a039e86a0d94/app/build.gradle.kts#L360
[hardening]: https://github.com/Mavaebrook/HandleIT-Android/blob/838deba5c3f7ea86c71265046a09a039e86a0d94/docs/TODO/learning-pipeline-hardening-20261017/index.md
[original-plan]: https://github.com/Mavaebrook/HandleIT-Android/blob/838deba5c3f7ea86c71265046a09a039e86a0d94/docs/TODO/reliable-learning-architect-20261016/index.md
[implementation]: https://github.com/Mavaebrook/HandleIT-Android/blob/838deba5c3f7ea86c71265046a09a039e86a0d94/docs/TODO/learning-pipeline-hardening-20261017/02-implementation.md
[pr-trigger]: https://github.com/Mavaebrook/HandleIT-Android/blob/838deba5c3f7ea86c71265046a09a039e86a0d94/.github/workflows/pr-check.yml#L3
[mcp-convert]: https://github.com/Mavaebrook/HandleIT-Android/blob/838deba5c3f7ea86c71265046a09a039e86a0d94/app/src/main/java/com/ai/assistance/operit/core/tools/mcp/MCPToolParameter.kt#L69
[mcp-call]: https://github.com/Mavaebrook/HandleIT-Android/blob/838deba5c3f7ea86c71265046a09a039e86a0d94/app/src/main/java/com/ai/assistance/operit/core/tools/mcp/MCPToolExecutor.kt#L382
[pr-doc-diff]: https://github.com/Mavaebrook/HandleIT-Android/compare/49ad9977732bd57566fd78aa7933c65ef5f766f4...832a4246fb44d34fbd80a033fade0d823fd35622
[d06-checkpoint]: https://github.com/Mavaebrook/HandleIT-Android/blob/832a4246fb44d34fbd80a033fade0d823fd35622/docs/TODO/learning-pipeline-hardening-20261017/08-step1-d06-checkpoint.md
[pr-hardening]: https://github.com/Mavaebrook/HandleIT-Android/blob/832a4246fb44d34fbd80a033fade0d823fd35622/docs/TODO/learning-pipeline-hardening-20261017/index.md#L15
[pr-formatter]: https://github.com/Mavaebrook/HandleIT-Android/blob/832a4246fb44d34fbd80a033fade0d823fd35622/app/src/main/java/com/ai/assistance/operit/data/learning/TaskPreflightFormatter.kt#L119
[architect-hook]: https://github.com/Mavaebrook/HandleIT-Android/blob/832a4246fb44d34fbd80a033fade0d823fd35622/app/src/main/java/com/ai/assistance/operit/core/chat/hooks/ArchitectRulesHook.kt#L47
[g02-audit]: https://github.com/Mavaebrook/HandleIT-Android/blob/832a4246fb44d34fbd80a033fade0d823fd35622/docs/TODO/learning-pipeline-hardening-20261017/07-step1-g02-execution-route-audit.md
[shell]: https://github.com/Mavaebrook/HandleIT-Android/blob/838deba5c3f7ea86c71265046a09a039e86a0d94/app/src/main/java/com/ai/assistance/operit/core/tools/system/shell/StandardShellExecutor.kt#L40
[retirement]: https://github.com/Mavaebrook/HandleIT-Android/blob/838deba5c3f7ea86c71265046a09a039e86a0d94/app/src/main/java/com/ai/assistance/operit/api/chat/library/MemoryLibrary.kt#L532
[reflector]: https://github.com/Mavaebrook/HandleIT-Android/blob/838deba5c3f7ea86c71265046a09a039e86a0d94/app/src/main/java/com/ai/assistance/operit/api/chat/library/ReflectorCurator.kt#L33
[codec]: https://github.com/Mavaebrook/HandleIT-Android/blob/838deba5c3f7ea86c71265046a09a039e86a0d94/app/src/main/java/com/ai/assistance/operit/data/learning/StringListCodec.kt#L121
[observations]: https://github.com/Mavaebrook/HandleIT-Android/blob/838deba5c3f7ea86c71265046a09a039e86a0d94/app/src/main/java/com/ai/assistance/operit/data/learning/ObservationCoordinator.kt#L15
[lifecycle]: https://github.com/Mavaebrook/HandleIT-Android/blob/838deba5c3f7ea86c71265046a09a039e86a0d94/app/src/main/java/com/ai/assistance/operit/data/learning/SkillRevisionLifecycle.kt#L21
[replay]: https://github.com/Mavaebrook/HandleIT-Android/blob/838deba5c3f7ea86c71265046a09a039e86a0d94/app/src/test/java/com/ai/assistance/operit/data/learning/ReplayEvaluationSeam.kt#L3
[dependencies]: https://github.com/Mavaebrook/HandleIT-Android/blob/838deba5c3f7ea86c71265046a09a039e86a0d94/ci/script/download_android_dependencies.sh#L26
[feature-diff]: https://github.com/Mavaebrook/HandleIT-Android/compare/838deba5c3f7ea86c71265046a09a039e86a0d94...856eb55c41ee8f5a873caff503a93ca727028462
[ui-plan]: https://github.com/Mavaebrook/HandleIT-Android/blob/838deba5c3f7ea86c71265046a09a039e86a0d94/docs/plans/handleit-interface-redesign-prd-2026-10-17.md
[floating]: https://github.com/Mavaebrook/HandleIT-Android/blob/838deba5c3f7ea86c71265046a09a039e86a0d94/docs/TODO/floating_window_nan_crash_20260917/index.md
