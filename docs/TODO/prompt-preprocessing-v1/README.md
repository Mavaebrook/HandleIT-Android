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

Implementation in progress. Hosted checks, APK and device acceptance pending. No schema, signing, package identity, separate Operit or bridge migration changes.
