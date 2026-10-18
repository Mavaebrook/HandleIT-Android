package com.ai.assistance.operit.data.repository

import android.content.Context
import com.ai.assistance.operit.data.db.ObjectBoxManager
import com.ai.assistance.operit.data.learning.MigrationReport
import com.ai.assistance.operit.data.learning.StringListMigration
import com.ai.assistance.operit.data.learning.TaskEpisodeRetrieval
import com.ai.assistance.operit.data.learning.TaskEpisodeVectorIndex
import com.ai.assistance.operit.data.model.TaskEpisode
import com.ai.assistance.operit.data.model.TaskEpisodeEntity
import com.ai.assistance.operit.data.model.TaskEpisodeEntity_
import com.ai.assistance.operit.util.AppLogger
import io.objectbox.Box
import io.objectbox.kotlin.boxFor
import io.objectbox.kotlin.query
import io.objectbox.query.QueryBuilder

/**
 * Persistence for structured task episodes. Bounded retrieval is delegated to the pure
 * [TaskEpisodeRetrieval] core so ranking/dedup/applicability stay unit-testable.
 */
class TaskEpisodeRepository(
    private val context: Context,
    profileId: String
) : EpisodeStore {
    private val store = ObjectBoxManager.get(context, profileId)
    private val episodeBox: Box<TaskEpisodeEntity> = store.boxFor()

    /**
     * Additional candidate generator for [retrieveRelevant]: a separate vector namespace keyed by
     * episode id. It is not an ObjectBox entity, so no schema change is involved.
     */
    private val vectorIndex = TaskEpisodeVectorIndex(context, profileId)

    override fun save(episode: TaskEpisode): Long {
        return episodeBox.put(TaskEpisodeEntity.fromEpisode(episode))
    }

    /**
     * Add an ALREADY SAVED episode to the episode vector index.
     *
     * The episode must already be persisted: this method only adds it to the vector index, it does
     * not save anything. [episodeId] is the id ObjectBox returned from [save], so the index always
     * refers to a stored episode.
     *
     * When the user has not configured an embedding provider, [TaskEpisodeVectorIndex.indexEpisode]
     * is a documented no-op (its disabled state), so this returns without doing work.
     *
     * Exceptions raised while embedding or writing the index propagate to the caller, which decides
     * how to handle them; this method does not swallow them.
     */
    override suspend fun indexSavedEpisode(episodeId: Long, episode: TaskEpisode) {
        vectorIndex.indexEpisode(episodeId, episodeIndexText(episode))
    }

    /**
     * Persist [episode] and then add it to the episode vector index.
     *
     * The episode is saved FIRST so the id returned by ObjectBox is the id used as the vector index
     * key; the index therefore always refers to a stored episode. Indexing itself is delegated to
     * [indexSavedEpisode], so there is exactly one text-building implementation
     * ([episodeIndexText]).
     *
     * When the user has not configured an embedding provider, [TaskEpisodeVectorIndex.indexEpisode]
     * is a documented no-op (its disabled state), so the episode is still saved and simply not
     * indexed.
     *
     * @return the stored episode id.
     */
    suspend fun indexEpisode(episode: TaskEpisode): Long {
        val savedId = save(episode)
        indexSavedEpisode(savedId, episode)
        return savedId
    }

    // No `reindexMissing` method is provided on purpose. Before the review scheduler was wired to
    // indexSavedEpisode, indexEpisode had zero callers, so no production episode was ever indexed
    // and there is no historical backlog to rebuild. The vector index is a derived cache: it is
    // populated by future saves through indexSavedEpisode, so there is nothing to re-index.

    /**
     * Every stored episode, unordered and unbounded.
     *
     * NOT used by retrieval: [retrieveRelevant] reads only the bounded recency window
     * ([recentCandidates]) so the whole table is never loaded for ranking. This method exists for
     * diagnostics and tests only.
     */
    fun all(): List<TaskEpisode> {
        return episodeBox.query().build().find().map { it.toEpisode() }
    }

    fun count(): Long = episodeBox.count()

    /**
     * Bounded similar-task retrieval: at most clampTopK(topK) episodes.
     *
     * Two candidate generators feed one merge rule ([TaskEpisodeRetrieval.mergeCandidates]):
     *  - lexical: the most recent [MAX_CANDIDATES_EXAMINED] episodes by createdAt descending,
     *    scored by [TaskEpisodeRetrieval.scoreEpisodes];
     *  - vector: up to [MAX_CANDIDATES_EXAMINED] episode ids from the episode vector index.
     * The lexical path is a defined member of the union, not a fallback. When the user has not
     * configured an embedding provider the vector generator returns no ids by its documented
     * disabled-state rule and the merge contains exactly the lexical candidates.
     */
    suspend fun retrieveRelevant(query: String, topK: Int = TaskEpisodeRetrieval.DEFAULT_TOP_K): List<TaskEpisode> {
        val lexicalCandidates = recentCandidates()
        val lexical = TaskEpisodeRetrieval.scoreEpisodes(query, lexicalCandidates)
        val vectorIds = vectorIndex.searchEpisodeIds(query, MAX_CANDIDATES_EXAMINED).toSet()
        val vectorCandidates =
            if (vectorIds.isEmpty()) {
                emptyList()
            } else {
                episodeBox.get(vectorIds.toList()).filterNotNull().map { it.toEpisode() }
            }
        return TaskEpisodeRetrieval.mergeCandidates(
            query = query,
            lexical = lexical,
            vectorIds = vectorIds,
            vectorCandidates = vectorCandidates,
            topK = topK
        )
    }

    /**
     * The bounded candidate window: at most [MAX_CANDIDATES_EXAMINED] most recent episodes.
     */
    private fun recentCandidates(): List<TaskEpisode> {
        return episodeBox
            .query()
            .order(TaskEpisodeEntity_.createdAt, QueryBuilder.DESCENDING)
            .build()
            .find(0, MAX_CANDIDATES_EXAMINED.toLong())
            .map { it.toEpisode() }
    }

    fun delete(episodeId: Long) {
        episodeBox.remove(episodeId)
    }

    /**
     * Rewrite the list columns of existing rows from the legacy newline-join encoding to the
     * [com.ai.assistance.operit.data.learning.StringListCodec] version envelope (D07).
     *
     * There is no schema change: the column names, the Kotlin field names and the ObjectBox
     * property ids are untouched. Only the string CONTENT of rows whose content actually changes is
     * rewritten.
     *
     * Behaviour, per row:
     *  - every list field already versioned → the row is not written, counted in
     *    [MigrationReport.alreadyVersioned];
     *  - at least one legacy field and no corrupt field → the row is rewritten with the migrated
     *    content, counted in [MigrationReport.migrated];
     *  - any corrupt or unsupported-version field → the row is NOT written at all (so a partially
     *    migrated row cannot exist), counted in [MigrationReport.skippedCorrupt] and logged with the
     *    row id and field name.
     *
     * Idempotent: running it twice produces the same bytes and the second run reports every row as
     * already versioned.
     *
     * Rows are processed in bounded batches of [MIGRATION_BATCH_SIZE] inside one ObjectBox
     * transaction per batch, so a large table is never loaded or written in a single transaction.
     *
     * NOT invoked automatically on app start. Invoking it is a separate, device-verified step
     * (A01); this method only provides the explicit path.
     */
    fun migrateListFieldsToVersioned(): MigrationReport {
        var report = MigrationReport()
        var offset = 0L
        while (true) {
            val batch = episodeBox
                .query()
                .order(TaskEpisodeEntity_.id)
                .build()
                .find(offset, MIGRATION_BATCH_SIZE.toLong())
            if (batch.isEmpty()) break
            var batchReport = MigrationReport()
            store.runInTx { batchReport = migrateBatch(batch) }
            report += batchReport
            offset += batch.size
        }
        AppLogger.i(
            TAG,
            "TaskEpisodeEntity list migration: migrated=${report.migrated} " +
                "alreadyVersioned=${report.alreadyVersioned} skippedCorrupt=${report.skippedCorrupt}"
        )
        return report
    }

    /**
     * Migrate one bounded batch inside the caller's transaction. Returns the counts for the batch.
     */
    private fun migrateBatch(batch: List<TaskEpisodeEntity>): MigrationReport {
        var migrated = 0
        var alreadyVersioned = 0
        var skippedCorrupt = 0
        for (entity in batch) {
            var changed = false
            var needsAttention = false
            for (fieldName in TaskEpisodeEntity.LIST_FIELDS) {
                val result = StringListMigration.migrateValue(entity.rawListField(fieldName))
                when (result.outcome) {
                    StringListMigration.Outcome.MIGRATED -> {
                        setListField(entity, fieldName, result.value)
                        changed = true
                    }

                    StringListMigration.Outcome.ALREADY_VERSIONED -> Unit

                    StringListMigration.Outcome.NEEDS_ATTENTION -> {
                        needsAttention = true
                        AppLogger.e(
                            TAG,
                            "TaskEpisodeEntity id=${entity.id} field '$fieldName' needs attention " +
                                "and was left untouched: ${result.detail}"
                        )
                    }
                }
            }
            when {
                needsAttention -> skippedCorrupt++
                changed -> {
                    episodeBox.put(entity)
                    migrated++
                }

                else -> alreadyVersioned++
            }
        }
        return MigrationReport(
            migrated = migrated,
            alreadyVersioned = alreadyVersioned,
            skippedCorrupt = skippedCorrupt
        )
    }

    private fun setListField(entity: TaskEpisodeEntity, fieldName: String, value: String) {
        when (fieldName) {
            TaskEpisodeEntity.FIELD_REQUIRED_PREREQUISITES -> entity.requiredPrerequisitesJoined = value
            TaskEpisodeEntity.FIELD_APPLICABLE_CONSTRAINTS -> entity.applicableConstraintsJoined = value
            TaskEpisodeEntity.FIELD_EVIDENCE_KEYS -> entity.evidenceKeysJoined = value
            else -> throw IllegalArgumentException("TaskEpisodeEntity has no list field '$fieldName'")
        }
    }

    // FOLLOW-UP (C03 part d, NOT implemented): graph expansion over MemoryLink for episodes.
    // MemoryLink.source and MemoryLink.target are ToOne<Memory> (data/model/Memory.kt), and
    // TaskEpisodeEntity has no link field, so an episode cannot be linked to a memory or to another
    // episode without a schema change. Implementing it requires either a new ObjectBox entity
    // (for example EpisodeLinkEntity with sourceEpisodeId, targetEpisodeId and memoryId) or new
    // fields on TaskEpisodeEntity. Encoding ids into MemoryLink.description or type strings would
    // give no referential integrity and no indexed traversal, so it is deliberately not done.
    // The intended edges are: episode -> failure, episode -> fix, episode -> prerequisite.

    companion object {
        private const val TAG = "TaskEpisodeRepository"

        /**
         * Maximum number of episodes examined by [retrieveRelevant].
         *
         * Retrieval must not load the whole table: the candidate set is the most recent
         * [MAX_CANDIDATES_EXAMINED] episodes by createdAt descending, and ranking then happens over
         * that bounded set. Trade-off: an older episode outside the recency window is not considered.
         */
        const val MAX_CANDIDATES_EXAMINED = 200

        /**
         * Rows per transaction for [migrateListFieldsToVersioned]. Bounded so a large table is never
         * loaded or written in one transaction.
         */
        const val MIGRATION_BATCH_SIZE = 100

        /**
         * The text that is embedded for an episode: [TaskEpisode.taskSummary],
         * [TaskEpisode.contextSummary] and [TaskEpisode.resolutionSummary], joined by single spaces.
         *
         * Those three fields are the "what was the task, in what situation, and how was it resolved"
         * triple that vector similarity is meant to capture; failure/cause text is deliberately
         * excluded so a past failure does not pull unrelated failing tasks towards it.
         *
         * Pure and side-effect free so the documented text is directly unit-testable.
         */
        internal fun episodeIndexText(episode: TaskEpisode): String =
            listOf(
                episode.taskSummary,
                episode.contextSummary,
                episode.resolutionSummary
            ).joinToString(" ")
    }
}
