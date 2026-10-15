package com.ai.assistance.operit.data.stats

import com.ai.assistance.operit.data.model.ApiProviderType

internal data class ReleasedProviderModelKey(
    val storedProviderModel: String,
    val provider: String,
    val model: String,
)

/** Decodes released `provider:model` keys stored as `provider_model`. */
internal object ReleasedProviderModelKeyDecoder {
    private val builtInProviderAliases = ApiProviderType.entries.associate { it.name to it.name }

    fun decode(
        encoded: String,
        additionalProviderAliases: Map<String, String> = emptyMap(),
    ): ReleasedProviderModelKey {
        val aliases = buildMap {
            putAll(builtInProviderAliases)
            additionalProviderAliases.forEach { (rawAlias, rawIdentity) ->
                val alias = rawAlias.trim()
                val identity = rawIdentity.trim()
                require(alias.isNotEmpty() && identity.isNotEmpty())
                val previous = put(alias, identity)
                require(previous == null || previous == identity)
            }
        }
        val known = aliases.keys.sortedByDescending(String::length)
            .firstOrNull { encoded == it || encoded.startsWith("${it}_") }
        require(known != null && known.length < encoded.lastIndex) {
            "released token key does not contain a known provider and model: $encoded"
        }
        val providerAlias = encoded.substring(0, known.length)
        val model = encoded.substring(known.length + 1)
        return ReleasedProviderModelKey(
            storedProviderModel = "$providerAlias:$model",
            provider = aliases.getValue(providerAlias),
            model = model,
        )
    }

    /** Used by migration code for released keys from before provider/model identities existed. */
    fun decodeOrNull(
        encoded: String,
        additionalProviderAliases: Map<String, String> = emptyMap(),
    ): ReleasedProviderModelKey? =
        try {
            decode(encoded, additionalProviderAliases)
        } catch (_: IllegalArgumentException) {
            null
        }
}
