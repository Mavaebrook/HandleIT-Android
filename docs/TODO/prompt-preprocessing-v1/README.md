# In-app prompt preprocessing v1

Base: c37ccd9b, including surgical runtime repair PR #20. Repository: Mavaebrook/HandleIT-Android; integration branch: main.

## Plan and acceptance

- Add one bounded preparation call before each top-level CHAT request, using the same resolved chat configuration, model parameters and thinking setting. Keep tool continuations and non-chat functions outside this path.
- Use an isolated provider instance through the built-in service factory. No extra credentials, model selector, gateway, tool execution or learning writer.
- Produce a validated execution brief from the current request, bounded recent context, retrieved episode evidence and exposed tool names. Preserve the current user message verbatim. Keep derived content below policy authority.
- Replace the consumed retrieved-episode fence with the brief. Retain source evidence inside the brief so provenance is not dependent on generated citations.
- Stop visibly on invalid output or provider failure; propagate cancellation and prevent a cancelled preparation from starting execution. Record usage through existing accounting and log only bounded diagnostic metadata.
- Add hosted regressions for parsing, prompt preservation, trust boundaries, model selection and cancellation. Update CHANGELOG and this document at validation milestones.
- Run GitHub-hosted Candidate checks and Android Build. No local Gradle. Install preserving app data/signing, then verify actual outbound brief, one preparation per turn, ordinary-chat recall and cancellation on device.

## Status

Implementation and 13 focused regression cases are present in PR #21. Source review and changed-file whitespace checks passed; hosted checks, signed APK and device acceptance remain pending. No schema, signing, package identity, separate Operit or bridge migration changes.

## Usage and limits

No separate provider setup is required. A top-level CHAT turn uses the resolved chat configuration, including per-chat model selection, sampling/custom parameters and thinking mode. Preparation uses a separate service instance; local tools and provider-hosted web search are disabled for this call only. Existing rate limits and token accounting apply. Main-chat tool access is unchanged.

Each user turn adds one model call. Tool-result continuations and delegated subtasks do not add preparation calls. The original request is retained; the execution brief and original episode evidence are attached as USER-level derived data, not system policy. Memory-operation suggestions are advisory: v1 does not enforce them or implement additional retrieval rounds.

The preparation input includes up to six recent non-system turns, 2000 characters each; a 12000-character policy excerpt; up to 8000 characters of source episode evidence; and up to 100 exposed tool names. The current user turn is not truncated: inputs above 32000 characters stop visibly. The JSON brief is limited to 6000 field characters; generation has a 120-second timeout and a 128-KiB character buffer cap including thinking output. Malformed output or failure stops the turn rather than silently sending an unprepared request. Retry is an explicit user action.

Provider usage is recorded through the existing service wrapper. The `PromptPreprocessor v1` log records execution ID, latency, counts and length, not generated text or credentials. Existing application/provider logging behavior is unchanged. The compiler is request-local and performs no memory writes.

## Device acceptance

Install the signed debug artifact over the existing HandleIT app without clearing data. In a fresh conversation, confirm one preparation call precedes the execution call, both use the selected DeepSeek model and generation settings, and the actual execution request contains the brief plus the original request. Use a known episode and an explicit correction to check provenance and correction preservation. Invoke a read-only tool and confirm continuation does not make a third preparation call. Cancel during preparation and confirm execution never starts. Repeat with a chat-specific model override. These are device acceptance checks, not claims established by JVM tests.
