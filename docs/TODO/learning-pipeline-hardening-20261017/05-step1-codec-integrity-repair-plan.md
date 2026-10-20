# Step 1 Codec & Data Integrity Repair — Plan (Job 1 only)

Status: PLANNED (implementation authorized 2026-10-19). This is a bounded repair of the
Step 1 codec/data-integrity work after the independent review of bridge #36 did NOT establish
Step 1 acceptance. Prior green Build 39285876715 / Tests 39285876711 did NOT establish acceptance.

Base: `f495c1083b204ee1c50eb333600a68c3a32c963d` (code SHA `41c9968fe173a334d24a7fefb827f4266557565d`).
Remote HEAD rechecked: origin/main == f495c108, clean tree, no concurrent work ahead.

## Scope (Job 1 ONLY)

This repair touches ONLY the codec, the two learning entities' read/write boundaries, the
preflight formatter, the skill-revision lifecycle, and the row-migration planner/repositories.
It does NOT start Job 2 (observation lifecycle), Stage 2 D06/G02, graph/relational entities, H01,
vector caching, or orchestrator redesign.

## Hard invariants (unchanged)

- `app/objectbox-models/default.json` byte-for-byte unchanged (blob `971b9fd3...`). No new
  entities, columns, UIDs. No entity12/MemoryLedger changes.
- Package/applicationId `com.ai.assistance.operit` and `.debug`, internal Operit classes,
  licensing, existing data/IDs, signing identity/certificate, a9d4849 notification snapshot: all
  preserved. No secret output, no key changes, no history rewriting.
- Builds/tests ONLY via GitHub-hosted `Android Build` / `Android Tests` Actions. Never VPS/local/
  phone/PRoot Gradle.
- No fallback/rollback code (AGENTS.md). Existing legacy compatibility is explicit version
  decoding, not exception fallback.

## Problem statement (from review)

A. `StringListCodec` leading-`{` precheck + strict Envelope parse still misclassifies
   brace-starting legacy text. Strict JSON proves syntax, NOT historical format intent. A newline
   does not prove legacy; a `{"v":` prefix or magic prefix cannot universally distinguish arbitrary
   preexisting strings. No heuristic may convert malformed recognized versioned payload into
   Legacy. Unprefixed `{"v":1,"items":[...]}` envelopes from the f39e4358 build must be accounted
   for (previous APK may have been installed). New-format data must round-trip losslessly; known-
   version migration must be idempotent. Where historical bytes can represent either legacy literal
   text or a versioned envelope, preserve unresolved bytes with an explicit ambiguity status rather
   than guessing. Unsupported versions and recognized malformed/truncated/trailing-junk payloads are
   nonhealthy, never silently Legacy/healthy-empty.

B. Domain boundary: `readListField` logs Corrupt/UnsupportedVersion and returns `emptyList()`
   with no domain signal. Add non-persisted explicit integrity metadata distinguishing genuine
   empty lists from unresolved/corrupt/unsupported/ambiguous fields. Retain field names,
   reason/version, and original raw stored strings for controlled recovery. Keep untrusted raw
   contents out of prompt rendering/logs. Audit all consumers/transformations/copies so integrity
   state survives.

C. Fail closed in actual consumers:
   - TaskEpisode with unresolved requiredPrerequisites/applicableConstraints must have a
     machine-readable ineligible-to-apply result consumed by the real applicability/preflight path
     (not only an unused Boolean or prompt warning). No claim that missing prerequisites are
     satisfied.
   - TaskPreflightFormatter must emit a bounded fixed caution that the prerequisite/constraint
     record needs verification; do not interpolate the raw corrupt payload. Do not duplicate memory
     blocks. This narrow corruption caution is in scope; broad D06 remains later.
   - SkillRevisionLifecycle.evaluate / preservesUserConstraints must reject a candidate with ANY
     unresolved list field (prerequisites, steps, checks, pitfalls, evidenceKeys, userConstraints),
     passed=false with an explicit reason. Audit baseline/current revision constraint comparisons so
     unresolved constraints cannot be bypassed.
   - This is memory applicability integrity, NOT a universal execution security gate; no G02 claim.

