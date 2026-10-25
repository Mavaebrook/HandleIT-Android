# Device audit evidence reconciliation — October 25, 2026

## Scope and plan

Reconcile the supplied on-device audit with repository snapshot `bf0bdd0cbab840ab25d8f5e8fdeb3751b90c710b`, distinguish observations from inferences, and define the minimum follow-up. This record does not execute device commands, inspect the installed APK/database, or alter application behavior.

The supplied report is the source for device observations. Raw command output, the exact command scripts and APK provenance were not supplied here, so those observations have not been independently reproduced.

## Accepted observations and limits

The report says retained logs contain auto-save/search activity and embedded assistant conversation text, but no matching MemoryDecayScheduler lines. It reports release-package run-as access is unavailable. These support an observation gap for the inspected package/window and NOT VERIFIED for inaccessible release data.

Missing log lines do not establish zero executions. Log level, retention, format, early returns, selected process/profile and the expected implementation must be established first. The quoted grep pattern ` DIWEFMemoryDecay` is not an anchored parser of a D/MemoryDecayScheduler tag as written. Empty output with EXIT:0 needs the actual shell/pipeline exit status: grep ordinarily returns 1 for no matches, while a trailing head/echo or explicit error handling can mask it.

Neither ASCII nor UTF-16 strings output enumerates ObjectBox entities or counts records. Zero readable names does not prove absent boxes; positive name counts may be conversation text, metadata or repeated bytes. No MemoryLedger row count or complete on-disk entity inventory has been established.

A default/user.md file shows that profile's files exist, not that it was the selected profile during the observed run. An in-memory map keyed by profile ID alone would not prove physical database isolation.

## What current repository source establishes

All source links are pinned to the snapshot, not asserted to match the installed phone APK.

- [PendingReviewEntity](https://github.com/Mavaebrook/HandleIT-Android/blob/bf0bdd0cbab840ab25d8f5e8fdeb3751b90c710b/app/src/main/java/com/ai/assistance/operit/data/model/PendingReviewEntity.kt) is an ObjectBox entity with pending, processing, failed and dead_letter states. [PendingReviewRepository](https://github.com/Mavaebrook/HandleIT-Android/blob/bf0bdd0cbab840ab25d8f5e8fdeb3751b90c710b/app/src/main/java/com/ai/assistance/operit/data/repository/PendingReviewRepository.kt) opens its box at line 34, persists enqueue at 54 and queries pending/failed rows at 65–75. The current implementation has a persisted review queue.
- [ReviewScheduler](https://github.com/Mavaebrook/HandleIT-Android/blob/bf0bdd0cbab840ab25d8f5e8fdeb3751b90c710b/app/src/main/java/com/ai/assistance/operit/api/chat/library/ReviewScheduler.kt) uses the tag ReviewScheduler, not MemoryDecayScheduler. It starts a 60-second loop at 108–119 and reads a bounded batch from the persisted queue at 206–211. The batch and durable queue coexist; they are not competing architectures. Empty queues return before the processing log.
- [OperitApplication](https://github.com/Mavaebrook/HandleIT-Android/blob/bf0bdd0cbab840ab25d8f5e8fdeb3751b90c710b/app/src/main/java/com/ai/assistance/operit/core/application/OperitApplication.kt) constructs and starts ReviewScheduler at line 229. This establishes source wiring, not successful execution in the inspected APK.
- [TaskEpisodeRepository](https://github.com/Mavaebrook/HandleIT-Android/blob/bf0bdd0cbab840ab25d8f5e8fdeb3751b90c710b/app/src/main/java/com/ai/assistance/operit/data/repository/TaskEpisodeRepository.kt) opens a TaskEpisodeEntity box at 28, persists episodes at 52–56 and exposes count/retrieval at 112/125. Absence of a MemoryLedger string cannot establish that current learning persistence or retrieval is impossible.
- [ObjectBoxManager](https://github.com/Mavaebrook/HandleIT-Android/blob/bf0bdd0cbab840ab25d8f5e8fdeb3751b90c710b/app/src/main/java/com/ai/assistance/operit/data/db/ObjectBox.kt) uses files/objectbox for default and files/objectbox_<profileId> for non-default profiles, in addition to the in-memory map.
- [default.json](https://github.com/Mavaebrook/HandleIT-Android/blob/bf0bdd0cbab840ab25d8f5e8fdeb3751b90c710b/app/objectbox-models/default.json) declares Memory, MemoryLink, MemoryProperty, MemoryTag, DocumentChunk, MemoryAutoSaveCandidate, PendingReviewEntity, SkillRevisionEntity and TaskEpisodeEntity. Entity ID 12 is named PendingReviewEntity in this snapshot. There is no MemoryLedger-named entity in this model. This is repository model evidence, not enumeration of the installed APK or its database; no schema/ID change is proposed.

The durable instruction to preserve entity12/MemoryLedger must not be interpreted as evidence that the installed application contains a MemoryLedger class. Preserve the schema and historical lineage while clarifying terminology; do not rename, recreate or delete entities to satisfy the audit's expectations.

## Revised verdict

The inspected debug build's review execution, queue counts, learning-record counts and end-to-end retrieval remain NOT VERIFIED from the supplied evidence. Release-package internal state remains NOT VERIFIED through the attempted access method.

Claims of no persisted review queue, exactly three materialized entities, no learning records, zero scheduler executions, and an upstream-only installed build are not established. Current repository source contradicts several of those structural claims, but does not prove that source is the installed build.

## Minimum follow-up for the device auditor

1. Identify the exact package/user, versionName/versionCode, installed APK path/hash and signing identity; tie the APK to a hosted artifact and source commit where possible. A version label alone is insufficient. Keep debug and release evidence separate.
2. Verify the active profile from authoritative preferences/UI at the same timestamp and identify the actual BoxStore directory using that build's source. Do not infer selection from an existing user.md file.
3. Inspect the matched APK's generated ObjectBox model/entity registrations or use supported, read-only in-app diagnostics. Obtain box counts through the matching ObjectBox API in the app's existing store, with profile/path and errors recorded. Query PendingReviewEntity by status and TaskEpisodeEntity counts when the matched model includes them; do not substitute strings counts. If this access is unavailable, report NOT VERIFIED.
4. Validate the log parser against one known genuine line, then use exact tags from the matched source. Capture command exit statuses separately from downstream pipelines. Establish the retained time window and logging settings.
5. Only after build identity and observability are established, define a controlled review-trigger acceptance test: observe enqueue, processing and persisted episode plus retrieval. Report empty queue, inaccessible store, disabled feature and execution failure as different outcomes. A read-only audit should stop before generating events or mutating device data unless that test is authorized.
6. Do not clear data, reinstall, migrate schemas, reset IDs or change signing to make the audit pass. Do not diagnose the release build by extrapolating from debug.

## Repository integration status

PR #7 already merged at 7cb417d0757795a8dec02938784bd82edd33a04c. The earlier #4/#5 integration at f5ddc3fb passed Android Build 40065402652 and Android Tests 40065402642. Later PR #6 merged at 5e7582dfa05bae733273a2fea8eae180c29df3d2; PR #14 merged at 28529c0baa0446f1a3a74c8da31133ec7ea4b9b6. This audit does not independently security-review those later implementations. PR #14 documents a model-dispatch gate and explicitly retains direct-entry coverage gaps; it must not be described as an app-wide sandbox.

This documentation reconciliation requires review; it does not authorize another merge or claim device acceptance.
