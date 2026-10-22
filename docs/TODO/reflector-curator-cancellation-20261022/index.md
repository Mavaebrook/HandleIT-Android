---
repository: https://github.com/Mavaebrook/HandleIT-Android
---

# Reflector and Curator cancellation

Status: implemented; hosted validation pending. Base: `5b3277cf844c0c198307c60ac0e9c8f79f1b88bf`.

Cancellation currently becomes a null model result inside `ReflectorCurator`. The scheduler can then record a review failure instead of ending cancelled work. Ordinary provider failures must continue to return null.

1. Inspect the Reflector, Curator and scheduler cancellation paths.
2. Propagate `CancellationException` through service lookup and both model calls, while preserving successful parsing and ordinary failure behavior.
3. Add JVM regression coverage through the production Reflector and Curator call paths.
4. Validate through the GitHub-hosted Android Build and Android Tests workflows and record the exact commit and run results.

No scheduler retry policy, persisted data, model selection or public caller signature changes are planned.

## Implementation and validation

Both public role methods now use context-free internal overloads, with service acquisition and the provider call inside the same cancellation-aware boundary. A `CancellationException` escapes unchanged and does not invoke the failure logger. Ordinary provider failures still return null and use the existing role failure logger; service acquisition failures now also use that logger instead of being silently discarded. Prompt construction, functional model routing and parsing are unchanged.

`ReflectorCuratorCancellationTest` exercises both roles with immediate cancellation, cancellation during suspension, ordinary provider failure, successful output and malformed output. These tests have been written but have NOT been run locally. No build or test result is claimed until hosted workflow evidence is recorded.

`ReviewScheduler` already rethrows cancellation before computing review disposition. Its code is unchanged. The tests cover the production role boundary; they do NOT instantiate the Android/ObjectBox scheduler or verify persisted retry counters. The public Android service adapter and existing scheduler cancellation handlers were inspected, and their compilation remains part of hosted validation.

## CI prerequisite

This PR includes the CI commit from [PR #8](https://github.com/Mavaebrook/HandleIT-Android/pull/8) so its main-targeted merge candidate receives hosted validation. Review this feature commit separately; merge #8 first. The CI prerequisite will disappear from this PR's diff once it reaches main. No Claude bridge job is required.
