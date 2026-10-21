package com.ai.assistance.operit.core.chat.hooks

/**
 * The single scope decision shared by the system-prompt hook and the summary hook (D05).
 *
 * Both hooks call [resolve] so that the prompt and the summary can never disagree about which
 * project requirements apply. The decision is a pure function of the trusted [ProjectIdentity] and
 * whether a workspace is present; it never inspects model text or retrieved memory.
 */
enum class ProjectPolicyScope {
    /** HandleIT project with a workspace: HandleIT-specific requirements AND generic preflight apply. */
    HANDLEIT_DEVELOPMENT,
    /** A known non-HandleIT project OR an unestablished identity, with a workspace: generic preflight only, no HandleIT rules. */
    GENERIC_DEVELOPMENT,
    /** No workspace (casual chat): no project requirements are injected. */
    NONE;

    /** True when HandleIT-specific requirements (canonical repo, build policy, protected paths) apply. */
    val appliesHandleItRules: Boolean
        get() = this == HANDLEIT_DEVELOPMENT

    /** True when the optional generic preflight/memory block may be appended. */
    val allowsGenericPreflight: Boolean
        get() = this == HANDLEIT_DEVELOPMENT || this == GENERIC_DEVELOPMENT

    companion object {
        /**
         * Resolve the scope. A workspace is required for any development scope; identity decides
         * whether HandleIT rules are added. [ProjectIdentity.Kind.UNKNOWN] never yields HandleIT
         * rules, so an unestablished identity cannot grant authority.
         */
        fun resolve(identity: ProjectIdentity, workspacePath: String?): ProjectPolicyScope {
            if (workspacePath.isNullOrBlank()) {
                return NONE
            }
            return when (identity.kind) {
                ProjectIdentity.Kind.HANDLEIT -> HANDLEIT_DEVELOPMENT
                // A known non-HandleIT project and an unestablished identity both get generic
                // development scope: the optional preflight/memory block is preserved, but
                // HandleIT-specific requirements are NOT injected. An unknown identity can never
                // grant HandleIT authority, and it must not silently disable valid preflight.
                ProjectIdentity.Kind.OTHER -> GENERIC_DEVELOPMENT
                ProjectIdentity.Kind.UNKNOWN -> GENERIC_DEVELOPMENT
            }
        }
    }
}
