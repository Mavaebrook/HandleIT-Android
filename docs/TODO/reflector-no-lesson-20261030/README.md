# Reflector "no lesson" outcome (2026-10-30)

## Problem

Device test, 2026-10-30: the only stored lesson (episode #2) came from a successful test turn that was reviewed only because it was a "complex successful turn" (several tool calls). Nothing went wrong, so the Reflector had nothing to learn; it recorded the user's one-off test instructions as `applicable_constraints` ("Do not fabricate a prior similar-task lessons block; say none if it is absent", ...). Such lessons are retrieved later and injected into unrelated prompts as if they were rules. The Reflector had no way to answer "nothing to learn", so every reviewed turn produced a lesson.

## Change

- Reflector prompts (EN/CN): when the evidence shows no mistake, no user correction, no failed or invalidated tool and no step the user had to supply, return `{"no_lesson": true, "reason": "..."}` instead of inventing a lesson. `applicable_constraints` are rules the user stated for future work of this kind, never the user's request or one-off instructions.
- `ReflectorCurator.reflect` returns `Reflection` (`Lesson` or `NoLesson`), or null on provider/parse failure.
- `ReviewScheduler`: `NoLesson` closes the review (row deleted, nothing saved, reason logged with the trigger). Lessons are saved as before. Failures keep the retry and dead-letter path.

## Not in this change

- Already-stored noise lessons (episode #2 on the owner's device) remain. There is no episode management UI yet.
- Whether "complex successful turn" should trigger reviews at all is left open; with "no lesson" available it now costs one Reflector call and stores nothing when there is nothing to learn.

## Validation

- [ ] Hosted CI: Android JVM tests (`ReflectorCuratorParseTest`, `ReflectorCuratorCancellationTest`).
- [ ] Device: a successful multi-tool turn produces no new lesson (log: `复盘无经验可学`); the Copper Kestrel correction test still produces a lesson with a `past mistake:` line.
