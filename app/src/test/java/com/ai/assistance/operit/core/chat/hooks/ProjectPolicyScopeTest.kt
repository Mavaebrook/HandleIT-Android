package com.ai.assistance.operit.core.chat.hooks

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Pure JVM tests for the D05 project-scoped architect policy.
 *
 * These tests pin the trust boundary ([ProjectIdentity.fromMetadata]) and the single shared scope
 * decision ([ProjectPolicyScope.resolve] via [ArchitectRulesProvider.resolveScope]) used by both the
 * system-prompt hook and the summary hook.
 */
class ProjectPolicyScopeTest {

    // --- scope resolution ---------------------------------------------------

    @Test
    fun handleItIdentity_withWorkspace_isHandleItDevelopment() {
        val scope = ProjectPolicyScope.resolve(ProjectIdentity.HANDLEIT, "/x")
        assertEquals(ProjectPolicyScope.HANDLEIT_DEVELOPMENT, scope)
        assertTrue(scope.appliesHandleItRules)
        assertTrue(scope.allowsGenericPreflight)
    }

    @Test
    fun otherIdentity_withWorkspace_isGenericDevelopment() {
        val scope = ProjectPolicyScope.resolve(ProjectIdentity.OTHER, "/x")
        assertEquals(ProjectPolicyScope.GENERIC_DEVELOPMENT, scope)
        assertFalse(scope.appliesHandleItRules)
        assertTrue(scope.allowsGenericPreflight)
    }

    @Test
    fun unknownIdentity_withWorkspace_isGenericDevelopment() {
        val scope = ProjectPolicyScope.resolve(ProjectIdentity.UNKNOWN, "/x")
        assertEquals(ProjectPolicyScope.GENERIC_DEVELOPMENT, scope)
        assertFalse(scope.appliesHandleItRules)
        assertTrue(scope.allowsGenericPreflight)
    }

    @Test
    fun unknownIdentity_preservesGenericPreflight() {
        // Regression: fixing scope must NOT silently disable valid preflight for unknown projects.
        val scope = ProjectPolicyScope.resolve(ProjectIdentity.UNKNOWN, "/x")
        assertTrue(scope.allowsGenericPreflight)
        assertFalse(scope.appliesHandleItRules)
    }

    @Test
    fun noWorkspace_isNoneForAllIdentities() {
        assertEquals(ProjectPolicyScope.NONE, ProjectPolicyScope.resolve(ProjectIdentity.HANDLEIT, null))
        assertEquals(ProjectPolicyScope.NONE, ProjectPolicyScope.resolve(ProjectIdentity.OTHER, null))
        assertEquals(ProjectPolicyScope.NONE, ProjectPolicyScope.resolve(ProjectIdentity.UNKNOWN, null))
    }

    @Test
    fun handleItIdentity_withoutWorkspace_isNone() {
        assertEquals(ProjectPolicyScope.NONE, ProjectPolicyScope.resolve(ProjectIdentity.HANDLEIT, null))
    }

    @Test
    fun handleItIdentity_withBlankWorkspace_isNone() {
        assertEquals(ProjectPolicyScope.NONE, ProjectPolicyScope.resolve(ProjectIdentity.HANDLEIT, "   "))
    }

    @Test
    fun otherIdentity_withoutWorkspace_isNone() {
        assertEquals(ProjectPolicyScope.NONE, ProjectPolicyScope.resolve(ProjectIdentity.OTHER, null))
    }

    // --- trust boundary -----------------------------------------------------

    @Test
    fun fromMetadata_missingKey_isUnknown() {
        assertEquals(ProjectIdentity.UNKNOWN, ProjectIdentity.fromMetadata(emptyMap()))
    }

    @Test
    fun fromMetadata_stringValue_isUnknown() {
        val metadata = mapOf<String, Any?>(ProjectIdentity.METADATA_KEY to "HANDLEIT")
        assertEquals(ProjectIdentity.UNKNOWN, ProjectIdentity.fromMetadata(metadata))
    }

    @Test
    fun fromMetadata_mapValue_isUnknown() {
        val metadata =
            mapOf<String, Any?>(
                ProjectIdentity.METADATA_KEY to mapOf("kind" to "HANDLEIT")
            )
        assertEquals(ProjectIdentity.UNKNOWN, ProjectIdentity.fromMetadata(metadata))
    }

