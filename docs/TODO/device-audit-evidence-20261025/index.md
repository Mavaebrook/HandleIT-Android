# Device audit evidence reconciliation — October 25, 2026

## October 26 checkpoint — PR #17 merged; device acceptance pending

Main is `17089b41b4f192968deeb0d36163333c53e222aa`. PR #17 merged on 2026-10-26 at 00:20:06 UTC from `3cff4d3039d18e5522ab82c5be8e79705c04148b`. Comparison with independently reviewed `181ad975ca7b2be01196c0db6f83166a35106e81` changes only the learning-pipeline documentation index; the reviewed application code is unchanged.

Hosted evidence verified once on October 26:
- [Final PR Check 40248115963](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/40248115963): SUCCESS at final PR head
- [Main Android Build 40248851432](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/40248851432): SUCCESS at main 17089b41
- [Main Android Tests 40248851346](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/40248851346): SUCCESS at main 17089b41
- Artifact `operit-android-85`, ID 12619119950, available and not expired at this check
- Main branch metadata confirms protection and required `Candidate checks`; detailed protection settings were not re-queried

Build 85 is available, NOT confirmed installed. Last supplied installed identity remains debug run 84 below until the device operator provides new evidence. The agent subsequently reported active profile `default` from DataStore key inspection and a genuine scheduler startup line. Pass execution and review/episode counts remain NOT VERIFIED. Raw xxd/strings inspection must not be described as a structured protobuf decode.

Next task: [bounded build-85 device validation](../device-runtime-validation-20261026/index.md). Do not repeat APK decoding or broad log searches. Release remains out of scope.

## Historical reconciliation scope

Reconcile the supplied on-device audit with repository snapshot `bf0bdd0cbab840ab25d8f5e8fdeb3751b90c710b`, distinguish observations from inferences, and define the minimum follow-up. This record does not execute device commands, inspect the installed APK/database, or alter application behavior.

The supplied report is the source for device observations. Raw command output and exact command scripts were not supplied here, so device observations have not been independently reproduced. The subsequently supplied build identity is recorded below.

## Last reported installed debug build — run 84

This supersedes the October 20 build previously recalled from memory. Preserve the old identity only as history; do not use it as the current installed build.

- Package: `com.ai.assistance.operit.debug`
- Version: 1.12.2, versionCode 51
- GitHub [Android Build run 84 / 40119139721](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/40119139721): SUCCESS
- Source commit: `28529c0baa0446f1a3a74c8da31133ec7ea4b9b6`, PR #14 G02 integration
- Artifact: `operit-android-84`, artifact ID 12570896270
- Build completed: 2026-10-24 18:20 UTC; GitHub run updated at 18:20:29Z
- Installation: 2026-10-24 22:20 local via pm install -r, as reported by the device agent; timezone offset was not supplied
- APK SHA-256: `5c65ec4a9152da84ceeb2e88ae86a390ed5dfe9f59a89df7eaf7c1392efa1737`
- APK size: 441,519,610 bytes, as reported
- Reported installed base.apk hash equals the local installation source APK hash

GitHub run number, commit, success and artifact metadata were independently checked. Installed-file/local-file hash equality and installation details are device-agent evidence; this documentation pass did not download/hash the artifact or rerun device commands. Keep this evidence distinction without reverting to the obsolete build identity.

Superseded memory: October 20 build, run 39517747866, commit prefix 1794c64fab, reported hash prefix 22eb0b. No full historical hash was supplied.

This correction identifies the debug build only. Release `com.ai.assistance.operit` remains separately reported as 1.12.2 / 51; do not assign the debug APK hash or commit to release.

GitHub comparison from 28529c0b to the repository snapshot bf0bdd0c changes only the learning-pipeline documentation index. The application source cited below is identical between those commits.

## October 25 next check — superseded by October 26 task

The device agent now reports ReviewScheduler, PendingReviewEntity/Repository and TaskEpisodeEntity/Repository present in the debug APK, withdrawing its original feature-absent claim. The remaining questions are runtime execution, selected profile, queue/episode counts and release-code identity.

Current source stores selection in Preferences DataStore named `user_preferences`, with keys `active_memory_space_id` and `memory_space_list` in UserPreferencesManager. The expected standard file is `files/datastore/user_preferences.preferences_pb`; verify the device file and decode only needed values through a supported read-only method. The `memory_space_profile_documents` shared-preferences file stores migration schema_version, not selection. No keys in shared_prefs therefore does not establish absence of selection.

MyObjectBox registration can establish registered entities, not scheduler startup or execution. Do not repeatedly retry the same failing recursive decompiler search. Inspect the specific application startup call and relevant classes separately; leave runtime counts and execution NOT VERIFIED until directly observed. SQLite tools do not query ObjectBox data.

## Original audit observations and limits

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

Claims of no persisted review queue, exactly three materialized entities, no learning records, zero scheduler executions, and an upstream-only installed build are not established. Current repository source contradicts several of those structural claims. The subsequent build-identity correction and compiled-class evidence above narrow the gap; runtime execution and counts remain unverified.

## Original follow-up checklist — build identification now supplied

Step 1's debug-build identity is superseded by the correction above; do not restart identification unless new evidence conflicts. Continue with profile selection and runtime diagnostics, keeping release separate.

1. Identify the exact package/user, versionName/versionCode, installed APK path/hash and signing identity; tie the APK to a hosted artifact and source commit where possible. A version label alone is insufficient. Keep debug and release evidence separate.
2. Verify the active profile from authoritative preferences/UI at the same timestamp and identify the actual BoxStore directory using that build's source. Do not infer selection from an existing user.md file.
3. Inspect the matched APK's generated ObjectBox model/entity registrations or use supported, read-only in-app diagnostics. Obtain box counts through the matching ObjectBox API in the app's existing store, with profile/path and errors recorded. Query PendingReviewEntity by status and TaskEpisodeEntity counts when the matched model includes them; do not substitute strings counts. If this access is unavailable, report NOT VERIFIED.
4. Validate the log parser against one known genuine line, then use exact tags from the matched source. Capture command exit statuses separately from downstream pipelines. Establish the retained time window and logging settings.
5. Only after build identity and observability are established, define a controlled review-trigger acceptance test: observe enqueue, processing and persisted episode plus retrieval. Report empty queue, inaccessible store, disabled feature and execution failure as different outcomes. A read-only audit should stop before generating events or mutating device data unless that test is authorized.
6. Do not clear data, reinstall, migrate schemas, reset IDs or change signing to make the audit pass. Do not diagnose the release build by extrapolating from debug.

## Repository integration status

PR #7 already merged at 7cb417d0757795a8dec02938784bd82edd33a04c. The earlier #4/#5 integration at f5ddc3fb passed Android Build 40065402652 and Android Tests 40065402642. Later PR #6 merged at 5e7582dfa05bae733273a2fea8eae180c29df3d2; PR #14 merged at 28529c0baa0446f1a3a74c8da31133ec7ea4b9b6. This audit does not independently security-review those later implementations. PR #14 documents a model-dispatch gate and explicitly retains direct-entry coverage gaps; it must not be described as an app-wide sandbox.

This documentation reconciliation requires review; it does not authorize another merge or claim device acceptance.
