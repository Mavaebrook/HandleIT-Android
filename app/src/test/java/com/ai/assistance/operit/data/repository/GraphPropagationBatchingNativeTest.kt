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

    /** Insert a memory; ObjectBox assigns the id (put with a preset id is rejected on new rows). */
    private fun memory(label: String): Memory {
        val memory = Memory(title = label, content = "content-$label")
        memoryBox.put(memory)
        return memory
    }

    private fun link(source: Memory, target: Memory, weight: Float): MemoryLink {
        val link = MemoryLink(type = "related", weight = weight)
        link.source.target = source
        link.target.target = target
        linkBox.put(link)
        return link
    }

    @Test
    fun relationQueryIn_loadsAllSeedsLinksWithTwoBoxQueries() {
        val m1 = memory("m1")
        val m2 = memory("m2")
        val m3 = memory("m3")
        val m4 = memory("m4")
        val m5 = memory("m5")
        link(m1, m3, 0.5f)
        link(m1, m4, 0.2f)
        link(m2, m5, 0.8f)
        link(m3, m1, 0.3f) // backlink into seed 1

        val seeds = longArrayOf(m1.id, m2.id)
        val outgoing = linkBox.query().`in`(MemoryLink_.sourceId, seeds).build().find()
        val incoming = linkBox.query().`in`(MemoryLink_.targetId, seeds).build().find()

        // Stored endpoint ids are readable without resolving ToOne targets.
        val outgoingPairs = outgoing.map { it.source.targetId to it.target.targetId }.toSet()
        val incomingPairs = incoming.map { it.source.targetId to it.target.targetId }.toSet()
        assertEquals(setOf(m1.id to m3.id, m1.id to m4.id, m2.id to m5.id), outgoingPairs)
        assertEquals(setOf(m3.id to m1.id), incomingPairs)
    }

    @Test
    fun batchedPropagation_matchesLegacyStoreTraversalExactly() {
        val m1 = memory("m1")
        val m2 = memory("m2")
        val m3 = memory("m3")
        val m4 = memory("m4")
        val m5 = memory("m5")
        val m6 = memory("m6") // out of scope for this query
        link(m1, m3, 0.5f)
        link(m1, m4, 0.2f)
        link(m2, m5, 0.8f)
        link(m2, m6, 0.6f)
        link(m3, m1, 0.3f)
        link(m4, m2, -0.5f)

        val allowed = setOf(m1.id, m2.id, m3.id, m4.id, m5.id)
        val seeds = listOf(m1.id, m2.id)
        val graphWeight = 2.0

        // Legacy traversal: exactly the pre-batching loop.
        val legacyScores = mutableMapOf(m1.id to 10.0, m2.id to 20.0)
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
        val batchedScores = mutableMapOf(m1.id to 10.0, m2.id to 20.0)
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