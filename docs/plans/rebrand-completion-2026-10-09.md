# Plan: Complete HandleIT Rebrand (sever Operit links + fix icon)

Date: 2026-10-09
Status: VERIFICATION DONE — plan pending owner approval (no edits made yet)
Repo: Mavaebrook/HandleIT-Android (HEAD 6085ce3, shallow clone)

## Project truth (established)
- Notification fix: PRESENT. Commit a9d4849; code at
  StandardSystemOperationTools.kt:645 -> OperitNotificationStore.snapshot(...)
  (Shizuku-free; DebuggerSystemOperationTools.kt:412 confirms store-backed impl).
- App display name: "HandleIT" (values-en/strings.xml app_name + build.gradle.kts resValue).
- About page / loading screen / icon: rebranded at HEAD commit 6085ce3.

## Open defects — Operit links NOT fully severed
1. package: namespace = applicationId = "com.ai.assistance.operit" (app/build.gradle.kts:360,397)
2. Class/theme names: OperitApplication, Theme.Operit, OperitDataDocumentsProvider,
   OperitAssistActivity, OperitNotificationListenerService, OperitNotificationStore,
   OperitVoiceInteractionService, OperitVoiceInteractionSessionService
3. README.md + README.zh-CN.md + Repo_Arch_Basic.md: "Operit AI", links to
   AAswordman/Operit, AAswordman/Operit2, operit.app
4. AndroidManifest.xml:452 android:label="Operit Workflow"
5. values-en/strings.xml backup_location = "...Download/Operit" folder
6. app/src/main/assets/templates/* : many "Operit" strings (READMEs, package.json, Main.java, etc.)

## Logo / icon
- Branded logo asset: app/src/main/assets/logo.svg = white rounded square (#FFFFFF)
  + blue glyph (#3882C7).
- Manifest icon: @mipmap/ic_launcher_simple -> adaptive-icon background = solid
  #FF000000 (black) + dark foreground => INCONSISTENT with logo.svg.
- Reference image (dark screenshot) contains brand blue #3882C7 + white; needs a
  human visual confirm (assistant has no vision; OCR/color analysis only).

## Proposed steps (pending approval)
1. DECIDE package rename scope. Renaming com.ai.assistance.operit is high-risk
   (proot paths, notification listener, deep links). Recommend: keep package name,
   only if owner agrees, OR full rename after a separate impact review.
2. Rename Operit* class/theme symbols -> HandleIT* (internal-only; low user impact).
3. Rewrite README.md / README.zh-CN.md / Repo_Arch_Basic.md (remove AAswordman/Operit, operit.app).
4. Fix strings: backup_location folder + "Operit Workflow" label + template files.
5. Fix launcher icon to match logo.svg (white square + #3882C7 blue glyph).
6. Build APK ONLY via the designated GitHub Actions workflow (architect rule).

## Hard constraints
- Do NOT touch self-learning (entity12 / MemoryLedger) — preserved as-is in snapshot.
- Do NOT regress the notification patch or existing HandleIT branding.
- Installed APK is never treated as source; only the GitHub repo is truth.
