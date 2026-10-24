---
repository: https://github.com/Mavaebrook/HandleIT-Android
branch: fix/memory-retirement-governance
pr: 4
status: published-awaiting-hosted-validation
---

# D02 restoration eligibility repair — 2026-10-23

## Plan

1. Reconcile PR #4 at `1323cb2e` with main `f12cfe00`, preserving both histories. PR #5 stays separate and unmerged.
2. Isolate existing metadata persistence in a BoxStore-backed `MemoryMetadataStore` used by `MemoryRepository`, allowing real-store tests without Android service initialization.
3. Atomically invalidate the restorable marker on ordinary status writes, including INACTIVE -> INACTIVE. Check and initialize missing status within one transaction; never overwrite an existing status.
4. Require a recorded prior status, current INACTIVE status, positive retirement timestamp/source ID, ACCEPTED reason and CORRECTS/SUPERSEDES link type. Retain historical audit when consuming the marker.
5. Add ten native ObjectBox JVM regressions: reopen durability, same-status deactivation, successful/repeated/refused restore, invalid provenance, rollback and status initialization.
6. Correct F4 pending-decision, callInTx, audit-retention and CI wording. Preserve `correctionApplicable = false` and the lack of a production restore caller.
7. Publish, run hosted PR validation, and update the handoff and Drive copy. Leave the PR open for independent review.

## Status-writer inventory

The PR #4 repository writes status through reinforcement initialization, deliberate deactivation, audited retirement and restoration. Main code search found MemoryProperty construction only in MemoryRepository and its model; the PR diff adds no separate writer. Import/update paths do not import or mutate metadata. Ordinary metadata status writes now share invalidation; retirement and restoration own their marker lifecycle separately.

## Test setup

The existing ObjectBox 5.3.0 plugin supports native JVM stores and provides its host library; see [ObjectBox documentation](https://docs.objectbox.io/android/android-local-unit-tests). Tests use generated MyObjectBox in a temporary directory, with no new dependency or schema. All execution is through GitHub-hosted Android Tests, never locally.

## Publication checkpoint

Implementation and ten native tests prepared; hosted validation pending at publication. Full retrieval, Android service initialization and historical phone upgrade preservation remain unverified. Structural audit validation does not establish semantic correction evidence or source existence. No automatic retirement, restoration UI, schema/UID change, signing change or PR merge.
