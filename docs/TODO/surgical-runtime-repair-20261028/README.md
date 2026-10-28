# Surgical runtime repair

Base: `17089b41b4f192968deeb0d36163333c53e222aa`. Scope: a usable HandleIT test build, not the bridge migration.

## Work

- Remove the duplicate legacy auto-save enqueue and automatic candidate processing; preserve stored memories, pending legacy rows, manual memory operations, and the learning setting. The old worker remains only for explicitly selected user messages, without the old five-candidate minimum.
- Enable profile-scoped episode recall in conversations without a workspace, without granting HandleIT project authority.
- Register enabled configured remote MCP servers independently of connection success; retain live connection checks at activation/invocation and disabled-server filtering.
- Include the user request in review context instead of an empty string.
- Use the normal hosted PR check and an Android debug artifact for device testing. No broad audit or local Gradle runs.

## Device smoke check

Use Operit to install the eventual debug APK over HandleIT, preserving app data. In a fresh HandleIT conversation, activate one enabled remote server by its configured ID and run one read-only tool. Ask a related question that should recall a known episode without binding a workspace. Explicitly select a user message for saving; the legacy worker should process only that selected message, not old automatic candidates.

## Limits

Existing memory and ObjectBox models remain intact. Explicit/manual memory tools remain available. Legacy automatic candidates are retained but no longer drained; explicitly selected user messages still use their existing save worker and interval. The separate Operit app is untouched. This is not full trajectory capture, skill publication, bridge ownership, or proof of improved model performance. Existing retrieved-memory fencing remains inside the system prompt. Remote registration means configured, not currently reachable.
