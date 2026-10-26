package com.ai.assistance.operit.data.repository

import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.data.model.MemoryLink
import com.ai.assistance.operit.data.model.MemoryLink_
import com.ai.assistance.operit.data.model.MyObjectBox
import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Native ObjectBox tests of the D04b batched link queries; execution is hosted JVM CI only.
 *
 * These verify the relation-query assessment against ObjectBox 5.3.0 (`QueryBuilder.in` over
 * [MemoryLink_.sourceId] / [MemoryLink_.targetId]), confirm that stored endpoint ids can be
 * read without resolving ToOne targets, and assert exact score parity between the legacy
 * per-seed/per-link store traversal and the batched path on identical data.
 */
class GraphPropagationBatchingNativeTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var directory: File
    private lateinit var store: BoxStore
    private lateinit var memoryBox: Box<Memory>
    private lateinit var linkBox: Box<MemoryLink>

    @Before
    fun setUp() {
        directory = temporaryFolder.newFolder("d04b-batching")
        store = MyObjectBox.builder().directory(directory).build()
        memoryBox = store.boxFor()
        linkBox = store.boxFor()
    }

    @After
    fun tearDown() {
        if (::store.isInitialized && !store.isClosed) store.close()
    }

    private fun memory(id: Long) {
        val memory = Memory(title = "m$id", content = "c$id")
        memory.id = id
        memoryBox.put(memory)
    }

    private fun link(id: Long, sourceId: Long, targetId: Long, weight: Float) {
        val source = memoryBox.get(sourceId) ?: error("source $sourceId missing")
        val target = memoryBox.get(targetId) ?: error("target $targetId missing")
        val link = MemoryLink(id = id, type = "related", weight = weight)
        link.source.target = source
        link.target.target = target
        linkBox.put(link)
    }

    @Test
    fun relationQueryIn_loadsAllSeedsLinksWithTwoBoxQueries() {
        listOf(1L, 2L, 3L, 4L, 5L).forEach { memory(it) }
        link(11, 1, 3, 0.5f)
        link(12, 1, 4, 0.2f)
        link(13, 2, 5, 0.8f)
        link(14, 3, 1, 0.3f) // backlink into seed 1

        val seeds = longArrayOf(1L, 2L)
        val outgoing = linkBox.query().`in`(MemoryLink_.sourceId, seeds).build().find()
        val incoming = linkBox.query().`in`(MemoryLink_.targetId, seeds).build().find()

        assertEquals(setOf(11L, 12L, 13L), outgoing.map { it.id }.toSet())
        assertEquals(setOf(14L), incoming.map { it.id }.toSet())
        // Stored endpoint ids are readable without resolving ToOne targets.
        outgoing.forEach { row ->
            val expected = when (row.id) {
                11L -> 1L to 3L
                12L -> 1L to 4L
                else -> 2L to 5L
            }
            assertEquals(expected.first, row.source.targetId)
            assertEquals(expected.second, row.target.targetId)
        }
        assertEquals(3L, incoming.first().source.targetId)
        assertEquals(1L, incoming.first().target.targetId)
    }

    @Test
    fun batchedPropagation_matchesLegacyStoreTraversalExactly() {
        listOf(1L, 2L, 3L, 4L, 5L, 6L).forEach { memory(it) }
        // memory 6 is out of scope for this query.
        link(21, 1, 3, 0.5f)
        link(22, 1, 4, 0.2f)
        link(23, 2, 5, 0.8f)
        link(24, 2, 6, 0.6f)
        link(26, 3, 1, 0.3f)
        link(27, 4, 2, -0.5f)

        val allowed = setOf(1L, 2L, 3L, 4L, 5L)
        val seeds = listOf(1L, 2L)
        val graphWeight = 2.0

        // Legacy traversal: exactly the pre-batching loop.
        val legacyScores = mutableMapOf(1L to 10.0, 2L to 20.0)
        seeds.forEach { seed ->
            val sourceMemory = memoryBox.get(seed) ?: return@forEach
            sourceMemory.links.reset()
            sourceMemory.backlinks.reset()
            val sourceScore = legacyScores[seed] ?: 0.0
            sourceMemory.links.forEach { l ->
                val targetMemory = l.target.target
                if (targetMemory != null && MemoryGraphScopeFilter.shouldPropagateTo(allowed, targetMemory.id)) {
                    val c = MemoryPropagationScoring.propagatedScore(sourceScore, l.weight, graphWeight)
                    MemoryPropagationScoring.addContribution(legacyScores, targetMemory.id, c) {}
                }
            }
            sourceMemory.backlinks.forEach { l ->
                val targetMemory = l.source.target
                if (targetMemory != null && MemoryGraphScopeFilter.shouldPropagateTo(allowed, targetMemory.id)) {
                    val c = MemoryPropagationScoring.propagatedScore(sourceScore, l.weight, graphWeight)
                    MemoryPropagationScoring.addContribution(legacyScores, targetMemory.id, c) {}
                }
            }
        }

        // Batched path: the production implementation.
        val batchedScores = mutableMapOf(1L to 10.0, 2L to 20.0)
        val outgoingRows = linkBox.query().`in`(MemoryLink_.sourceId, seeds.toLongArray()).build().find()
            .map { GraphPropagationBatching.LinkRow(it.id, it.source.targetId, it.target.targetId, it.weight) }
        val incomingRows = linkBox.query().`in`(MemoryLink_.targetId, seeds.toLongArray()).build().find()
            .map { GraphPropagationBatching.LinkRow(it.id, it.source.targetId, it.target.targetId, it.weight) }
        val endpointIds = (outgoingRows.map { it.targetId } + incomingRows.map { it.sourceId }).toHashSet()
        val existingEndpointIds = memoryBox.get(endpointIds.toList()).filterNotNull().map { it.id }.toHashSet()
        val stats = GraphPropagationBatching.propagate(
            seeds = seeds,
            outgoingLinks = outgoingRows,
            incomingLinks = incomingRows,
            scores = batchedScores,
            allowedScopedIds = allowed,
            existingEndpointIds = existingEndpointIds,
            graphPropagationWeight = graphWeight
        ) { _, _ -> }

        assertEquals(legacyScores, batchedScores)
        assertEquals(5, stats.edgesTraversed)
        assertEquals(0, stats.skippedDangling)
        assertEquals(1, stats.skippedOutOfScope)
        assertEquals(1, stats.skippedNonPositive)
        assertEquals(10, stats.legacyQueryCount)
        assertEquals(3, stats.batchedQueryCount)
        assertTrue(stats.queryRoundTripsSaved > 0)
    }
}