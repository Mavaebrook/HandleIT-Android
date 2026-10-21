package com.ai.assistance.operit.core.chat.hooks

/**
 * Runtime architect-rules hook.
 *
 * Pins the durable project requirements (see AGENTS.md "Project requirements") into the agent
 * runtime so they survive task resume and context compaction. These rules are intentionally kept
 * separate from salience-ranked optional memories: decay or semantic retrieval must never remove
 * mandatory development constraints.
 *
 * Scope: only development/workspace tasks (a workspace path is present). Casual conversation is
 * left untouched.
 */
object ArchitectRulesProvider {
    const val HOOK_ID = "builtin_architect_rules"

    private val RULES_BLOCK_EN =
        """
        [Pinned project requirements — HandleIT-Android]
        - Canonical repo: Mavaebrook/HandleIT-Android, branch main. Build/test ONLY via GitHub-hosted Actions (Android Build / Android Tests). Never build or test on VPS, local machine, phone, or PRoot.
        - Preserve explicit user corrections; never silently revert or reintroduce a corrected fact.
        - Plan multistep tasks before editing; keep the active TODO (docs/TODO/) and CHANGELOG.md updated at meaningful milestones, not only at the end.
        - Update the relevant README documentation when behavior or usage changes.
        - Preserve invariants: namespace/applicationId com.ai.assistance.operit, .debug identity, internal Operit classes, licensing, existing data and ObjectBox IDs. Do not modify entity12/MemoryLedger. Preserve the a9d4849 notification snapshot behavior and the existing signing identity/certificate SHA-256. No key replacement, secret rotation, or history rewrite. Never print secrets.
        - Completion gate: before declaring a task complete, reconcile acceptance criteria against actual changes, tests, and documentation. Report unfinished or blocked items honestly; never manufacture a documentation edit where it is irrelevant.
        """.trimIndent()

    private val RULES_BLOCK_ZH =
        """
        [固定项目要求 — HandleIT-Android]
        - 规范仓库：Mavaebrook/HandleIT-Android，分支 main。构建/测试仅通过 GitHub 托管的 Actions（Android Build / Android Tests）执行。禁止在 VPS、本机、手机或 PRoot 上构建或测试。
        - 保留用户明确的更正；不得静默回退或重新引入已被更正的事实。
        - 编辑前先规划多步任务；在关键里程碑（而非仅在结束时）更新当前 TODO（docs/TODO/）与 CHANGELOG.md。
        - 当行为或用法变化时，更新相关 README 文档。
        - 保留不变量：namespace/applicationId com.ai.assistance.operit、.debug 身份、内部 Operit 类、许可、既有数据与 ObjectBox ID。不得修改 entity12/MemoryLedger。保留 a9d4849 通知快照行为以及既有签名身份/证书 SHA-256。禁止更换密钥、轮换密钥或重写历史。禁止打印任何密钥。
        - 完成门禁：在宣布任务完成前，将验收标准与实际改动、测试和文档逐项核对。如实报告未完成或被阻塞的项；不得在无关处伪造文档改动。
        """.trimIndent()

    fun buildArchitectRulesBlock(useEnglish: Boolean): String {
        return if (useEnglish) RULES_BLOCK_EN else RULES_BLOCK_ZH
    }

    /**
     * True when the task is a development/workspace task (a workspace path is present).
     * Casual conversation has no workspace path and is therefore not scoped in.
     */
    fun isDevelopmentTask(workspacePath: String?, metadata: Map<String, Any?>): Boolean {
        val resolved = workspacePath ?: metadata["workspacePath"] as? String
        return !resolved.isNullOrBlank()
    }
}

class ArchitectRulesSystemPromptHook : SystemPromptComposeHook {
    override val id: String = ArchitectRulesProvider.HOOK_ID

    /**
     * Append the pinned architect rules and, when present, the bounded similar-task preflight block
     * to the SYSTEM prompt.
     *
     * D06: the preflight block is now FENCED UNTRUSTED DATA. The block produced by
     * `TaskPreflightFormatter.buildPreflightBlock` already carries its own fixed authoritative
     * framing sentence and its `<retrieved_memory trust="untrusted" ...>` fence, so this hook adds
     * NO framing of its own and appends the block EXACTLY ONCE, separated by `\n\n`.
     *
     * Honest limitation: the block is still delivered inside the SYSTEM message. Fencing and
     * escaping reduce, but do not eliminate, the chance that retrieved text is read as
     * instructions; a true lower-trust message boundary would require provider-level plumbing that
     * does not exist here.
     */
    override fun onEvent(context: PromptHookContext): PromptHookMutation? {
        if (context.stage != "compose_system_prompt_sections") {
            return null
        }
        val workspacePath = context.metadata["workspacePath"] as? String
        if (!ArchitectRulesProvider.isDevelopmentTask(workspacePath, context.metadata)) {
            return null
        }
        val current = context.systemPrompt ?: return null
        val block = ArchitectRulesProvider.buildArchitectRulesBlock(context.useEnglish == true)
        val preflight = (context.metadata["architectPreflightBlock"] as? String)?.takeIf { it.isNotBlank() }
        val appended = buildString {
            append(current)
            append("\n\n")
            append(block)
            if (preflight != null) {
                append("\n\n")
                append(preflight)
            }
        }
        return PromptHookMutation(systemPrompt = appended)
    }
}

class ArchitectRulesSummaryHook : SummaryGenerateHook {
    override val id: String = ArchitectRulesProvider.HOOK_ID + "_summary"

    override fun onEvent(context: SummaryHookContext): SummaryHookMutation? {
        if (context.stage != "before_prepare_summary_prompt") {
            return null
        }
        val workspacePath = context.metadata["workspacePath"] as? String
        if (!ArchitectRulesProvider.isDevelopmentTask(workspacePath, context.metadata)) {
            return null
        }
        val current = context.summaryPrompt ?: return null
        val block = ArchitectRulesProvider.buildArchitectRulesBlock(context.useEnglish == true)
        val appended = current + "\n\n" + block
        return SummaryHookMutation(summaryPrompt = appended)
    }
}

object ArchitectRulesHookRegistrar {
    fun register() {
        PromptHookRegistry.registerSystemPromptComposeHook(ArchitectRulesSystemPromptHook())
        SummaryHookRegistry.registerSummaryGenerateHook(ArchitectRulesSummaryHook())
    }

    fun unregister() {
        PromptHookRegistry.unregisterSystemPromptComposeHook(ArchitectRulesProvider.HOOK_ID)
        SummaryHookRegistry.unregisterSummaryGenerateHook(ArchitectRulesProvider.HOOK_ID + "_summary")
    }
}
