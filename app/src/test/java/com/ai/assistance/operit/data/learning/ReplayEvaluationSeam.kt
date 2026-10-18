package com.ai.assistance.operit.data.learning

/*
 * Evaluation seam for replay comparisons (Step 1, Area 1). TEST-ONLY.
 *
 * This is a SEAM, not an optimization engine. It defines the smallest stable shape needed to run a
 * fixture through some retrieval+formatter implementation and to compare two such runs later
 * (baseline vs candidate). It deliberately contains no scoring, no ranking, no promotion gate and no
 * model call: those belong to a later step, and inventing them here would make the baseline
 * meaningless.
 *
 * No Android dependency: [TaskEpisode] is a plain data class and the production logic exercised here
 * ([TaskEpisodeRetrieval], [TaskPreflightFormatter]) is pure.
 */

/**
 * The observable result of replaying one fixture.
 *
 * @param fixtureId the fixture this outcome belongs to.
 * @param retrievedIds the ids retrieval returned, in ranked order.
 * @param cautionLines the formatter block split into non-blank lines, in rendered order.
 */
data class ReplayOutcome(
    val fixtureId: String,
    val retrievedIds: List<Long>,
    val cautionLines: List<String>
)

/** A replay implementation: fixture in, outcome out. */
interface ReplayRunner {
    fun run(fixture: ReplayFixture): ReplayOutcome
}

/**
 * The BASELINE runner: the current production retrieval and formatter, unchanged.
 *
 * Retrieval is [TaskEpisodeRetrieval.rankRelevantEpisodes] with the same bounded top-k the harness
 * asserts on, and formatting is [TaskPreflightFormatter.buildPreflightBlock] in English. Nothing is
 * re-implemented here, so a baseline outcome is by construction the current production behaviour.
 */
object CurrentReplayRunner : ReplayRunner {

    /** The bounded top-k used by the harness; matches the production default. */
    const val TOP_K = 3

    override fun run(fixture: ReplayFixture): ReplayOutcome {
        val retrieved = TaskEpisodeRetrieval.rankRelevantEpisodes(
            query = fixture.query,
            episodes = fixture.episodes,
            topK = TOP_K
        )
        val block = TaskPreflightFormatter.buildPreflightBlock(
            episodes = retrieved,
            currentTaskContext = fixture.currentTaskContext,
            useEnglish = true
        )
        return ReplayOutcome(
            fixtureId = fixture.id,
            retrievedIds = retrieved.map { it.id },
            cautionLines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
        )
    }
}

/**
 * A baseline-vs-candidate comparison over the same fixtures.
 *
 * [diff] reports per-fixture differences in retrieved ids and in rendered caution lines. It is the
 * extension point for a later candidate implementation; it does not decide which side is better and
 * does not implement any optimization.
 */
data class ReplayComparison(
    val baseline: List<ReplayOutcome>,
    val candidate: List<ReplayOutcome>
) {

    /**
     * Per-fixture difference report, ordered by fixture id for determinism.
     *
     * A fixture present on only one side is reported as such rather than silently skipped: a
     * comparison that drops fixtures would hide exactly the regressions it exists to catch.
     */
    fun diff(): List<String> {
        val baselineById = baseline.associateBy { it.fixtureId }
        val candidateById = candidate.associateBy { it.fixtureId }
        val fixtureIds = (baselineById.keys + candidateById.keys).sorted()

        val report = mutableListOf<String>()
        fixtureIds.forEach { fixtureId ->
            val base = baselineById[fixtureId]
            val cand = candidateById[fixtureId]
            when {
                base == null -> report.add("$fixtureId: missing from baseline")
                cand == null -> report.add("$fixtureId: missing from candidate")
                base == cand -> Unit // identical outcome, nothing to report
                else -> {
                    if (base.retrievedIds != cand.retrievedIds) {
                        report.add(
                            "$fixtureId: retrievedIds baseline=${base.retrievedIds} candidate=${cand.retrievedIds}"
                        )
                    }
                    if (base.cautionLines != cand.cautionLines) {
                        report.add(
                            "$fixtureId: cautionLines baseline=${base.cautionLines} candidate=${cand.cautionLines}"
                        )
                    }
                }
            }
        }
        return report
    }
}
