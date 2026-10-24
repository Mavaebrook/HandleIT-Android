package com.ai.assistance.operit.core.tools

import com.ai.assistance.operit.core.chat.hooks.ProjectIdentity
import com.ai.assistance.operit.data.model.AITool

/**
 * Pure decision function for the G02 project-constraint gate.
 *
 * HandleIT project constraints apply only when the invocation carries a trusted
 * [ProjectIdentity.Kind.HANDLEIT] identity: local Gradle/Android builds are forbidden and writes
 * to protected paths are forbidden. Any OTHER/UNKNOWN identity yields
 * [ProjectConstraintDecision.Allow], so an unestablished identity can never grant HandleIT
 * authority.
 *
 * Denial reasons are bounded, deterministic codes, never the full command, arguments, or paths,
 * so a blocked result can never leak sensitive content into model-visible text.
 */
object ProjectConstraintPolicy {

    const val LOCAL_BUILD_FORBIDDEN = "LOCAL_BUILD_FORBIDDEN"
    const val PROTECTED_PATH_MUTATION = "PROTECTED_PATH_MUTATION"

    private val commandToolNames =
        setOf(
            "execute_shell",
            "execute_in_terminal_session",
            "execute_in_terminal_session_streaming",
            "execute_hidden_terminal_command"
        )

    // Mutation tools that carry a single `path` parameter.
    private val pathMutationToolNames =
        setOf(
            "write_file",
            "write_file_binary",
            "delete_file",
            "make_directory",
            "create_file",
            "edit_file",
            "apply_file"
        )

    // Mutation tools that carry `source` and `destination` parameters.
    private val sourceDestinationMutationToolNames =
        setOf(
            "move_file",
            "copy_file",
            "zip_files",
            "unzip_files"
        )

    sealed class ProjectConstraintDecision {
        object Allow : ProjectConstraintDecision()
        data class Deny(val reasonCode: String) : ProjectConstraintDecision()
    }

    fun decide(context: ToolInvocationContext, tool: AITool): ProjectConstraintDecision {
        if (context.projectIdentity.kind != ProjectIdentity.Kind.HANDLEIT) {
            return ProjectConstraintDecision.Allow
        }

        if (tool.name in commandToolNames) {
            val command = tool.parameters.find { it.name == "command" }?.value
            if (!command.isNullOrBlank() && isLocalBuildCommand(command)) {
                return ProjectConstraintDecision.Deny(LOCAL_BUILD_FORBIDDEN)
            }
        }

        if (tool.name in pathMutationToolNames) {
            val path = tool.parameters.find { it.name == "path" }?.value
            if (isProtectedPath(path)) {
                return ProjectConstraintDecision.Deny(PROTECTED_PATH_MUTATION)
            }
        }

        if (tool.name in sourceDestinationMutationToolNames) {
            val source = tool.parameters.find { it.name == "source" }?.value
            val destination = tool.parameters.find { it.name == "destination" }?.value
            if (isProtectedPath(source) || isProtectedPath(destination)) {
                return ProjectConstraintDecision.Deny(PROTECTED_PATH_MUTATION)
            }
        }

        return ProjectConstraintDecision.Allow
    }

    private fun isLocalBuildCommand(command: String): Boolean {
        return command
            .trim()
            .split(Regex("\\s+"))
            .any { it == "./gradlew" || it == "gradlew" || it == "gradlew.bat" || it == "gradle" }
    }

    private fun isProtectedPath(rawPath: String?): Boolean {
        val path = rawPath?.trim() ?: return false
        if (path.isEmpty()) {
            return false
        }
        val normalized = path.lowercase()

        if (normalized == ".git" ||
            normalized.startsWith(".git/") ||
            normalized.endsWith("/.git") ||
            normalized.contains("/.git/")
        ) {
            return true
        }

        if (normalized == "objectbox-models/default.json" ||
            normalized.endsWith("/objectbox-models/default.json")
        ) {
            return true
        }

        if (normalized.endsWith(".jks") ||
            normalized.endsWith(".keystore") ||
            normalized.endsWith(".p12") ||
            normalized.endsWith(".pfx")
        ) {
            return true
        }

        val fileName = normalized.substringAfterLast('/')
        return fileName == "local.properties" ||
            fileName == "keystore.properties" ||
            fileName == "signing.properties"
    }
}
