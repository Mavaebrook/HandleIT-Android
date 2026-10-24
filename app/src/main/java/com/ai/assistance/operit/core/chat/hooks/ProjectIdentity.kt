package com.ai.assistance.operit.core.chat.hooks

import java.io.File

/**
 * Trusted project identity for architect-rule scoping (D05).
 *
 * This type is the ONLY authority for deciding whether HandleIT-specific project requirements
 * (canonical repository, build-location policy, protected paths, invariants) may be injected into a
 * prompt. It must be constructed from trusted application/workspace configuration only — never from
 * model text, retrieved memories, a repo string claimed in a prompt, or a directory basename
 * heuristic.
 *
 * Production wiring: [resolveFromWorkspace] establishes the identity from the workspace's git
 * remote configuration, so a workspace bound to the canonical `Mavaebrook/HandleIT-Android`
 * repository resolves to [Kind.HANDLEIT] without any prompt claim. [ConversationService] carries the
 * result through [METADATA_KEY]; callers that do not supply a typed [ProjectIdentity] fall back to
 * [resolveFromWorkspace], so the identity can still be established from the workspace itself. An
 * absent or non-matching configuration resolves to [Kind.UNKNOWN] or [Kind.OTHER], never to
 * [Kind.HANDLEIT]. See
 * docs/TODO/learning-pipeline-hardening-20261017/10-step1-d05-g02-project-policy.md.
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

        private const val CANONICAL_HOST = "github.com"
        private const val CANONICAL_PATH = "Mavaebrook/HandleIT-Android"

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

        /**
         * Resolve a [ProjectIdentity] from the text of a `.git/config` file.
         *
         * Scans every `[remote "..."]` section for a `url = ...` line and returns [HANDLEIT] when
         * any remote URL refers to the canonical `Mavaebrook/HandleIT-Android` repository, [OTHER]
         * when at least one parseable remote is present but none is canonical, and [UNKNOWN] when
         * no parseable remote is found. Matching is exact host-and-path (case-insensitive host,
         * case-sensitive path), never substring, so `Mavaebrook/HandleIT-Android-fork` cannot match.
         */
        fun resolveFromGitConfig(gitConfigText: String?): ProjectIdentity {
            if (gitConfigText.isNullOrBlank()) {
                return UNKNOWN
            }
            val remotes = parseRemoteHostAndPaths(gitConfigText)
            if (remotes.isEmpty()) {
                return UNKNOWN
            }
            return if (remotes.any { isCanonicalRemote(it) }) HANDLEIT else OTHER
        }

        /**
         * Resolve a [ProjectIdentity] from a workspace directory by reading its git configuration.
         *
         * Reads `<workspace>/.git/config`, following a worktree `.git` file's `gitdir:` pointer, and
         * delegates to [resolveFromGitConfig]. A missing or unreadable config yields [UNKNOWN]. The
         * workspace basename is never consulted: authority comes only from the configured remote URL.
         */
        fun resolveFromWorkspace(workspacePath: String?): ProjectIdentity {
            if (workspacePath.isNullOrBlank()) {
                return UNKNOWN
            }
            val configText = readGitConfig(workspacePath) ?: return UNKNOWN
            return resolveFromGitConfig(configText)
        }

        private fun readGitConfig(workspacePath: String): String? {
            return runCatching {
                val gitPath = File(workspacePath, ".git")
                val configFile =
                    if (gitPath.isFile) {
                        // Worktree: ".git" is a file containing "gitdir: <path>".
                        val gitDir =
                            gitPath
                                .readText()
                                .lineSequence()
                                .firstOrNull { it.trim().startsWith("gitdir:") }
                                ?.substringAfter(':')
                                ?.trim()
                        gitDir?.let { File(it, "config") }
                    } else {
                        File(gitPath, "config")
                    }
                configFile?.takeIf { it.isFile }?.readText()
            }.getOrNull()
        }

        private fun parseRemoteHostAndPaths(gitConfigText: String): List<Pair<String, String>> {
            val remotes = mutableListOf<Pair<String, String>>()
            var inRemoteSection = false
            for (rawLine in gitConfigText.lineSequence()) {
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
                    continue
                }
                if (line.startsWith("[")) {
                    inRemoteSection = line.startsWith("[remote ")
                    continue
                }
                if (inRemoteSection && '=' in line) {
                    val key = line.substringBefore('=').trim()
                    if (key == "url") {
                        val value = line.substringAfter('=').trim().trim('"')
                        parseRemoteHostAndPath(value)?.let { remotes.add(it) }
                    }
                }
            }
            return remotes
        }

        private fun parseRemoteHostAndPath(rawUrl: String): Pair<String, String>? {
            val url = rawUrl.trim()
            if (url.isEmpty()) {
                return null
            }
            val host: String
            val path: String
            if (!url.contains("://")) {
                // scp-like syntax: [user@]host:path
                val afterAt = url.substringAfterLast('@')
                val colon = afterAt.indexOf(':')
                if (colon <= 0) {
                    return null
                }
                host = afterAt.substring(0, colon)
                path = afterAt.substring(colon + 1)
            } else {
                // URL syntax: scheme://[user@]host[:port]/path
                val rest = url.substringAfter("://")
                val slash = rest.indexOf('/')
                val authority = if (slash >= 0) rest.substring(0, slash) else rest
                val hostPort = authority.substringAfterLast('@')
                host = hostPort.substringBefore(':')
                path = if (slash >= 0) rest.substring(slash + 1) else ""
            }
            if (host.isEmpty() || path.isEmpty()) {
                return null
            }
            val normalizedPath = path.trimEnd('/').removeSuffix(".git")
            if (normalizedPath.isEmpty()) {
                return null
            }
            return host.lowercase() to normalizedPath
        }

        private fun isCanonicalRemote(remote: Pair<String, String>): Boolean {
            return remote.first == CANONICAL_HOST && remote.second == CANONICAL_PATH
        }
    }
}