D. Subsequent write preservation: attaching metadata while `toEntity()` later serializes
   placeholder empty lists would destroy original bytes. Audit toEntity/save/update/copy/review
   paths. Either block writes of unresolved fields/rows with a surfaced result, or preserve original
   raw fields until explicit resolution. No automatic clearing of integrity metadata or overwriting
   unresolved bytes with []/empty/new envelope. No user-facing resolution UI unless indispensable.

E. Whole-row migration: retain two-phase RowMigrationPlanner and bounded repositories. Any
   NEEDS_ATTENTION row remains untouched in memory and on disk. Stage all fields before any
   mutation. Repeated migration is idempotent. Include corrupt later-field case.

## Chosen provenance policy (honest, explicit)

The codec discriminator is STRUCTURAL, not a magic prefix. We keep the unprefixed envelope
format (f39e4358 already wrote it; a prefix would strand those rows). We add an explicit
`Ambiguous` decode outcome for the case where stored bytes are a well-formed JSON object that
COULD be a versioned envelope but whose provenance cannot be proven — specifically a brace-starting
value that parses as JSON but does not carry a recognized `v`/`items` envelope shape. Such bytes are
preserved verbatim with an explicit ambiguity status; they are never silently decoded as Legacy and
never silently rewritten. Recognized-but-malformed versioned payloads (truncated, trailing junk,
wrong shape, non-string item) remain `Corrupt`. Unknown `v` remains `UnsupportedVersion`. Legacy
newline semantics are preserved ONLY for values that are not brace-starting (origin/format
established as legacy by the absence of any envelope shape). Brace-starting legacy examples
(`"{placeholder}\nsecond_item"`, literal `{"a":1}`) are handled by the explicit ambiguity policy:
preserved, surfaced, never discarded.

## Failing-first regressions (red on repair branch, never on main)

1. Corrupt requiredPrerequisitesJoined truncated payload -> toEpisode preserves raw/integrity;
   actual applicability is ineligible and formatter cautions; save/toEntity cannot erase raw data.
2. Corrupt userConstraintsJoined OR stepsJoined -> toRevision preserves raw/integrity and actual
   lifecycle rejects; round-trip writes retain original or fail explicitly.
3. Legacy brace-starting multiline, JSON-shaped single-line, ambiguous envelope-shaped literal,
   malformed versioned containing raw newlines, unknown version, trailing junk. Tests encode the
   chosen provenance policy honestly, not broadened Legacy fallthrough.
4. Known-format valid encode/decode brace/quote/newline round-trip and repeat migration; healthy
   empty lists remain healthy.
5. Later-field corruption leaves entire row unmutated; other model transformations do not lose
   integrity status.

Red must demonstrate behavior failure, not a missing new API/compilation error. Where new model
APIs make red-first impractical, use an existing public boundary reproduction or explicitly report
the evidence limit; do not manufacture a red run.

## Delivery / gate

- Repair branch `fix/step1-codec-integrity` (CONTRIBUTING prefix `fix/`), PR against main.
- Both Actions run against the exact repaired code SHA via `workflow_dispatch` (curl + GITHUB_TOKEN;
  gh CLI not installed). Red regression commits stay on the branch, never pushed to main.
- Independent review of the actual diff. Full commit list, base/head compare, red/green run links,
  precise test results, schema-unchanged check, each acceptance PASS/PARTIAL/BLOCKED with production
  caller evidence. JVM evidence is NOT device/DB upgrade verification.
- Do not merge until the independent gate verifies scope acceptance. Green CI is not blanket
  completion. STOP after Job 1 review handoff; do not dispatch Job 2 or Stage 2.

## Acceptance checklist (to be filled with evidence)

