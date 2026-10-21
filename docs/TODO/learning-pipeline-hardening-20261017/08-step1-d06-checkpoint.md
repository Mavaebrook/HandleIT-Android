# Step 1 — D06 checkpoint

## Branch and commits

- Branch: `fix/step1-d06-safe-memory-rendering`
- Base: `origin/main` at `838deba5c3f7ea86c71265046a09a039e86a0d94` (PR #2 merged)
- Implementation commit: `f91b43d5ce1fe83dbb1dab6a3b9a2b586f683b77` (D06 renderer + tests + plan doc)
- Documentation commit: `5340951b5ac35195b37221ea2c868837d6153767` (index, CHANGELOG, G02 audit doc)
- Test fix commit: `dce76750fb05d666354867e2b032f83013bf497d` (unresolved-row test builds RowListIntegrity directly to avoid the JVM android.util.Log stub)
- Review fix commit: `adfcf154327dff29ce21d095e2bf19dcb3ce07a1` (escape attribute quotes to block outcome attribute injection; neutralize U+2028/U+2029 line separators)
- Worktree: `/home/teddy/HandleIT-Android-d06`

## Completed criteria

- [x] D06 renderer fenced and sanitized; provenance limited to existing fields; unknown rendered as unknown.
- [x] Whole-block budget enforced without slicing mid-tag.
- [x] Unresolved-integrity gate preserved.
- [x] Regressions added at the renderer seam (10 new tests plus updated existing ones).
- [x] G02 execution-route audit recorded (audit only).
- [x] A01 device evidence recorded as PARTIAL.
- [x] index.md, CHANGELOG.md updated.
- [x] Branch pushed; both hosted workflows dispatched on final SHA.

## Remaining work

- [ ] CI outcome for the second dispatch on SHA `dce76750` (Android Build run 39529855733, Android Tests run 39529857659; pending at checkpoint time).
- [ ] First dispatch on `5340951b` recorded: Android Build SUCCESS (run 39526251675), Android Tests FAILURE (run 39526269306) — 1 of 1612 tests failed, `buildPreflightBlock_unresolvedRow_rendersOnlyTaskAndCaution_noResolutionOrCause`, because the fixture used `TaskEpisodeEntity.toEpisode()`, which calls `AppLogger.e` -> `android.util.Log.e` and throws "not mocked" on the JVM.
- [ ] Fix applied in `dce76750`: the test hand-constructs `RowListIntegrity` via the `episode()` helper's `listIntegrity` parameter, bypassing `toEpisode()`. Both workflows were re-dispatched on the fixed SHA `dce76750`.
- [ ] Pre-merge review found and fixed two injection gaps in `adfcf154`: (a) the model-controlled `outcome` attribute was not quote-escaped, allowing attribute injection that defeated the provenance guarantee; fixed with `sanitizeAttributeValue` escaping `"` and `'`. (b) ASCII-only `\s` let U+2028/U+2029 line separators survive; fixed by explicit neutralization and UNICODE_CHARACTER_CLASS. Three regressions added.
- [ ] PR review and merge (PR left unmerged by design).
- [ ] A01 full verification requires a compatible populated historical baseline, established separately.
- [ ] G02 enforcement implementation (separate task; audit only here).

## CI

- First dispatch (SHA 5340951b): Android Build run 39526251675 SUCCESS; Android Tests run 39526269306 FAILURE (1 test, fixed in dce76750).
- Second dispatch (SHA dce76750): Android Build run 39529855733 (pending); Android Tests run 39529857659 (pending).
- Third dispatch (SHA adfcf154): Android Build run 39530282737 (pending); Android Tests run 39530284722 (pending). This is the final code SHA for review.

## Resume instructions

If this task is resumed: the final code SHA for review is `adfcf154`. Check the run URLs 39530282737 and 39530284722 for the final outcome. If CI failed, read the failing job log, fix on this branch, and re-dispatch both workflows on the new final SHA. Do not run Gradle/build/tests locally. Do not merge the PR without explicit authorization.
