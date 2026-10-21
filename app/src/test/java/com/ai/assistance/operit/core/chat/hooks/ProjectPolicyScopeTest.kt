package com.ai.assistance.operit.core.chat.hooks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
