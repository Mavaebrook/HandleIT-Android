---
repository: https://github.com/Mavaebrook/HandleIT-Android
---

# MCP argument preservation

Before this fix, the executor guessed types even for declared strings, reparsed nested JSON strings,
and removed null array entries. This could change remote write arguments before dispatch.

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

Implementation and regression tests passed PR Check 39708024114 at `ae43e582`; PR #9 merged at `0b00ed20`. See
[behavior and validation](01-implementation.md).

## Integration and validation

PR #8's CI prerequisite merged first, followed by #9. No additional prerequisite merge is pending.

Final main `f12cfe00`: [Android Tests 39809127516](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39809127516) succeeded; [Android Build 39809127467](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/39809127467) succeeded; completed 2026-10-22 16:16:59 UTC, confirmed 2026-10-23. See the [integration record](../repository-handoff-20261021/07-integration-20261022.md) for current provenance.
