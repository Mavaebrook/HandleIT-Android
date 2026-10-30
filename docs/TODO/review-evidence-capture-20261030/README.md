# Review evidence capture (2026-10-30)

## Problem

The self-learning goal is to recognize a task similar to a past one and surface the mistakes and missing steps from last time. That requires each stored lesson (`TaskEpisode`) to name the mistake. It did not, because the Reflector never saw it:

- `ReviewScheduler` called the Reflector with `taskSummary + "\n" + contextSummary`: the final reply and the latest user message, unlabeled.
- The trigger reason and outcome were stored on the pending review but never passed to the Reflector.
- The reply the user corrected (`previousAssistantTurnContent`) was used only to detect a correction marker, then discarded.
- Failed tools were counted (`failedToolResultCount`) but their names and error text were not kept.

So on a correction turn the Reflector saw the correction and the already-fixed answer, and `failure_summary` / `cause_summary` were reconstructed by guesswork. Reviews are drained later in batches, so this evidence cannot be recovered at drain time; it must be captured when the turn ends.

## Change

- `TurnObservationAccumulator` now also records each failed tool result (`toolName`, `error`, result text) and the names of tools invalidated by truncated tool rounds.
- `ReviewEvidence` (new, `data/learning/ReviewEvidence.kt`) is captured in `finalizeAssistantResponse` from the same observations used for the review signals: user request, previous assistant reply, final reply, matched correction marker, explicit-correction flag, failed tool records, invalidated tools, and counts. Text is bounded (4000 chars per conversation text, 1000 per tool error/result, 10 tool records); every bound that cut something is listed in `truncated_fields`.
- `PendingReviewEntity` gains one property, `evidenceJson` (ObjectBox property id 12, added to `app/objectbox-models/default.json`). No existing entity, property id or UID changes.
- `ReflectorInput.build` gives the Reflector a labeled JSON object: `evidence_status`, `trigger_reason`, `observed_outcome`, `evidence`.
- Rows queued by an earlier build have no `evidenceJson`. They are labeled `queued_before_evidence_capture`, with their two legacy columns reported under the names of what they held (final reply, user request). This is a description of existing data, not a substitute value.
- A stored `evidenceJson` that cannot be decoded throws inside the review attempt, so the row follows the normal retry and dead-letter path and stays inspectable. It is never read as empty.
- The saved lesson keeps the exact Reflector input in a new `TaskEpisodeEntity.reflectorInputJson` property (ObjectBox property id 14). The pending review, and the evidence on it, is deleted once the lesson is saved; without this the lesson would survive but not what it was learned from. Each lesson is therefore an (evidence, lesson) pair, which is the data needed to fine-tune or distill a small local model later. The value is never rendered into prompts or used for retrieval scoring.
- The Reflector prompts (EN/CN) describe the input and ask for the mistake in `failure_summary`, a cause grounded in the evidence (hypothesis unless shown), and the missing step in `required_prerequisites`.

## Not in this change

- Retrieval still accepts any single shared token as a match, and `TaskPreflightFormatter` still does not render `failureSummary` or `applicableConstraints`. That is the next step: the stored mistake has to reach the prompt.
- `PromptPreprocessor` is unchanged. Whether it is still needed is to be decided after the lesson reaches the prompt directly.
- There is still no explicit "mark wrong" UI control, so `explicit_correction_action` stays false in production.

## Validation

- [ ] Hosted CI: Android JVM tests (`ReviewEvidenceTest`, updated `ReflectorCuratorCancellationTest`).
- [ ] Hosted CI: Android build (ObjectBox model change).
- [ ] Device: install over the existing app data; trigger a correction turn (e.g. "that's wrong, …") and a failed tool turn; confirm the resulting episodes name the mistake.

No local, VPS or device build was run; builds and tests run only in GitHub Actions.
