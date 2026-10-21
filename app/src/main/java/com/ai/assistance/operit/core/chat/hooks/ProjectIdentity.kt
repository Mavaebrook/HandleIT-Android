package com.ai.assistance.operit.core.chat.hooks

/**
 * Trusted project identity for architect-rule scoping (D05).
 *
 * This type is the ONLY authority for deciding whether HandleIT-specific project requirements
 * (canonical repository, build-location policy, protected paths, invariants) may be injected into a
 * prompt. It must be constructed from trusted application/workspace configuration only — never from
 * model text, retrieved memories, a repo string claimed in a prompt, or a directory basename
 * heuristic.
 *
 * Production wiring note: no trusted identity source exists yet (there is no ProjectContext, no DI
 * container, and no build-config constant naming the canonical repository). Until a caller supplies
 * a [ProjectIdentity] through [METADATA_KEY], every task resolves to [Kind.UNKNOWN] and HandleIT
 * rules are NOT injected. This is deliberate: an unknown project must never inherit HandleIT
 * restrictions. See docs/TODO/learning-pipeline-hardening-20261017/10-step1-d05-g02-project-policy.md.
 */
data class ProjectIdentity(val kind: Kind) {

    enum class Kind {
        /** The HandleIT-Android project itself. HandleIT-specific requirements apply. */
        HANDLEIT,
        /** A different, known project. Generic development guidance may apply; HandleIT rules do not. */
        OTHER,
        /** Identity not established. No project-specific requirements are injected. */
        UNKNOWN
    }

    companion object {
        /** Metadata key under which a trusted [ProjectIdentity] is carried through prompt hooks. */
        const val METADATA_KEY = "projectIdentity"

        val HANDLEIT = ProjectIdentity(Kind.HANDLEIT)
        val OTHER = ProjectIdentity(Kind.OTHER)
        val UNKNOWN = ProjectIdentity(Kind.UNKNOWN)

        /**
         * Resolve a [ProjectIdentity] from hook metadata. Only a value that is already a
         * [ProjectIdentity] is trusted; anything else (a String, a map, model text) resolves to
         * [UNKNOWN]. This is the trust boundary: provenance cannot be granted by prompt content.
         */
        fun fromMetadata(metadata: Map<String, Any?>): ProjectIdentity {
            return metadata[METADATA_KEY] as? ProjectIdentity ?: UNKNOWN
        }
    }
}
