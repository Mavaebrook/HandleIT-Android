package com.ai.assistance.operit.data.repository

import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.data.model.MemoryProperty
import com.ai.assistance.operit.data.repository.MemoryRepository.Companion.PROP_STATUS
import com.ai.assistance.operit.data.repository.MemoryRepository.Companion.STATUS_ACTIVE
import com.ai.assistance.operit.data.repository.MemoryRepository.Companion.STATUS_INACTIVE
import com.ai.assistance.operit.data.repository.MemoryRepository.Companion.PROP_RETIRED_PREV_STATUS
import com.ai.assistance.operit.data.repository.MemoryRepository.Companion.PROP_RETIRED_AT
import com.ai.assistance.operit.data.repository.MemoryRepository.Companion.PROP_RETIRED_REASON
import com.ai.assistance.operit.data.repository.MemoryRepository.Companion.PROP_RETIRED_BY_LINK_TYPE
import com.ai.assistance.operit.data.repository.MemoryRepository.Companion.PROP_RETIRED_SOURCE_ID
import com.ai.assistance.operit.data.repository.MemoryRepository.Companion.PROP_PENDING_RETIREMENT_REASON
import com.ai.assistance.operit.data.repository.MemoryRepository.Companion.PROP_RESTORED_AT
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor

/**
 * Existing metadata persistence, isolated from Android service initialization.
 * Ordinary status writes invalidate restoration, including INACTIVE -> INACTIVE.
 * Retirement and restoration own their marker lifecycle within a write transaction.
 */
internal class MemoryMetadataStore(
    private val store: BoxStore,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    private val memoryBox = store.boxFor<Memory>()
    private val propertyBox = store.boxFor<MemoryProperty>()

    fun readProperty(memory: Memory, key: String): String? {
        if (memory.id <= 0L) return null
        memory.properties.reset()
        return memory.properties.firstOrNull { it.key == key }?.value
    }

    private fun writeRawProperty(memory: Memory, key: String, value: String) {
        if (memory.id <= 0L) return
        memory.properties.reset()
        val existing = memory.properties.firstOrNull { it.key == key }
        if (existing != null) {
            existing.value = value
            propertyBox.put(existing)
        } else {
            val property = MemoryProperty(key = key, value = value)
            propertyBox.put(property)
            memory.properties.add(property)
        }
        memoryBox.put(memory)
    }

    private fun clearProperty(memory: Memory, key: String) {
        if (memory.id <= 0L) return
        memory.properties.reset()
        val toRemove = memory.properties.filter { it.key == key }
        if (toRemove.isNotEmpty()) {
            propertyBox.removeByIds(toRemove.map { it.id })
            memory.properties.reset()
        }
    }

    fun writeProperty(memory: Memory, key: String, value: String) {
        if (memory.id <= 0L) return
        if (key == PROP_STATUS) {
            store.runInTx {
                // Even a same-status write supersedes the old retirement's authority.
                clearProperty(memory, PROP_RETIRED_PREV_STATUS)
                writeRawProperty(memory, key, value)
            }
        } else {
            writeRawProperty(memory, key, value)
        }
    }

    fun initializeStatusIfAbsent(memory: Memory) {
        if (memory.id <= 0L) return
        store.runInTx {
            // A stale reinforcement read must not overwrite a concurrent retirement.
            if (readProperty(memory, PROP_STATUS) == null) {
                writeProperty(memory, PROP_STATUS, STATUS_ACTIVE)
            }
        }
    }

    fun markInactive(memory: Memory) {
        writeProperty(memory, PROP_STATUS, STATUS_INACTIVE)
    }

    fun retire(memory: Memory, reasonCode: String, linkType: String, sourceMemoryId: Long) {
        if (memory.id <= 0L) return
        store.runInTx {
            if (!readProperty(memory, PROP_RETIRED_PREV_STATUS).isNullOrBlank()) return@runInTx
            val priorStatus = readProperty(memory, PROP_STATUS)?.takeIf { it.isNotBlank() } ?: STATUS_ACTIVE
            writeRawProperty(memory, PROP_RETIRED_PREV_STATUS, priorStatus)
            writeRawProperty(memory, PROP_RETIRED_AT, nowMs().toString())
            writeRawProperty(memory, PROP_RETIRED_REASON, reasonCode)
            writeRawProperty(memory, PROP_RETIRED_BY_LINK_TYPE, linkType)
            writeRawProperty(memory, PROP_RETIRED_SOURCE_ID, sourceMemoryId.toString())
            writeRawProperty(memory, PROP_STATUS, STATUS_INACTIVE)
            clearProperty(memory, PROP_PENDING_RETIREMENT_REASON)
        }
    }

    fun restore(memory: Memory): Boolean {
        if (memory.id <= 0L) return false
        return store.callInTx {
            val decision = MemoryRetirementGovernance.evaluateRestore(
                currentStatus = readProperty(memory, PROP_STATUS),
                recordedPrevStatus = readProperty(memory, PROP_RETIRED_PREV_STATUS),
                recordedRetiredAt = readProperty(memory, PROP_RETIRED_AT),
                recordedReason = readProperty(memory, PROP_RETIRED_REASON),
                recordedLinkType = readProperty(memory, PROP_RETIRED_BY_LINK_TYPE),
                recordedSourceId = readProperty(memory, PROP_RETIRED_SOURCE_ID)
            )
            when (decision) {
                is MemoryRetirementGovernance.RestoreDecision.Refuse -> false
                is MemoryRetirementGovernance.RestoreDecision.Restore -> {
                    writeRawProperty(memory, PROP_STATUS, decision.priorStatus)
                    clearProperty(memory, PROP_RETIRED_PREV_STATUS)
                    clearProperty(memory, PROP_PENDING_RETIREMENT_REASON)
                    // Keep audit history; only the restorable marker is consumed.
                    writeRawProperty(memory, PROP_RESTORED_AT, nowMs().toString())
                    true
                }
            }
        }
    }
}
