package com.ai.assistance.operit.core.tools

import com.ai.assistance.operit.core.chat.hooks.ProjectIdentity

/**
 * Per-invocation context available to tool hooks at dispatch time (G02).
 *
 * Carries the authoritative project identity resolved from the chat's bound workspace
 * ([ProjectIdentity.resolveFromWorkspace]) plus the raw workspace path and chat id for auditing.
 * Hooks must treat this as best-effort: [ProjectIdentity.UNKNOWN] is the honest result whenever no
 * trustworthy workspace could be resolved, and UNKNOWN must never grant HandleIT authority.
 */
data class ToolInvocationContext(
    val projectIdentity: ProjectIdentity = ProjectIdentity.UNKNOWN,
    val chatId: String? = null,
    val workspacePath: String? = null,
)
