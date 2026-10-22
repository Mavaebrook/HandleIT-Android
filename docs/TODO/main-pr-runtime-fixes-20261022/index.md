---
repository: https://github.com/Mavaebrook/HandleIT-Android
---

# Main PR validation and runtime fixes

Status: implementation in progress, 2026-10-22. Base: `5b3277cf`.

1. Route PR checks targeting `main` through the existing Android Build and Android Tests workflows using reusable calls. Preserve the merge-candidate SHA, selected-lane gate, and signed push/manual builds. PR builds must receive no signing secrets and produce no distributable APK upload.
2. Preserve MCP argument strings and nested JSON values; add regressions at the production payload seam.
3. Propagate Reflector/Curator cancellation; preserve ordinary failure behavior and cover both calls.
4. Publish reviewable PRs, inspect hosted validation, and record exact commits, results, and pending administrative work.

Acceptance: appropriate checks run on the merge candidate; required selected lanes cannot be skipped successfully; signing remains unchanged for push/manual builds. Required-check enforcement needs repository administrator configuration after verification. Runtime fixes must pass hosted JVM tests before merge. No local Gradle builds or tests will run.

See [implementation and validation](01-progress.md). Existing D02/D04/D05 reviews and the model handoff remain separate.

