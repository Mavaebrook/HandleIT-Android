package com.ai.assistance.operit.data.learning

/**
 * Pure decision for the safe-by-default write path of the learning repositories.
 *
 * When a domain object carries unresolved list integrity and the caller supplies no explicit
 * `originalRaw`, a re-save must NOT re-encode the (empty) decoded lists and erase the original
 * stored bytes. This object decides, purely, whether the repository must read the stored row's raw
 * fields and preserve them, or whether the caller's explicit map (or the normal encode path) applies.
 *
 * Extracted as a pure function so the decision is unit-testable without an ObjectBox store, the same
 * way [RowMigrationPlanner] isolates the migration decision.
 */
object UnresolvedWriteGuard {

    /**
     * True when the repository must read the stored row's raw fields to preserve unresolved bytes.
     *
     * @param hasUnresolved true when the domain object carries any unresolved list field.
     * @param callerSuppliedRaw the caller's explicit originalRaw map (empty when not supplied).
     * @param persistedId the domain object's id; 0 means a new row with no stored bytes to preserve.
     */
    fun mustPreserveStoredRaw(
        hasUnresolved: Boolean,
        callerSuppliedRaw: Map<String, String>,
        persistedId: Long
    ): Boolean = hasUnresolved && callerSuppliedRaw.isEmpty() && persistedId != 0L
}