- [ ] A. Codec: explicit Ambiguous outcome; no malformed->Legacy; unprefixed envelopes handled;
      lossless round-trip; idempotent migration; unresolved bytes preserved.
- [ ] B. Non-persisted integrity metadata on TaskEpisode/SkillRevision; raw strings retained;
      consumers/copies preserve it.
- [ ] C. Fail-closed: episode ineligible-to-apply consumed by real preflight path; formatter fixed
      caution; lifecycle rejects any unresolved list field; constraint comparison audited.
- [ ] D. Write preservation: unresolved rows/fields not overwritten with empty/new envelope.
- [ ] E. Whole-row migration: NEEDS_ATTENTION untouched in memory+disk; idempotent; later-field case.
- [ ] Schema unchanged (default.json blob identical).
- [ ] Red regressions demonstrated on branch; green on repaired SHA; independent review.

## Bridge #38 recovery checkpoint (2026-10-20)

- Recovered state: no orchestrator checkpoint row existed for parent github-38 (handleit.db has no parent/child or checkpoint table); recovery used the published red tests + #38 instructions, not an invented state.
- Worktree /home/teddy/HandleIT-Android-work was clean on branch fix/step1-codec-integrity at HEAD 66887f07 (one commit ahead of red commit 02e8f029, unpushed at recovery time). No stashes, no untracked task files, no uncommitted changes to preserve.
- Commit 66887f07 already implemented fix areas 1 (entity encodeField preserves rawStored unconditionally; originalRaw map removed; no DB read/put race; id=0 covered), 2 (preflight gates INELIGIBLE_UNRESOLVED_INTEGRITY before emitting resolution/cause; bounded fixed caution), 3 (bounded fixed diagnostic codes, no parser messages), and 5 (destructive-default test removed; guard test removed with deleted UnresolvedWriteGuard).
- Remaining work at recovery: fix area 4 (explicit PARTIAL/BLOCKED provenance statement for envelope-shaped legacy literal collision) plus non-blocking hardening (rawStored empty guard, field-name constants, unused catch bindings, stale CHANGELOG/index wording describing deleted originalRaw/resavePreservingUnresolved/UnresolvedWriteGuard).
- Next edits: (a) add PARTIAL/BLOCKED provenance statement + durable-discriminator design decision (documentation only, no new schema); (b) harden encodeField against empty rawStored; (c) replace hardcoded field-name literals in TaskEpisodeRetrieval with entity constants; (d) fix unused catch bindings; (e) correct stale docs.

## Envelope provenance: durable-discriminator / migration design decision (recorded, NOT implemented)

- Problem: under the fixed schema a legacy literal that is byte-identical to a versioned envelope cannot be distinguished from a real envelope. This is the one PARTIAL/BLOCKED case in the codec (see StringListCodec KDoc "Envelope-shaped legacy literal collision: PARTIAL / BLOCKED").
- Decision: do NOT attempt to solve this with more parsing heuristics. Heuristics cannot prove historical intent and would risk misclassifying healthy current writes.
- Durable option 1 (preferred, future): add a persistent per-field format discriminator (a small enum/int column or a sentinel-prefixed envelope version) so provenance is recorded at write time and never inferred. This is a schema change (new column / new entity field) and is explicitly OUT OF SCOPE here; it must go through the normal ObjectBox schema-change review (default.json change, UID assignment, migration).
- Durable option 2 (alternative): a one-time migration that stamps every existing row's provenance by re-encoding through the codec and recording the decode outcome, so future reads never need to infer. Also a schema/data change, out of scope here.
- Interim behavior (this repair): preserve unresolved bytes verbatim (Corrupt/Ambiguous), never rewrite them, and treat the successfully-parsed collision as an envelope with the honest structural-inference caveat. No universal-compatibility claim is made.
- This decision is recorded so a future schema-change task can pick it up; it is NOT implemented in this repair and no new schema is added.
