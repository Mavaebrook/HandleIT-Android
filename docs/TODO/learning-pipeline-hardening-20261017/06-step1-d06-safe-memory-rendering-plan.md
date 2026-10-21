# Step 1 — D06 safe memory rendering (bounded task)

Status: PLANNED. This is a bounded task that implements D06 (safe memory rendering) and performs a G02 execution-route AUDIT ONLY. It is NOT Stage 2 and it does not complete G02.

## Scope

This task implements D06: retrieved model-generated memory text is structurally separated from trusted system framing, rendered deterministically and boundedly, labelled with only the provenance that actually exists, and escaped so that delimiter, role-marker and control-character content is treated as data.

The same task performs a G02 execution-route audit. The audit inventories the routes and records what is and is not covered. It implements no enforcement.

Out of scope, explicitly:

- C04-C06, meaning the episode schema extension, the Reflector prompt changes and the plan-time gap check
- graph and relational entities, including `EpisodeLinkEntity` and any `MemoryLink` traversal
- H01, the dedicated `REVIEW` function type
- automatic promotion of lessons or skills
- vector caching
- orchestrator changes of any kind
- G02 enforcement implementation, including any new interception hook, policy engine or shell-path guard

## Baseline

- Branch `fix/step1-d06-safe-memory-rendering`, created from `origin/main` at `838deba5c3f7ea86c71265046a09a039e86a0d94` (PR #2 merged).
- Worktree `/home/teddy/HandleIT-Android-d06`.

## Current state (reconciled before editing)

- `TaskPreflightFormatter.buildPreflightBlock` interpolates `taskSummary`, `resolutionSummary`, `causeSummary` and prerequisites verbatim into the system prompt with only a soft bracketed header, no fence, no escaping, no provenance.
- `ArchitectRulesHook.ArchitectRulesSystemPromptHook.onEvent` appends the preflight block to the SYSTEM prompt with only `\n\n` separation.
- `TaskEpisode` carries `id: Long` and `createdAtMs: Long` but has NO source-chat and NO confidence field. Those must render as unknown, never fabricated.
- The codebase already uses an XML-fence convention for injected content: `<assistant_role source="...">` and `<user_profile source="...">` in ConversationService.
- Existing safety gate: `EpisodeApplicability.INELIGIBLE_UNRESOLVED_INTEGRITY` renders only a neutral task line plus a fixed caution constant; resolution/cause are not rendered.

## D06 acceptance checklist

- [ ] Retrieved model-generated memory text is structurally separated from trusted system framing using the existing XML-fence convention, with a fixed authoritative framing block kept separate from untrusted episode data.
- [ ] Deterministic bounded rendering with stable episode IDs and timestamps.
- [ ] Accurate source/trust labels using only provenance actually present; missing source chat and confidence render as unknown, never fabricated, never inferred from outcome.
- [ ] No schema change to add unavailable provenance.
- [ ] Delimiter, role-marker and control-character content escaped/encoded as DATA; useful multilingual (CJK) text preserved.
- [ ] Field, episode and whole-block budgets applied without cutting framing or serialized structures into malformed output.
- [ ] Existing unresolved prerequisite/constraint gate preserved: no resolution/cause/advice from unresolved rows, no raw recovery-byte leakage.
- [ ] No duplicate memory block.
- [ ] Cancellation and existing no-extra-model-call behavior preserved.
- [ ] Regressions added at the actual renderer/composition seam: fake system/assistant delimiters, embedded instructions, control characters, long inputs, Unicode/CJK, missing provenance, unresolved rows.
- [ ] Regressions assert structural separation, metadata accuracy, complete framing, bounded size, no raw-byte leakage.
- [ ] Existing codec/redaction tests preserved.
- [ ] Honest documentation of any remaining lower-trust-boundary gap; D06 marked PARTIAL if provider plumbing cannot enforce a true boundary.

## G02 audit checklist (audit only, no enforcement)

- [ ] Inventory `AIToolHandler.executeTool`/`checkToolInterception` and all direct execution routes.
- [ ] For each route record file/symbol/caller, whether shared policy interception runs, which scope/executor context reaches it, and uncovered bypasses.
- [ ] Distinguish app runtime from external phone/VPS agents.
- [ ] Output a bounded follow-up enforcement plan with testable acceptance criteria.
- [ ] G02 checkbox stays unchecked; no enforcement implemented.

## A01 documentation update

- [ ] Record user-reported device evidence separately from independently checked CI.
- [ ] Keep A01 full-verification checkbox unchecked with precise explanation.

## Invariants

- `app/objectbox-models/default.json` byte-for-byte unchanged.
- No new entities, columns or UIDs. No entity12/MemoryLedger edits.
- Preserve `com.ai.assistance.operit` and `.debug` identities, internal Operit classes, licensing, existing data, signing identity/certificate, and the a9d4849 notification behavior.
- No secrets output, no key changes, no history rewriting, no silent fallback chains, no weakened tests.

## Verification

- Builds and tests ONLY via the GitHub-hosted `Android Build` and `Android Tests` workflows.
- Never run Gradle, builds or tests on the VPS, a local machine, a phone or PRoot.

## Delivery

- Commit and push the scoped implementation, dispatch both hosted workflows on the exact final SHA, and open a PR against main LEFT UNMERGED.
