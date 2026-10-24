package com.ai.assistance.operit.core.tools

import com.ai.assistance.operit.core.chat.hooks.ProjectIdentity
import com.ai.assistance.operit.core.tools.ProjectConstraintPolicy.ProjectConstraintDecision
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the G02 project-constraint gate ([ProjectConstraintPolicy]).
 *
 * These tests pin the identity scoping (only HANDLEIT is constrained), the four local-build command
 * routes, protected-path mutation, and the bounded reason-code invariant.
 */
class ProjectConstraintPolicyTest {

    private fun tool(name: String, vararg params: Pair<String, String>): AITool =
        AITool(name = name, parameters = params.map { ToolParameter(it.first, it.second) })

    private fun decide(identity: ProjectIdentity, tool: AITool): ProjectConstraintDecision =
        ProjectConstraintPolicy.decide(ToolInvocationContext(projectIdentity = identity), tool)

    private fun assertDeny(decision: ProjectConstraintDecision, expectedCode: String) {
        assertTrue(decision is ProjectConstraintDecision.Deny)
        assertEquals(expectedCode, (decision as ProjectConstraintDecision.Deny).reasonCode)
    }

    // --- identity scoping: only HANDLEIT is constrained ---

    @Test
    fun nonHandleItIdentity_allowsLocalBuild() {
        val build = tool("execute_shell", "command" to "./gradlew assembleDebug")
        assertSame(ProjectConstraintDecision.Allow, decide(ProjectIdentity.OTHER, build))
        assertSame(ProjectConstraintDecision.Allow, decide(ProjectIdentity.UNKNOWN, build))
    }

    @Test
    fun nonHandleItIdentity_allowsProtectedPathWrite() {
        val write = tool("write_file", "path" to "app/objectbox-models/default.json")
        assertSame(ProjectConstraintDecision.Allow, decide(ProjectIdentity.OTHER, write))
        assertSame(ProjectConstraintDecision.Allow, decide(ProjectIdentity.UNKNOWN, write))
    }

    // --- local build forbidden on every command route ---

    @Test
    fun handleIt_dotSlashGradlew_isBlocked() {
        val decision = decide(ProjectIdentity.HANDLEIT, tool("execute_shell", "command" to "./gradlew assembleDebug"))
        assertDeny(decision, ProjectConstraintPolicy.LOCAL_BUILD_FORBIDDEN)
    }

    @Test
    fun handleIt_gradleBuild_isBlocked() {
        val decision = decide(ProjectIdentity.HANDLEIT, tool("execute_shell", "command" to "gradle build"))
        assertDeny(decision, ProjectConstraintPolicy.LOCAL_BUILD_FORBIDDEN)
    }

    @Test
    fun handleIt_bareGradlew_isBlocked() {
        val decision = decide(ProjectIdentity.HANDLEIT, tool("execute_in_terminal_session", "command" to "gradlew"))
        assertDeny(decision, ProjectConstraintPolicy.LOCAL_BUILD_FORBIDDEN)
    }

    @Test
    fun handleIt_gradlewBat_isBlocked() {
        val decision = decide(ProjectIdentity.HANDLEIT, tool("execute_hidden_terminal_command", "command" to "gradlew.bat"))
        assertDeny(decision, ProjectConstraintPolicy.LOCAL_BUILD_FORBIDDEN)
    }

    @Test
    fun handleIt_streamingTerminal_isBlocked() {
        val decision = decide(ProjectIdentity.HANDLEIT, tool("execute_in_terminal_session_streaming", "command" to "gradle build"))
        assertDeny(decision, ProjectConstraintPolicy.LOCAL_BUILD_FORBIDDEN)
    }

    @Test
    fun handleIt_catBuildGradle_isAllowed() {
        val decision = decide(ProjectIdentity.HANDLEIT, tool("execute_shell", "command" to "cat build.gradle"))
        assertSame(ProjectConstraintDecision.Allow, decision)
    }

    @Test
    fun handleIt_unrelatedShell_isAllowed() {
        val decision = decide(ProjectIdentity.HANDLEIT, tool("execute_shell", "command" to "ls -la"))
        assertSame(ProjectConstraintDecision.Allow, decision)
    }

    // --- protected path mutation ---

    @Test
    fun handleIt_objectboxModelWrite_isBlocked() {
        val decision = decide(ProjectIdentity.HANDLEIT, tool("write_file", "path" to "app/objectbox-models/default.json"))
        assertDeny(decision, ProjectConstraintPolicy.PROTECTED_PATH_MUTATION)
    }

    @Test
    fun handleIt_dotGitMutation_isBlocked() {
        val decision = decide(ProjectIdentity.HANDLEIT, tool("delete_file", "path" to ".git/config"))
        assertDeny(decision, ProjectConstraintPolicy.PROTECTED_PATH_MUTATION)
    }

    @Test
    fun handleIt_signingKeystoreWrite_isBlocked() {
        val decision = decide(ProjectIdentity.HANDLEIT, tool("write_file_binary", "path" to "release.keystore"))
        assertDeny(decision, ProjectConstraintPolicy.PROTECTED_PATH_MUTATION)
    }

    @Test
    fun handleIt_localPropertiesEdit_isBlocked() {
        val decision = decide(ProjectIdentity.HANDLEIT, tool("edit_file", "path" to "local.properties"))
        assertDeny(decision, ProjectConstraintPolicy.PROTECTED_PATH_MUTATION)
    }

    @Test
    fun handleIt_moveOntoProtectedPath_isBlocked() {
        val decision = decide(ProjectIdentity.HANDLEIT, tool("move_file", "source" to "/tmp/x", "destination" to "app/objectbox-models/default.json"))
        assertDeny(decision, ProjectConstraintPolicy.PROTECTED_PATH_MUTATION)
    }

    @Test
    fun handleIt_copyFromProtectedPath_isBlocked() {
        val decision = decide(ProjectIdentity.HANDLEIT, tool("copy_file", "source" to "release.p12", "destination" to "/tmp/y"))
        assertDeny(decision, ProjectConstraintPolicy.PROTECTED_PATH_MUTATION)
    }

    @Test
    fun handleIt_nonProtectedWrite_isAllowed() {
        val decision = decide(ProjectIdentity.HANDLEIT, tool("write_file", "path" to "src/main/Notes.md"))
        assertSame(ProjectConstraintDecision.Allow, decision)
    }

    // --- bounded reason code ---

    @Test
    fun denialReason_isBoundedCode() {
        val decision = decide(ProjectIdentity.HANDLEIT, tool("execute_shell", "command" to "./gradlew --no-daemon assembleRelease --token SECRETVALUE"))
        val deny = decision as ProjectConstraintDecision.Deny
        assertEquals(ProjectConstraintPolicy.LOCAL_BUILD_FORBIDDEN, deny.reasonCode)
        assertTrue(!deny.reasonCode.contains("gradle"))
        assertTrue(!deny.reasonCode.contains("SECRETVALUE"))
    }
}
