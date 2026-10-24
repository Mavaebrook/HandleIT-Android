package com.ai.assistance.operit.core.tools

import com.ai.assistance.operit.data.model.AITool

/**
 * G02 hook: enforces project constraints at tool-dispatch time.
 *
 * Registered on [AIToolHandler] startup. It delegates to [ProjectConstraintPolicy] and maps a
 * [ProjectConstraintPolicy.ProjectConstraintDecision.Deny] to a bounded, deterministic
 * [AIToolHookDecision.Block] whose reason is a stable code (never the command, arguments, or paths).
 */
class ProjectConstraintHook : AIToolHook {

    override fun onToolCallIntercept(
        tool: AITool,
        context: ToolInvocationContext?
    ): AIToolHookDecision {
        val invocationContext = context ?: ToolInvocationContext()
        return when (val decision = ProjectConstraintPolicy.decide(invocationContext, tool)) {
            ProjectConstraintPolicy.ProjectConstraintDecision.Allow -> AIToolHookDecision.Allow
            is ProjectConstraintPolicy.ProjectConstraintDecision.Deny ->
                AIToolHookDecision.Block(decision.reasonCode)
        }
    }
}
