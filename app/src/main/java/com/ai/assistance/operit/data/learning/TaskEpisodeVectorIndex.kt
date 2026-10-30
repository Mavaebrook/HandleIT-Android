package com.ai.assistance.operit.data.learning

import android.content.Context
import com.ai.assistance.operit.data.model.Embedding
import com.ai.assistance.operit.data.preferences.MemorySearchSettingsPreferences
import com.ai.assistance.operit.services.CloudEmbeddingService
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.OperitPaths
import com.ai.assistance.operit.util.vector.IndexItem
import com.ai.assistance.operit.util.vector.VectorIndexManager
import java.io.File

/**
 * Vector index for task episodes.
 *
 * This is a SEPARATE namespace under the existing vector directory
 * (`<filesDir>/.vector_index/episode_hnsw_<profile>_<dimension>.idx`), keyed by episode id. It
 * reuses [VectorIndexManager] and [IndexItem] unchanged and requires NO ObjectBox schema change:
 * the index is a serialized HNSW file on disk, not an entity, and the embedding vector is not
 * persisted on [com.ai.assistance.operit.data.model.TaskEpisodeEntity].
 *
 * Vector retrieval is an ADDITIONAL candidate generator for [TaskEpisodeRetrieval.mergeCandidates];
 * the lexical path remains a defined member of the merge.
 */
class TaskEpisodeVectorIndex(
    private val context: Context,
    profileId: String
) {
    companion object {
        private const val TAG = "TaskEpisodeVectorIndex"

        /**
         * Sanitizes a profile id for use in an index file name. This MUST match
         * MemoryRepository.sanitizeIndexKey so both subsystems agree on the file naming rule:
         * trim, substitute "default" when blank, then replace every character outside
         * `[a-zA-Z0-9._-]` with "_".
         */
        fun sanitizeIndexKey(raw: String): String {
            val normalized = raw.trim().ifBlank { "default" }
            return normalized.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        }
    }

    private val sanitizedProfileKey = sanitizeIndexKey(profileId)
    private val embeddingService = CloudEmbeddingService(context)
    private val searchSettings = MemorySearchSettingsPreferences(context, profileId)

    private fun indexFile(dimension: Int): File =
        File(OperitPaths.vectorIndexDir(context), "episode_hnsw_${sanitizedProfileKey}_${dimension}.idx")

    /**
     * True when the user has configured a usable embedding provider. When false, vector indexing
     * and search are DISABLED by definition: [indexEpisode] and [searchEpisodeSimilarities] are documented
     * no-ops that return without doing work. This is the defined disabled state, not a fallback.
     */
    fun isEnabled(): Boolean = searchSettings.loadCloudEmbedding().isReady()

    /**
     * Embed [text] for [episodeId] and add it to the index.
     *
     * When vector search is disabled ([isEnabled] is false) this is a documented no-op: the user
     * has not configured an embedding provider, so there is nothing to index with.
     */
    suspend fun indexEpisode(episodeId: Long, text: String) {
        val config = searchSettings.loadCloudEmbedding()
        if (!config.isReady()) {
            AppLogger.d(TAG, "Vector indexing disabled: no embedding provider configured")
            return
        }
        if (text.isBlank()) return
        val embedding: Embedding = embeddingService.generateEmbeddingOrThrow(config, text)
        val dimension = embedding.vector.size
        val manager = VectorIndexManager<IndexItem<Long, Long>, Long>(
            dimensions = dimension,
            maxElements = 1,
            indexFile = indexFile(dimension)
        )
        try {
            manager.addItem(
                IndexItem(
                    id = episodeId,
                    vector = embedding.vector,
                    version = episodeId,
                    value = episodeId
                )
            )
            manager.save()
        } finally {
            manager.close()
        }
    }

    /**
     * Return the cosine similarity of [query] to each of the (up to [k]) nearest episodes, keyed by
     * episode id.
     *
     * The similarity is returned, not just the id, because nearest-neighbour search always returns
     * the k nearest episodes however unrelated they are. Without the score the merge could not tell a
     * real match from the least-bad one, and every vector hit used to qualify automatically
     * (docs/TODO/episode-retrieval-threshold-20261030/README.md). The threshold itself is applied
     * by [TaskEpisodeRetrieval.mergeCandidates].
     *
     * When vector search is disabled ([isEnabled] is false) this returns an empty map: that is the
     * defined result of the disabled state, and [TaskEpisodeRetrieval.mergeCandidates] then merges
     * exactly the lexical candidates.
     */
    suspend fun searchEpisodeSimilarities(query: String, k: Int): Map<Long, Double> {
        val config = searchSettings.loadCloudEmbedding()
        if (!config.isReady()) {
            AppLogger.d(TAG, "Vector search disabled: no embedding provider configured")
            return emptyMap()
        }
        if (query.isBlank() || k <= 0) return emptyMap()
        val embedding: Embedding = embeddingService.generateEmbeddingOrThrow(config, query)
        val dimension = embedding.vector.size
        val file = indexFile(dimension)
        if (!file.exists()) return emptyMap()
        val manager = VectorIndexManager<IndexItem<Long, Long>, Long>(
            dimensions = dimension,
            maxElements = 1,
            indexFile = file
        )
        return try {
            val similarities = LinkedHashMap<Long, Double>()
            manager.findNearestWithDistance(embedding.vector, k).forEach { (item, distance) ->
                // FLOAT_COSINE_DISTANCE: similarity = 1 - distance. Keep the best score per episode.
                val similarity = 1.0 - distance.toDouble()
                val previous = similarities[item.value]
                if (previous == null || similarity > previous) similarities[item.value] = similarity
            }
            similarities
        } finally {
            manager.close()
        }
    }
}
