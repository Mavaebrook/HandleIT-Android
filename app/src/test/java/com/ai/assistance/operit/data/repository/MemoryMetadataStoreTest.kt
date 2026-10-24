package com.ai.assistance.operit.data.repository

import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.data.model.MemoryProperty
import com.ai.assistance.operit.data.model.MyObjectBox
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
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Native ObjectBox tests of the production metadata store; execution is hosted JVM CI only. */
class MemoryMetadataStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()
    private lateinit var directory: File
    private lateinit var store: BoxStore
    private lateinit var metadata: MemoryMetadataStore
    private var timestamp = 1000L
    private var failTimestamp = false

    @Before
    fun setUp() {
        directory = temporaryFolder.newFolder("memory-metadata")
        openStore()
    }

    @After
    fun tearDown() {
        if (::store.isInitialized && !store.isClosed) store.close()
    }

    private fun openStore() {
        store = MyObjectBox.builder().directory(directory).build()
        metadata = MemoryMetadataStore(store) {
            check(!failTimestamp) { "injected timestamp failure" }
            timestamp
        }
    }

    private fun reopen() {
        store.close()
        openStore()
    }

    private fun newMemory(): Memory {
        val memory = Memory(title = "Preserved fact", content = "Stored content")
        store.boxFor<Memory>().put(memory)
        metadata.initializeStatusIfAbsent(memory)
        return memory
    }

    private fun reload(id: Long): Memory = checkNotNull(store.boxFor<Memory>().get(id))

    private fun snapshot(id: Long): Map<String, String> {
        val memory = reload(id)
        memory.properties.reset()
        return memory.properties.associate { it.key to it.value }
    }

    private fun retire(memory: Memory) = metadata.retire(memory, "ACCEPTED", "CORRECTS", 42L)

    // Delete a real persisted row to model an incomplete historical/corrupt audit.
    private fun removeProperty(memory: Memory, key: String) {
        store.runInTx {
            memory.properties.reset()
            val rows = memory.properties.filter { it.key == key }
            store.boxFor<MemoryProperty>().removeByIds(rows.map { it.id })
        }
    }

    // Directly rewrite the persisted status value (bypassing MemoryMetadataStore.writeProperty
    // so the retirement marker is not cleared), modelling a blank status row.
    private fun writeBlankStatus(memory: Memory) {
        store.runInTx {
            val m = reload(memory.id)
            m.properties.reset()
            val row = m.properties.first { it.key == PROP_STATUS }
            row.value = "   "
            store.boxFor<MemoryProperty>().put(row)
        }
    }

    @Test
    fun retirementAndRestore_surviveReopenAndRetainAudit() {
        val memory = newMemory()
        metadata.writeProperty(memory, PROP_PENDING_RETIREMENT_REASON, "INSUFFICIENT_EVIDENCE")
        retire(memory)
        val retired = snapshot(memory.id)
        assertEquals(STATUS_INACTIVE, retired[PROP_STATUS])
        assertEquals(STATUS_ACTIVE, retired[PROP_RETIRED_PREV_STATUS])
        assertEquals("1000", retired[PROP_RETIRED_AT])
        assertEquals("ACCEPTED", retired[PROP_RETIRED_REASON])
        assertEquals("CORRECTS", retired[PROP_RETIRED_BY_LINK_TYPE])
        assertEquals("42", retired[PROP_RETIRED_SOURCE_ID])
        assertNull(retired[PROP_PENDING_RETIREMENT_REASON])
        reopen()
        timestamp = 2000L
        assertTrue(metadata.restore(reload(memory.id)))
        reopen()
        val restored = snapshot(memory.id)
        assertEquals(STATUS_ACTIVE, restored[PROP_STATUS])
        assertEquals("2000", restored[PROP_RESTORED_AT])
        assertNull(restored[PROP_RETIRED_PREV_STATUS])
        assertNull(restored[PROP_PENDING_RETIREMENT_REASON])
        listOf(PROP_RETIRED_AT, PROP_RETIRED_REASON, PROP_RETIRED_BY_LINK_TYPE, PROP_RETIRED_SOURCE_ID)
            .forEach { key -> assertEquals(retired[key], restored[key]) }
        timestamp = 3000L
        assertFalse(metadata.restore(reload(memory.id)))
        assertEquals(restored, snapshot(memory.id))
    }

    @Test
    fun deliberateSameStatusDeactivation_invalidatesRestoreAfterReopen() {
        val memory = newMemory()
        retire(memory)
        val retired = snapshot(memory.id)
        metadata.markInactive(memory)
        reopen()
        val deactivated = snapshot(memory.id)
        assertEquals(retired - PROP_RETIRED_PREV_STATUS, deactivated)
        assertFalse(metadata.restore(reload(memory.id)))
        assertEquals(deactivated, snapshot(memory.id))
        assertEquals(STATUS_INACTIVE, deactivated[PROP_STATUS])
    }

    @Test
    fun ordinaryActiveStatusWrite_invalidatesRestoreMarker() {
        val memory = newMemory()
        retire(memory)
        metadata.writeProperty(memory, PROP_STATUS, STATUS_ACTIVE)
        assertNull(snapshot(memory.id)[PROP_RETIRED_PREV_STATUS])
        assertFalse(metadata.restore(reload(memory.id)))
        assertEquals(STATUS_ACTIVE, snapshot(memory.id)[PROP_STATUS])
    }

    @Test
    fun missingAuditFields_refuseWithoutPersistedChanges() {
        listOf(PROP_RETIRED_PREV_STATUS, PROP_RETIRED_AT, PROP_RETIRED_REASON,
            PROP_RETIRED_BY_LINK_TYPE, PROP_RETIRED_SOURCE_ID).forEach { key ->
            val memory = newMemory()
            retire(memory)
            removeProperty(memory, key)
            val before = snapshot(memory.id)
            assertFalse("missing " + key, metadata.restore(reload(memory.id)))
            assertEquals(before, snapshot(memory.id))
        }
    }

    @Test
    fun invalidAuditFields_refuseWithoutPersistedChanges() {
        listOf(
            PROP_RETIRED_PREV_STATUS to " ",
            PROP_RETIRED_AT to "not-a-time", PROP_RETIRED_AT to "0", PROP_RETIRED_AT to "-1",
            PROP_RETIRED_REASON to "", PROP_RETIRED_REASON to "INSUFFICIENT_EVIDENCE",
            PROP_RETIRED_BY_LINK_TYPE to "RELATES_TO",
            PROP_RETIRED_SOURCE_ID to "0", PROP_RETIRED_SOURCE_ID to "-1",
            PROP_RETIRED_SOURCE_ID to "not-an-id"
        ).forEach { (key, value) ->
            val memory = newMemory()
            retire(memory)
            metadata.writeProperty(memory, key, value)
            val before = snapshot(memory.id)
            assertFalse("invalid " + key, metadata.restore(reload(memory.id)))
            assertEquals(before, snapshot(memory.id))
        }
    }

    @Test
    fun failedRetirement_rollsBackPriorStatusMarker() {
        val memory = newMemory()
        val before = snapshot(memory.id)
        failTimestamp = true
        // Timestamp acquisition follows the marker write inside the real transaction.
        assertThrows(IllegalStateException::class.java) { retire(memory) }
        reopen()
        assertEquals(before, snapshot(memory.id))
    }

    @Test
    fun failedRestore_rollsBackStatusMarkerAndPendingDecision() {
        val memory = newMemory()
        retire(memory)
        metadata.writeProperty(memory, PROP_PENDING_RETIREMENT_REASON, "INSUFFICIENT_EVIDENCE")
        val before = snapshot(memory.id)
        failTimestamp = true
        // restored_at follows status write and marker/pending deletion.
        assertThrows(IllegalStateException::class.java) { metadata.restore(memory) }
        reopen()
        assertEquals(before, snapshot(memory.id))
    }

    @Test
    fun deliberateDeactivation_participatesInOuterTransactionRollback() {
        val memory = newMemory()
        retire(memory)
        val before = snapshot(memory.id)
        assertThrows(IllegalStateException::class.java) {
            store.runInTx {
                metadata.markInactive(memory)
                check(false) { "abort outer transaction" }
            }
        }
        reopen()
        assertEquals(before, snapshot(memory.id))
        assertTrue(metadata.restore(reload(memory.id)))
    }

    @Test
    fun reinforcementInitialization_preservesInactiveStatusAndMarker() {
        val memory = newMemory()
        retire(memory)
        val before = snapshot(memory.id)
        metadata.initializeStatusIfAbsent(memory)
        assertEquals(before, snapshot(memory.id))
    }

    @Test
    fun missingStatusInitialization_invalidatesStaleMarker() {
        val memory = newMemory()
        retire(memory)
        removeProperty(memory, PROP_STATUS)
        metadata.initializeStatusIfAbsent(reload(memory.id))
        reopen()
        assertEquals(STATUS_ACTIVE, snapshot(memory.id)[PROP_STATUS])
        assertNull(snapshot(memory.id)[PROP_RETIRED_PREV_STATUS])
        assertFalse(metadata.restore(reload(memory.id)))
    }

    @Test
    fun retire_alreadyInactiveWithoutMarker_doesNotRecordInactivePriorStatus() {
        val memory = newMemory()
        metadata.markInactive(memory)
        val before = snapshot(memory.id)
        assertEquals(STATUS_INACTIVE, before[PROP_STATUS])
        assertNull(before[PROP_RETIRED_PREV_STATUS])
        retire(memory)
        assertEquals(before, snapshot(memory.id))
        assertNull(snapshot(memory.id)[PROP_RETIRED_PREV_STATUS])
        assertFalse(metadata.restore(reload(memory.id)))
    }

    @Test
    fun blankStatusInitialization_overwritesBlankWithActiveAndInvalidatesStaleMarker() {
        val memory = newMemory()
        retire(memory)
        writeBlankStatus(memory)
        assertEquals("   ", snapshot(memory.id)[PROP_STATUS])
        assertEquals(STATUS_ACTIVE, snapshot(memory.id)[PROP_RETIRED_PREV_STATUS])
        metadata.initializeStatusIfAbsent(reload(memory.id))
        reopen()
        assertEquals(STATUS_ACTIVE, snapshot(memory.id)[PROP_STATUS])
        assertNull(snapshot(memory.id)[PROP_RETIRED_PREV_STATUS])
        assertFalse(metadata.restore(reload(memory.id)))
    }
}
