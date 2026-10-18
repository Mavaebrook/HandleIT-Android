package com.ai.assistance.operit.data.learning

import com.ai.assistance.operit.core.tools.ToolResultData
import com.ai.assistance.operit.core.tools.ADBResultData
import com.ai.assistance.operit.core.tools.TerminalCommandResultData
import com.ai.assistance.operit.core.tools.HiddenTerminalCommandResultData
import com.ai.assistance.operit.data.model.ToolResult

/**
 * Scope coordinates for an observation produced from a tool result (Step 1, Area 2).
 * Any field the runtime does not actually know is the empty string (unknown), never guessed.
 */
data class ObservationScope(
    val memorySpace: String,
    val projectScope: String = "",
    val taskScope: String = "",
    val executorId: String = "",
    val environment: String = "",
    val sessionId: String = ""
)

/**
 * Minimal, real, deterministic producer of [ObservationRecord]s from a tool execution result
 * (Step 1, Area 2).
 *
 * It records ONLY what the structured result fields actually support:
 *  - "tool.execution.status" = BooleanValue(result.success), trust OBSERVED, source TOOL_EXECUTION.
 *    This is the transport-level execution status the executor produced.
 *  - "shell.exitCode" = IntValue(exitCode), trust OBSERVED, ONLY when the result carries a
 *    structured exit code (ADBResultData / TerminalCommandResultData / HiddenTerminalCommandResultData).
 *
 * It deliberately does NOT emit a "task.succeeded" or "shell.succeeded" observation from
 * result.success or from exitCode == 0: tool transport success does NOT imply that a shell operation
 * or the desired task succeeded. Those facts remain unknown. OS permissions and file existence are
 * never inferred here.
 *
 * Returns null when there is nothing structured to record.
 */
object ToolObservationProducer {

    const val PREDICATE_TOOL_EXECUTION_STATUS = "tool.execution.status"
    const val PREDICATE_SHELL_EXIT_CODE = "shell.exitCode"

    fun fromToolResult(
        toolName: String,
        callId: String,
        result: ToolResult,
        scope: ObservationScope,
        nowMs: Long
    ): ObservationRecord {
        return ObservationRecord(
            eventId = "$callId:$toolName",
            sourceId = callId,
            memorySpace = scope.memorySpace,
            projectScope = scope.projectScope,
            taskScope = scope.taskScope,
            executorId = scope.executorId,
            environment = scope.environment,
            sessionId = scope.sessionId,
            predicate = PREDICATE_TOOL_EXECUTION_STATUS,
            subject = toolName,
            value = TypedValue.BooleanValue(result.success),
            observedAtMs = nowMs,
            validUntilMs = 0L,
            source = ObservationSource.TOOL_EXECUTION,
            trust = TrustLevel.OBSERVED
        )
    }

    /**
     * A separate observation for a structured shell exit code, or null when the result carries no
     * structured exit code. exitCode == 0 is transport-level and does NOT prove the desired task
     * succeeded; it is recorded as a raw exit code only.
     */
    fun shellExitCodeObservation(
        toolName: String,
        callId: String,
        result: ToolResult,
        scope: ObservationScope,
        nowMs: Long
    ): ObservationRecord? {
        val exitCode = structuredExitCode(result.result) ?: return null
        return ObservationRecord(
            eventId = "$callId:$toolName:exit",
            sourceId = callId,
            memorySpace = scope.memorySpace,
            projectScope = scope.projectScope,
            taskScope = scope.taskScope,
            executorId = scope.executorId,
            environment = scope.environment,
            sessionId = scope.sessionId,
            predicate = PREDICATE_SHELL_EXIT_CODE,
            subject = toolName,
            value = TypedValue.IntValue(exitCode),
            observedAtMs = nowMs,
            validUntilMs = 0L,
            source = ObservationSource.TOOL_EXECUTION,
            trust = TrustLevel.OBSERVED
        )
    }

    private fun structuredExitCode(data: ToolResultData): Int? = when (data) {
        is ADBResultData -> data.exitCode
        is TerminalCommandResultData -> data.exitCode
        is HiddenTerminalCommandResultData -> data.exitCode
        else -> null
    }
}
