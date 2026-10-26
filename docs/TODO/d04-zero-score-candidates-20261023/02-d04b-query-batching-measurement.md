# D04b — Salience query batching: assessment, implementation and measurement

## Scope (from the D04 follow-up index)
D04b salience query batching remained unimplemented and unmeasured after PR #5. ObjectBox 5.3.0 relation queries were the assessed path; a schema change was not established as necessary.

## Old behavior
Graph expansion (`MemoryRepository.runSearchMemoriesWithDebug`, stage 4) took up to 10 top-scoring seeds and, per seed:
1. `sourceMemory.links.reset()` + iteration and `sourceMemory.backlinks.reset()` + iteration (one ToMany relation resolution per direction per seed),
2. per link, `link.target.target` / `link.source.target` ToOne endpoint resolution (one single-entity lookup per link).
For S processed seeds and R considered link rows that is `2*S + R` query round trips, all on the IO dispatcher.

## Intent
Load all seeds' outgoing and incoming links with ObjectBox relation queries and verify link endpoints with one batch `get`, without touching the schema or the relation semantics.

## New behavior
- `GraphPropagationBatching.propagate(...)`: pure Kotlin engine that computes the same contributions with the same iteration order (seed order, then per seed outgoing then incoming rows ordered by link id) and the same `MemoryPropagationScoring` positive-contribution gate. Dangling rows (endpoint missing from the batch get) and out-of-scope targets are skipped like the legacy null/scope checks.
- `MemoryRepository` now issues:
  - `linkBox.query().in(MemoryLink_.sourceId, seedIds)` — all outgoing links of all seeds,
  - `linkBox.query().in(MemoryLink_.targetId, seedIds)` — all incoming links of all seeds,
  - one `memoryBox.get(endpointIds)` batch existence check.
  That is a constant 3 round trips (only when seeds exist). Stored endpoint ids are read via `ToOne.targetId` without resolving targets.
- The on-device debug line now records the measurement: `Graph expansion: N edges traversed (seeds=..., rows=..., queries legacy=... -> batched=..., dangling=..., outOfScope=..., nonPositive=...)`.

## Measurement
Query round-trip plan (deterministic, asserted in JVM tests):
- legacy: `2*S + R`
- batched: `3` (0 when no seed is processed)
- S >= 2: the batched plan never issues more queries.
- S == 1: batched wins once any link row is considered and costs exactly one extra round trip only for a single seed with no links at all.

Assessment notes:
- No schema change required: batching uses the existing relation-id properties (`MemoryLink_.sourceId` / `MemoryLink_.targetId`).
- `QueryBuilder.link(RelationInfo)` joins were assessed and are unnecessary here: the propagation needs per-row link ids and weights, not target-side conditions.
- Runtime timing on device is not measured; the round-trip arithmetic is the measured quantity. Tests run in hosted JVM CI only.

## Validation
Branch `fix/d04b-salience-query-batching`, PR #18 against `main`. Hosted PR Check / Android Build / Android Tests results recorded after the hosted run.

[DONE]