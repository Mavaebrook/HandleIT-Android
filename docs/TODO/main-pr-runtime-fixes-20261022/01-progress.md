# Implementation and validation

Updated 2026-10-22. Base main: `5b3277cf`.

## Main PR validation

Implemented: main target filter, reusable named Android workflows, preserved merge-candidate checkout and aggregate gate, instrumentation source compilation input, PR signing/upload guards. Main push and manual signing behavior is unchanged.

Validation: source review in progress. Hosted PR checks pending publication. No local build or test ran. Required-check enforcement is not configured by these files; an administrator must require `Candidate checks` and up-to-date branches on main after validation.

## Runtime work

MCP argument preservation and Reflector/Curator cancellation are being implemented as separate changes. Do not mark these complete from the CI-only PR. Each change will include focused JVM regressions and a task record. D06 and G02 remain partial; no security enforcement is added here.

## Resume

Inspect the PR head and its latest Candidate checks run before merging. A successful older workflow run does not validate a newer commit. If the CI change has not merged, dependent runtime PRs must retain the CI prerequisite or wait for it before validation. Do not dispatch another Claude-backed bridge job while its credits remain exhausted.
