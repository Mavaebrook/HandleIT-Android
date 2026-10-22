---
repository: https://github.com/Mavaebrook/HandleIT-Android
---

# MCP argument preservation

The executor currently guesses types even for declared strings, reparses nested JSON strings,
and removes null array entries. This can change remote write arguments before dispatch.

## Plan

1. Route the executor through one testable conversion seam using each property's explicit type.
2. Preserve literal strings, structured values, nested JSON types, and null entries; remove raw
   argument logging and heuristic repair of malformed arrays.
3. Add JVM regressions for the production conversion seam and serialized transport payload.
4. Validate with the hosted Android Build and Android Tests workflows, then record their results.

## Acceptance

- Declared strings and arguments without one explicit supported type remain unchanged.
- Valid JSON object/array arguments retain nested strings, numbers, booleans, and nulls.
- Already structured arguments remain unchanged, including list element types and null positions.
- Conversion emits no argument contents to logs.
- Existing transport interfaces and persisted data remain unchanged.

Implementation and regression tests are written; hosted validation is pending. See
[behavior and validation](01-implementation.md).

## CI prerequisite

This PR includes the CI commit from [PR #8](https://github.com/Mavaebrook/HandleIT-Android/pull/8) so its main-targeted merge candidate receives hosted validation. Review this feature commit separately; merge #8 first. The CI prerequisite will disappear from this PR's diff once it reaches main. No Claude bridge job is required.
