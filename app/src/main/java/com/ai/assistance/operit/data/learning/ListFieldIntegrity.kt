package com.ai.assistance.operit.data.learning

/**
 * Non-persisted integrity metadata for one list field decoded from a stored column.
 *
 * This type is NEVER persisted and NEVER added to an ObjectBox entity. It travels alongside the
 * decoded domain object so a consumer can distinguish a genuine empty list from a field whose
 * stored bytes could not be resolved (corrupt, unsupported version, or ambiguous provenance).
 *
 * The original raw stored string is retained for controlled recovery. It must NOT be rendered into
 * prompts or logs verbatim (it is untrusted); only [status] and [reason] are safe to surface.
 */
data class ListFieldIntegrity(
    val fieldName: String,
    val status: Status,
    /** Short non-sensitive reason; empty for HEALTHY. */
    val reason: String = "",
    /** Envelope version when [status] == UNSUPPORTED_VERSION, else null. */
    val version: Int? = null,
    /** Original raw stored string, retained for recovery. Never render verbatim. */
    val rawStored: String = ""
) {
    enum class Status {
        /** Decoded cleanly (versioned or established legacy). The list is trustworthy. */
        HEALTHY,
        /** Recognized versioned payload that is malformed/truncated/trailing-junk. */
        CORRUPT,
        /** Well-formed envelope with a version this codec does not support. */
        UNSUPPORTED_VERSION,
        /** Brace-starting bytes whose provenance (legacy literal vs envelope) cannot be proven. */
        AMBIGUOUS
    }

    val isHealthy: Boolean get() = status == Status.HEALTHY
    val isUnresolved: Boolean get() = status != Status.HEALTHY

    companion object {
        fun healthy(fieldName: String): ListFieldIntegrity =
            ListFieldIntegrity(fieldName = fieldName, status = Status.HEALTHY)

        /** Build integrity metadata from a typed decode result for [fieldName]. */
        fun fromDecoded(fieldName: String, decoded: StringListCodec.DecodedList): ListFieldIntegrity =
            when (decoded) {
                is StringListCodec.DecodedList.Versioned -> healthy(fieldName)
                is StringListCodec.DecodedList.Legacy -> healthy(fieldName)
                is StringListCodec.DecodedList.Corrupt -> ListFieldIntegrity(
                    fieldName = fieldName,
                    status = Status.CORRUPT,
                    reason = decoded.reason,
                    rawStored = decoded.stored
                )
                is StringListCodec.DecodedList.UnsupportedVersion -> ListFieldIntegrity(
                    fieldName = fieldName,
                    status = Status.UNSUPPORTED_VERSION,
                    reason = "unsupported envelope version ${decoded.version}",
                    version = decoded.version,
                    rawStored = decoded.stored
                )
                is StringListCodec.DecodedList.Ambiguous -> ListFieldIntegrity(
                    fieldName = fieldName,
                    status = Status.AMBIGUOUS,
                    reason = decoded.reason,
                    rawStored = decoded.stored
                )
            }
    }
}

/**
 * The integrity state of every list field of one row. Non-persisted.
 */
data class RowListIntegrity(
    val fields: Map<String, ListFieldIntegrity>
) {
    val hasUnresolved: Boolean get() = fields.values.any { it.isUnresolved }
    val unresolvedFields: List<ListFieldIntegrity> get() = fields.values.filter { it.isUnresolved }

    /**
     * Equality is defined over the UNRESOLVED fields only. A row whose every field is healthy is
     * equal to [HEALTHY] whether its map is empty or explicitly populated with healthy entries,
     * because both describe the same integrity state: nothing unresolved. This keeps a freshly
     * constructed domain object (default [HEALTHY]) equal to the same object after a storage
     * round-trip that populated an all-healthy map.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RowListIntegrity) return false
        return unresolvedByField() == other.unresolvedByField()
    }

    override fun hashCode(): Int = unresolvedByField().hashCode()

    private fun unresolvedByField(): Map<String, ListFieldIntegrity> =
        fields.filterValues { it.isUnresolved }

    companion object {
        val HEALTHY = RowListIntegrity(emptyMap())
    }
}
