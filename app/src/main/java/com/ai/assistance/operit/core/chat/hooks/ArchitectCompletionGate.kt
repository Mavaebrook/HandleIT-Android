package com.ai.assistance.operit.core.chat.hooks

/**
 * Pure completion-gate helper (no Android dependencies, unit-testable).
 *
 * Reconciles acceptance criteria against actual changes, tests and documentation before a task is
 * declared complete. If any criterion is unsatisfied the report explicitly states the task is NOT
 * complete, so unfinished or blocked items are reported honestly rather than hidden.
 */
object ArchitectCompletionGate {

    data class CompletionCheck(
        val criterion: String,
        val satisfied: Boolean,
        val evidence: String
    )

    fun evaluate(checks: List<CompletionCheck>): String {
        if (checks.isEmpty()) {
            return "Completion gate: no criteria provided. Task is NOT complete (nothing was verified)."
        }

        val satisfied = checks.filter { it.satisfied }
        val unsatisfied = checks.filter { !it.satisfied }

        val builder = StringBuilder()
        builder.append("Completion gate report:\n")
        builder.append("- Satisfied (${satisfied.size}/${checks.size}):\n")
        if (satisfied.isEmpty()) {
            builder.append("  - (none)\n")
        } else {
            satisfied.forEach { check ->
                builder.append("  - [x] ${check.criterion} — ${check.evidence}\n")
            }
        }
        builder.append("- Unsatisfied (${unsatisfied.size}/${checks.size}):\n")
        if (unsatisfied.isEmpty()) {
            builder.append("  - (none)\n")
        } else {
            unsatisfied.forEach { check ->
                builder.append("  - [ ] ${check.criterion} — ${check.evidence}\n")
            }
        }

        if (unsatisfied.isEmpty()) {
            builder.append("All criteria satisfied. Task may be declared complete.")
        } else {
            builder.append(
                "Task is NOT complete: ${unsatisfied.size} criterion/criteria unsatisfied. " +
                    "Report these unfinished or blocked items honestly."
            )
        }
        return builder.toString()
    }
}
