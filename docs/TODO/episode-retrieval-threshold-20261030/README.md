# Episode retrieval threshold and past-mistake rendering (2026-10-30)

## Problem

The goal of the self-learning loop is to recognize a task similar to a past one and put the past mistake in front of the model. Two things stopped that even when a good lesson was stored:

- **Retrieval had no similarity floor.** Any episode sharing a single meaningful word with the request was a lexical match (`score > 0`). When an embedding provider was configured, every nearest-neighbour hit received a fixed `VECTOR_CANDIDATE_BONUS` and qualified automatically. Nearest-neighbour search always returns the nearest episodes however unrelated, so the top-k slots filled whenever any episode existed.
- **The mistake was never rendered.** `TaskPreflightFormatter` rendered the task, resolution, cause and missing prerequisites, but not `failureSummary` (what went wrong) or `applicableConstraints`.

## Change

- **Lexical qualification** (`TaskEpisodeRetrieval.qualifiedLexicalScore`): an episode is a lexical match only when it shares at least `MIN_LEXICAL_SHARED_TOKENS = 2` distinct meaningful tokens with the request and at least `MIN_LEXICAL_SCORE = 0.25` of the request's meaningful tokens. The two-token floor mirrors the existing prerequisite-applicability floor.
- **Vector qualification**: `TaskEpisodeVectorIndex.searchEpisodeSimilarities` returns the cosine similarity of each nearest episode (`VectorIndexManager.findNearestWithDistance`, additive), and a vector hit qualifies only at `MIN_VECTOR_SIMILARITY = 0.50` or above. The fixed bonus is removed.
- **Merge rule**: a candidate qualifies lexically or by vector; its rank score is its qualifying lexical score plus its qualifying vector similarity, so an episode matched both ways ranks first.
- **Rendering**: each retrieved episode now shows `past mistake:` (from `failureSummary`), then cause, then resolution, then up to three `constraints:`, then the existing "verify before relying on this" prerequisites. Episodes with unresolved stored lists still render only the task and the fixed caution. The block budget is raised from 2000 to 3000 characters so the extra lines do not push out whole episodes.

## Vector threshold calibration

Measured on 2026-10-30 against Qwen3-Embedding-0.6B (Q8_0, llama.cpp, last-token pooling, no query instruction), the model served at `api.peasantware.com`. Seven realistic requests against six episode index texts (task + context + resolution):

| | min | mean | max |
|---|---|---|---|
| intended episode | 0.540 | 0.671 | 0.741 |
| every other pairing | — | 0.313 | 0.485 |
| off-topic requests (weather, recipe, joke, science) | — | — | 0.390 |

The intended episode ranked first in all seven. 0.50 separates the groups. **The value is model-specific**: cosine scales differ between embedding models, so it must be re-measured if the embedding provider changes.

## Behaviour changes in tests

- `TaskEpisodeRetrievalTest`: a single shared word no longer retrieves an episode; two generic words in a long request are not a match; vector-only hits qualify at or above the threshold and are excluded below it.
- `ReplayHarnessTest` / `ReplayFixtures`: the `analogousStructureDifferentVocabulary` fixture used to be retrieved on the incidental word "package". It is no longer retrieved lexically (`expectedTopEpisodeId = null`); the same episode is still retrieved when the vocabulary matches. The structural-match limitation is now left to the vector generator. Recall over the remaining fixtures with an expected top episode stays 1.0.
- `TaskPreflightFormatterTest`: bounds updated to 3000; new tests for the mistake line, its order, constraints, and the fail-closed case.

## Not in this change

- Asymmetric query instructions for Qwen3 embeddings (could raise related-pair scores further).
- A device-side check that a real stored lesson reaches a real prompt. That needs PR #24 (evidence capture) merged, lessons accumulated, and request logging (planned model API proxy).

## Validation

- [ ] Hosted CI: Android JVM tests.
- [ ] Device: with the embedding endpoint configured, a request unrelated to any stored lesson gets no `<retrieved_memory>` block; a request similar to a stored failed task shows its `past mistake:` line.

No local, VPS or device build was run; builds and tests run only in GitHub Actions.