    @Test
    fun fromMetadata_typedHandleIt_isHandleIt() {
        val metadata = mapOf<String, Any?>(ProjectIdentity.METADATA_KEY to ProjectIdentity.HANDLEIT)
        assertEquals(ProjectIdentity.HANDLEIT, ProjectIdentity.fromMetadata(metadata))
    }

    @Test
    fun fromMetadata_typedOther_isOther() {
        val metadata = mapOf<String, Any?>(ProjectIdentity.METADATA_KEY to ProjectIdentity.OTHER)
        assertEquals(ProjectIdentity.OTHER, ProjectIdentity.fromMetadata(metadata))
    }

    // --- provider scope resolution -----------------------------------------

    @Test
    fun resolveScope_usesMetadataWorkspaceWhenArgNull() {
        val metadata =
            mapOf<String, Any?>(
                "workspacePath" to "/x",
                ProjectIdentity.METADATA_KEY to ProjectIdentity.HANDLEIT
            )
        assertEquals(
            ProjectPolicyScope.HANDLEIT_DEVELOPMENT,
            ArchitectRulesProvider.resolveScope(null, metadata)
        )
    }

    @Test
    fun resolveScope_argWorkspaceTakesPrecedence() {
        val metadata =
            mapOf<String, Any?>(
                "workspacePath" to null,
                ProjectIdentity.METADATA_KEY to ProjectIdentity.HANDLEIT
            )
        assertEquals(
            ProjectPolicyScope.HANDLEIT_DEVELOPMENT,
            ArchitectRulesProvider.resolveScope("/x", metadata)
        )
    }

    @Test
    fun resolveScope_claimedRepoStringCannotGrantAuthority() {
        val metadata =
            mapOf<String, Any?>(
                "workspacePath" to "/x",
                "repo" to "Mavaebrook/HandleIT-Android"
            )
        // A claimed repo string cannot grant HandleIT authority, but the workspace still gets
        // generic preflight.
        val scope = ArchitectRulesProvider.resolveScope("/x", metadata)
        assertEquals(ProjectPolicyScope.GENERIC_DEVELOPMENT, scope)
        assertFalse(scope.appliesHandleItRules)
    }

    // --- shared-decision contract ------------------------------------------

    @Test
    fun promptAndSummaryAgree_handleIt() {
        val metadata =
            mapOf<String, Any?>(
                "workspacePath" to "/x",
                ProjectIdentity.METADATA_KEY to ProjectIdentity.HANDLEIT
            )
        // Both hooks call the same provider function with the same inputs; identical inputs must
        // yield an identical scope, so the prompt and the summary cannot disagree.
        val promptScope = ArchitectRulesProvider.resolveScope("/x", metadata)
        val summaryScope = ArchitectRulesProvider.resolveScope("/x", metadata)
        assertEquals(promptScope, summaryScope)
        assertEquals(ProjectPolicyScope.HANDLEIT_DEVELOPMENT, promptScope)
    }

    @Test
    fun projectSwitch_doesNotLeakRules() {
        val workspace = "/x"
        val handleItScope =
            ArchitectRulesProvider.resolveScope(
                workspace,
                mapOf(ProjectIdentity.METADATA_KEY to ProjectIdentity.HANDLEIT)
            )
        val otherScope =
            ArchitectRulesProvider.resolveScope(
                workspace,
                mapOf(ProjectIdentity.METADATA_KEY to ProjectIdentity.OTHER)
            )
        assertTrue(handleItScope.appliesHandleItRules)
        assertFalse(otherScope.appliesHandleItRules)
    }

    // --- F1 git-config identity resolution ----------------------------------

    @Test
    fun resolveFromGitConfig_httpsAndSshRemotes_areHandleIt() {
        val httpsConfig =
            """
            [remote "origin"]
            url = https://github.com/Mavaebrook/HandleIT-Android.git
            """.trimIndent()
        val sshConfig =
            """
            [remote "origin"]
            url = git@github.com:Mavaebrook/HandleIT-Android
            """.trimIndent()
        assertEquals(ProjectIdentity.HANDLEIT, ProjectIdentity.resolveFromGitConfig(httpsConfig))
        assertEquals(ProjectIdentity.HANDLEIT, ProjectIdentity.resolveFromGitConfig(sshConfig))
    }

