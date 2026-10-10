# Plan: Rebrand completion — default assistant persona + English locale gaps
Date: 2026-10-10
Status: IN PROGRESS — plan written before edits (per handleit-architect)
Repo: Mavaebrook/HandleIT-Android (branch main, HEAD 5c5005836a9c4040d8b97f368ce8ad1d77e91bb5, clean tree)
Scope: fix the two remaining user-facing rebrand gaps approved by owner ("#2: yes").

## Project truth (re-verified this session)
- Branch main, HEAD 5c50058, working tree clean before edits.
- Remote origin embeds a GitHub PAT (ghp_3GU...) — flagged for rotation (separate remediation).

## Gap #1 — default assistant name/persona still "Operit" (root-caused)
- CharacterCardManager.kt:98  const val DEFAULT_CHARACTER_NAME = "Operit"  (used at :188)
- CharacterCardBilingualData.kt:26  "你是Operit，一个全能AI助手，旨在解决用户提出的任何任务。"
- CharacterCardBilingualData.kt:28  "You are Operit, an all-purpose AI assistant designed to help users solve any task."
- CharacterCardBilingualData.kt voice persona: "你永远是 Operit，..." / "You are always Operit, ..."
Fix: replace "Operit" -> "HandleIT" in the above name + persona strings only.
Note: avatar asset path file:///android_asset/operit.png is internal (not user-facing); left unchanged this pass.

## Gap #2 — re-scoped (owner premise refined)
Finding: whole-repo grep for "免责" = ZERO matches; no standalone "Chinese disclaimer" string in code.
- agreement_disclaimer IS already localized (zh/en/es/id/ko/ms/pt-rBR/ro) — no action.
- Actual remaining English-locale gap: 6 strings present in default values/ but MISSING from values-en,
  so English users fall back to Chinese text:
  1. backup_format_csv            "CSV"
  2. backup_format_csv_desc       "表格格式，适合数据清洗和迁移"
  3. data_recovery_configuration_file_domain_issue  "配置文件内容异常：%1$s，可以在保全原件后修复"
  4. tool_permission_confirmation_timeout  "工具执行确认超时。"
  5. tool_permission_execution_denied      "工具执行权限被拒绝。"
  6. tool_permission_overlay_required_for_confirmation  "需要悬浮窗权限才能确认工具执行。"
Fix: add English translations for these 6 keys to values-en/strings.xml.

## Edits to apply
1. CharacterCardManager.kt:98 Operit -> HandleIT.
2. CharacterCardBilingualData.kt: replace 4 "Operit" persona occurrences -> "HandleIT".
3. values-en/strings.xml: insert 6 missing keys with English text (backup_format_csv/csv_desc after
   backup_format_txt_desc; data_recovery_..._domain_issue after ..._file_check_failed;
   3 tool_permission_* after tool_permission_operation).

## Hard constraints (unchanged)
- Do NOT touch self-learning (entity12 / MemoryLedger).
- Do NOT regress the notification patch or existing HandleIT branding.
- Installed APK is never source; only GitHub repo is truth.
- Build APK ONLY via the designated GitHub Actions workflow (no local/proot builds).
- Package name stays Option A: com.ai.assistance.operit / .debug (unchanged this pass).
