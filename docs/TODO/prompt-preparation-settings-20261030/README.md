# Prompt preparation settings (2026-10-30)

## Why

Device log, 2026-10-30 (deepseek-v4-pro, thinking on): the preparation call took 7–14 s on successful turns and produced 700–1900 output tokens per brief, mostly thinking. Two of eight turns hit the fixed 15-second timeout (elapsed 17.8 s and 22.4 s) and continued without a brief. Preparation inherited the chat's thinking setting and had a hard-coded timeout, so neither could be adjusted.

## Change

Settings › Context & Summary › **Prompt Preparation** (global, stored in `ApiPreferences`):

| Setting | Default | Effect |
| --- | --- | --- |
| Enable prompt preparation | on | Off skips the preparation call entirely; the turn is sent as prepared by the normal pipeline (system prompt, lessons, history). |
| Thinking during preparation | **off** | Independent of the chat's thinking mode, which is unchanged. |
| Preparation timeout | 15 s (5–60 s, steps of 5) | On timeout the turn continues without a brief, as before. |

`EnhancedAIService.preprocessRequest` reads the three settings per turn; its success and timeout log lines now include `thinking=` and `timeoutS=`.

Behavior change: preparation no longer uses thinking unless the user turns it on.

## Validation

- [ ] Hosted CI: compile and JVM tests.
- [ ] Device: with defaults, log lines `PromptPreprocessor v1 ... thinking=false timeoutS=15` and lower `elapsedMs` than before; changing the slider changes `timeoutS`; disabling the switch removes `PromptPreprocessor` lines.