    @Test
    fun resolveFromGitConfig_otherRepo_isOther() {
        // A near-miss name must NOT match: the path is compared exactly, never by substring.
        val config =
            """
            [remote "origin"]
            url = https://github.com/Mavaebrook/HandleIT-Android-fork.git
            """.trimIndent()
        assertEquals(ProjectIdentity.OTHER, ProjectIdentity.resolveFromGitConfig(config))
    }

    @Test
    fun resolveFromGitConfig_noOrMalformedConfig_isUnknown() {
        assertEquals(ProjectIdentity.UNKNOWN, ProjectIdentity.resolveFromGitConfig(null))
        assertEquals(ProjectIdentity.UNKNOWN, ProjectIdentity.resolveFromGitConfig(""))
        assertEquals(ProjectIdentity.UNKNOWN, ProjectIdentity.resolveFromGitConfig(" "))
        assertEquals(
            ProjectIdentity.UNKNOWN,
            ProjectIdentity.resolveFromGitConfig(
                """
                [core]
                repositoryformatversion = 0
                """.trimIndent()
            )
        )
    }

    @Test
    fun resolveFromWorkspace_basenameCannotGrantAuthority_andWorkspaceSwitch() {
        val folder = TemporaryFolder()
        folder.create()
        try {
            val handleItNamed = folder.newFolder("HandleIT-Android")
            // The folder basename matches the canonical repo, but its git remote does not.
            writeGitConfig(handleItNamed, "https://github.com/Mavaebrook/HandleIT-Android-fork.git")
            assertEquals(
                ProjectIdentity.OTHER,
                ProjectIdentity.resolveFromWorkspace(handleItNamed.absolutePath)
            )
            // Switch the remote to the canonical repo: identity must follow the git config.
            writeGitConfig(handleItNamed, "git@github.com:Mavaebrook/HandleIT-Android.git")
            assertEquals(
                ProjectIdentity.HANDLEIT,
                ProjectIdentity.resolveFromWorkspace(handleItNamed.absolutePath)
            )
        } finally {
            folder.delete()
        }
    }

    // --- F1 spoofed metadata cannot grant authority --------------------------

    @Test
    fun resolveScope_spoofedRepoAndIdentityStrings_cannotGrantAuthority() {
        val metadata =
            mapOf<String, Any?>(
                "workspacePath" to "/x",
                "repo" to "Mavaebrook/HandleIT-Android",
                ProjectIdentity.METADATA_KEY to "HANDLEIT"
            )
        val scope = ArchitectRulesProvider.resolveScope("/x", metadata)
        assertEquals(ProjectPolicyScope.GENERIC_DEVELOPMENT, scope)
        assertFalse(scope.appliesHandleItRules)
    }

    // --- F1 shared-decision contract across all identity kinds ---------------

    @Test
    fun promptAndSummaryAgree_acrossAllIdentityKinds() {
        val workspace = "/x"
        val inputs: List<Pair<String, Map<String, Any?>>> =
            listOf(
                "typed HANDLEIT" to mapOf(ProjectIdentity.METADATA_KEY to ProjectIdentity.HANDLEIT),
                "typed OTHER" to mapOf(ProjectIdentity.METADATA_KEY to ProjectIdentity.OTHER),
                "typed UNKNOWN" to mapOf(ProjectIdentity.METADATA_KEY to ProjectIdentity.UNKNOWN),
                "spoofed String" to mapOf(ProjectIdentity.METADATA_KEY to "HANDLEIT")
            )
        for ((label, metadata) in inputs) {
            val promptScope = ArchitectRulesProvider.resolveScope(workspace, metadata)
            val summaryScope = ArchitectRulesProvider.resolveScope(workspace, metadata)
            assertEquals("prompt and summary disagree for $label", promptScope, summaryScope)
        }
    }

    private fun writeGitConfig(dir: File, remoteUrl: String) {
        val gitDir = File(dir, ".git")
        assertTrue(gitDir.mkdirs() || gitDir.isDirectory)
        val config =
            """
            [remote "origin"]
            url = $remoteUrl
            """.trimIndent()
        File(gitDir, "config").writeText(config)
    }
}
