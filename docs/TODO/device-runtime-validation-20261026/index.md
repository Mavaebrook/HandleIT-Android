# Build 85 bounded device validation

Status: READY FOR DEVICE HANDOFF; not dispatched by this session.
Owner: on-device agent. Repository preparation and source review are complete.
Source: main `17089b41b4f192968deeb0d36163333c53e222aa`, PR #17.
Build: [Android Build 40248851432](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/40248851432), artifact `operit-android-85`, ID 12619119950.
Tests: [Android Tests 40248851346](https://github.com/Mavaebrook/HandleIT-Android/actions/runs/40248851346), SUCCESS.
Implementation/extraction reference: [review pipeline audit](../../review-pipeline-audit.md).

## Objective and boundaries

Obtain one bounded observation of the known debug app's active profile/store, persisted review counts, episode count and a naturally scheduled review pass. Preserve all app data and schema IDs. No application changes, forced review triggers, clear-data, migrations, clock changes, release APK investigation or broad scans.

The current session has no connected phone/orchestrator endpoint. This task is prepared in the repository, not claimed to be running on the phone. Installation was previously held for explicit authorization. First determine from existing installation records whether build 85 is already installed; do not install or replace an APK merely to perform this read-only task without that authorization.

## Procedure

1. Read this file and the linked extraction instructions only. Reuse `/sdcard/Download/HandleIT-audit/` records, outside the app's cleanOnExit directory. Do not refetch the old review packet or re-investigate CI triggers.
2. Establish whether the installed DEBUG package `com.ai.assistance.operit.debug` contains build 85 from existing installation evidence. If it remains run 84, report BUILD READY / INSTALLATION PENDING and stop before installation. Version 1.12.2/code 51 alone does not distinguish these builds.
3. For an authorized installation workflow, use only the hosted artifact above, preserve package/signing identity and data, and record the actual APK hash and installed-file match. The GitHub artifact ZIP digest is NOT the APK hash. Current artifact metadata: compressed size 432,238,186 bytes; archive digest `sha256:2607ef108ec62338b9a2fab173d0b76e7e172349f05db41c113dbe11f861062c`. This session has not downloaded or hashed its APK.
4. Once the target build is confirmed installed and operational, launch once normally and note T0, timezone, process/session and the log boundary. Ordinary application scheduling can mutate its own queue; this is passive observation, not a frozen database.
5. Capture the new launch's tagged `[ReviewPipelineAudit]` lines using the documented filter. Record selected profile, store path, presence/open state, every reported review status count, episode count and readErrors. Do not combine lines from earlier launches. Counts from reviews and episodes are separate reads, not an atomic cross-table snapshot.
6. Allow normal scheduling without triggering work. First inspect newly appended canonical scheduler lines after about 90 seconds. If a pass is still running or has not been observed, make at most one further capture at T0+600 seconds. No repeated polling, command loops, forced retries or new chats intended to create work.
7. Report the observed terminal outcome: completed, partial, failed, error or cancelled, together with failedProfiles and available counters. Completed with failedMarked greater than zero is not universal review success. If no terminal event appears within the bound, report NOT OBSERVED WITHIN WINDOW; do not infer never executed.
8. Save a compact result under `/sdcard/Download/HandleIT-audit/` and return it for repository reconciliation. Include only genuine diagnostic lines from this session, not request-body dumps or shell echoes. Never include memory contents, credentials or the whole database.

If the existing store is not open at the launch snapshot, report UNAVAILABLE (store not open). Do not create/open it through a foreign model or force processing to obtain counts. The current reporter runs at startup; waiting alone does not refresh those counts.

## Acceptance record

Return build commit/run and actual APK hash evidence, observation window, active profile/store, review counts by status, episode count, startup-recovery status, pass start/terminal outcome, and read errors. For each field separate OBSERVED, UNAVAILABLE and NOT VERIFIED.

A populated historical-device upgrade preservation test (A01), full retrieval correctness and end-to-end enqueue-to-episode acceptance remain separate tasks. Empty queues and successful compilation do not prove those properties.

## Next repository work

After this bounded observation, prioritize the observed failure if one exists. Otherwise select a separate scoped task from the handoff: D04b query batching measurement or O1 cancellation replay analysis. Do not expand this diagnostic task into schema changes, automatic retirement activation, direct-entry G02 expansion or a new learning architecture.
