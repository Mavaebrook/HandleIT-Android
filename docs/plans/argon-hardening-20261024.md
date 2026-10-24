# Plan — Argon review hardening (non-blocking notes 1 & 2)

- Date: 2026-10-24
- Task: argon-hardening-20261024
- Scope: two one-line hardening edits in `MemoryMetadataStore.kt`, sourced from Argon's review `Argon-Review-f5ddc3fb75-2026-10-24.txt` (Drive id `1Oq-KED_Vy4Tdv22MxnhzUQtmRTNQwcpf`). Optional polish, not defect remediation.

## Ground truth

- `main` HEAD: `f5ddc3fb75e5eab59f785f5bc72f34f479007b5f` — Argon's review target, verdict **APPROVE-WITH-NOTES, zero blockers**, CI **ACCEPTED**.
- File edited: `app/src/main/java/com/ai/assistance/operit/data/repository/MemoryMetadataStore.kt` (blob `7044cb15f4800bb766908705620fdcebdd7f64be`).
- Open PRs at time of work: #11, #7, #6.

## Changes (2 lines)

1. `retire` — after `priorStatus` is computed, add `if (priorStatus == STATUS_INACTIVE) return@runInTx` before the audit write. Closes a future-caller gap: a caller bypassing `MemoryRetirementGovernance.decide` on a memory deactivated via `markInactive` would otherwise record `priorStatus = "INACTIVE"`.
2. `initializeStatusIfAbsent` — change `if (readProperty(memory, PROP_STATUS) == null)` to `if (readProperty(memory, PROP_STATUS).isNullOrBlank())`, aligning blank handling with `retire`'s `?.takeIf { it.isNotBlank() } ?: STATUS_ACTIVE`.

Neither changes established behavior; the governance path already blocks the already-inactive case before `retire` is called.

## Evidence

- Accepted CI on `f5ddc3fb75`: Android Build #81 run `40065402652`, Android Tests #82 run `40065402642` (both SUCCESS).
- This PR waits for a green `Candidate checks` before merge.

## Provenance & records

- Reviewer: Argon (Drive artifact). Applied by Mavaebrook assistant under the `handleit-architect` skill.
- CHANGELOG: dated entry added (repo uses dated entries; no `## Unreleased` header exists).
- README: reviewed, unchanged — internal hardening, no user-facing/behavioral change.
- No PROJECT_CONSTRAINTS correction record required — no prior decision is being reversed.

## Verification

- Diff shows exactly 2 changed code lines plus the plan and CHANGELOG entry.
- No local build or APK produced; APK builds remain GitHub-Actions-only.
