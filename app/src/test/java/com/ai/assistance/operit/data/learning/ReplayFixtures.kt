package com.ai.assistance.operit.data.learning

import com.ai.assistance.operit.data.model.TaskEpisode

/*
 * All fixtures are SYNTHETIC. No sanitized real fixtures are included because no actual evidence is
 * available; inventing real outcomes is forbidden. Real fixtures may be added later only when actual
 * evidence exists.
 *
 * This file is TEST-ONLY data. It contains no production logic and no Android dependency: a
 * [TaskEpisode] is a plain data class, so the whole fixture set is usable from a plain JVM test.
 *
 * The set is FIXED and BOUNDED (8 fixtures, one per required scenario). It is deliberately not
 * generated, not randomized and not extended at runtime: a replay harness is only useful if the
 * same inputs always produce the same outputs.
 *
 * HOW THE EXPECTATIONS WERE PRODUCED: every expectation below was obtained by running the CURRENT
 * production logic ([TaskEpisodeRetrieval], [TaskPreflightFormatter]) over the fixture, not by
 * writing down what the behaviour ought to be. Where the current behaviour is wrong, the fixture
 * records the wrong output and the harness test marks it `KNOWN LIMITATION`, so a later fix changes
 * the assertion visibly instead of silently passing.
 */

/**
 * Which evaluation family a fixture belongs to.
 *
 * DEVELOPMENT fixtures are the ones a change may be developed against; HELD_OUT fixtures are kept
 * apart so that a later change cannot be tuned to every fixture it is measured on. The split is
 * fixed here and must not be re-shuffled to make a candidate look better.
 */
enum class FixtureFamily {
    DEVELOPMENT,
    HELD_OUT
}

/**
 * Where a fixture's content came from.
 *
 * [SYNTHETIC] means the episode text was written by hand for this harness. [SANITIZED_REAL] would
 * mean it was derived from an actual recorded episode with secrets removed; that label may only be
 * used when such evidence actually exists. Every fixture in this file is [SYNTHETIC].
 */
enum class FixtureProvenance {
    SYNTHETIC,
    SANITIZED_REAL
}

/**
 * One replay fixture: a query, the present task context, the candidate episodes, and the outputs the
 * CURRENT production logic is expected to produce for them.
 *
 * @param id stable fixture id, used in reports and in [ReplayOutcome].
 * @param family development or held-out family (see [FixtureFamily]).
 * @param provenance where the content came from (see [FixtureProvenance]).
 * @param description what scenario the fixture exercises, in one line.
 * @param query the retrieval query (what the present task is about).
 * @param currentTaskContext the present task context passed to the formatter for applicability.
 * @param episodes the candidate episodes retrieval ranks over.
 * @param expectedTopEpisodeId the id the CURRENT retrieval is expected to rank first, or null when
 *        the fixture does not assert a top hit (e.g. a known-limitation fixture where retrieval
 *        returns nothing).
 * @param expectedCautionContains substrings the CURRENT formatter block is expected to contain.
 * @param expectedCautionAbsent substrings the CURRENT formatter block is expected NOT to contain.
 */
data class ReplayFixture(
    val id: String,
    val family: FixtureFamily,
    val provenance: FixtureProvenance,
    val description: String,
    val query: String,
    val currentTaskContext: String,
    val episodes: List<TaskEpisode>,
    val expectedTopEpisodeId: Long?,
    val expectedCautionContains: List<String> = emptyList(),
    val expectedCautionAbsent: List<String> = emptyList()
)

/**
 * The fixed, bounded fixture set.
 *
 * Eight fixtures, one per required scenario. Four are DEVELOPMENT and four are HELD_OUT. Every
 * fixture is SYNTHETIC.
 *
 * IMPORTANT: the expectations below record what the CURRENT production logic does, not what it
 * should do. Where the current behaviour is wrong, the fixture still records the current output and
 * the harness test marks it as a KNOWN LIMITATION, so that a later fix visibly changes the
 * assertion instead of silently passing.
 */
object ReplayFixtures {

    // --- Scenario 1: same entities, different roles -------------------------------------------

    /**
     * "file A" is the SOURCE in one episode and the TARGET in another. The query describes a copy
     * whose source is file A, so the role-appropriate episode (file A as source) must rank first.
     *
     * Current retrieval is lexical only: it has no notion of role, so this fixture passes only
     * because the role-appropriate episode happens to share more words with the query. That is a
     * coincidence of wording, not role understanding.
     */
    private val sameEntitiesDifferentRoles = ReplayFixture(
        id = "sameEntitiesDifferentRoles",
        family = FixtureFamily.DEVELOPMENT,
        provenance = FixtureProvenance.SYNTHETIC,
        description = "Same entity 'file A' appears as copy source in one episode and as copy target in another; the query asks for the source role.",
        query = "copy file A into the release directory",
        currentTaskContext = "copy file A into the release directory",
        episodes = listOf(
            TaskEpisode(
                id = 101L,
                taskSummary = "copy file A into the release directory",
                contextSummary = "file A was the source of the copy; the release directory was the target",
                outcome = TaskEpisode.OUTCOME_SUCCESS,
                resolutionSummary = "copied file A with the workspace copy tool",
                requiredPrerequisites = listOf("release directory exists"),
                evidenceKeys = listOf("copy:fileA:source"),
                createdAtMs = 1_000L
            ),
            TaskEpisode(
                id = 102L,
                taskSummary = "copy the build artifact into file A",
                contextSummary = "file A was the target of the copy; the build artifact was the source",
                outcome = TaskEpisode.OUTCOME_FAILURE,
                failureSummary = "file A was overwritten because it was the copy target",
                causeSummary = "the copy direction was reversed",
                resolutionSummary = "restored file A from the previous snapshot",
                requiredPrerequisites = listOf("file A is writable"),
                evidenceKeys = listOf("copy:fileA:target"),
                createdAtMs = 2_000L
            )
        ),
        expectedTopEpisodeId = 101L,
        // The role-appropriate episode's prerequisite is applicable (the context mentions both
        // "release" and "directory") but is not literally present in the context, so it is rendered
        // as a caution. This is the desired direction and it works today.
        expectedCautionContains = listOf("release directory exists")
    )

    // --- Scenario 2: negated prerequisite ------------------------------------------------------

    /**
     * One episode requires "must NOT be rooted" and another requires "device is rooted". The
     * present context says the device is rooted, so the negated prerequisite is VIOLATED and must
     * be surfaced as a caution.
     *
     * Current behaviour: the negated prerequisite IS rendered as a caution, but only by accident.
     * Applicability drops the stopword "not", so the condition reduces to "device rooted" and
     * matches the context; the substring satisfaction check then fails because the context does not
     * contain the literal phrase "device must not be rooted". The caution therefore appears for the
     * wrong reason, and the same mechanism would silently DROP the caution if the context happened
     * to contain the literal negated phrase (e.g. "the device must not be rooted" as a stated
     * requirement). Negation is not understood anywhere in the pipeline. KNOWN LIMITATION.
     */
    private val negatedPrerequisite = ReplayFixture(
        id = "negatedPrerequisite",
        family = FixtureFamily.HELD_OUT,
        provenance = FixtureProvenance.SYNTHETIC,
        description = "A negated prerequisite ('device must not be rooted') is compared with a positive one; negation must not count as satisfaction.",
        query = "install the debug build on the test device",
        currentTaskContext = "install the debug build on the test device; the device is rooted",
        episodes = listOf(
            TaskEpisode(
                id = 201L,
                taskSummary = "install the debug build on the test device",
                contextSummary = "the test device was rooted and the install was rejected",
                outcome = TaskEpisode.OUTCOME_FAILURE,
                failureSummary = "the installer refused to run on a rooted device",
                causeSummary = "the device was rooted",
                resolutionSummary = "used an unrooted device",
                requiredPrerequisites = listOf("device must not be rooted"),
                evidenceKeys = listOf("install:rooted-device"),
                createdAtMs = 3_000L
            ),
            TaskEpisode(
                id = 202L,
                taskSummary = "install the release build on the test device",
                contextSummary = "the test device was rooted and the install succeeded",
                outcome = TaskEpisode.OUTCOME_SUCCESS,
                resolutionSummary = "installed the release build",
                requiredPrerequisites = listOf("device is rooted"),
                evidenceKeys = listOf("install:rooted-device:release"),
                createdAtMs = 4_000L
            )
        ),
        expectedTopEpisodeId = 201L,
        // Current behaviour: the caution is rendered, but only because the literal negated phrase is
        // absent from the context. Negation itself is not understood. KNOWN LIMITATION.
        expectedCautionContains = listOf("device must not be rooted")
    )

    // --- Scenario 3: partial prerequisites -----------------------------------------------------

    /**
     * The episode requires three prerequisites; the request satisfies only one of them. The caution
     * should list the two missing ones.
     *
     * Current behaviour: applicability requires at least two distinct shared meaningful tokens, so
     * a prerequisite is only rendered when the present context already talks about it. The two
     * prerequisites the context does not mention are therefore dropped BEFORE the missing-check
     * runs, and the one the context does mention is treated as satisfied by substring containment.
     * The result is that NO caution is rendered at all, even though two prerequisites are missing.
     * This is a KNOWN LIMITATION recorded by the harness.
     */
    private val partialPrerequisites = ReplayFixture(
        id = "partialPrerequisites",
        family = FixtureFamily.DEVELOPMENT,
        provenance = FixtureProvenance.SYNTHETIC,
        description = "Episode requires three prerequisites; the request satisfies one; the caution should list the missing ones.",
        query = "publish the signed release apk to the internal channel",
        currentTaskContext = "publish the signed release apk to the internal channel; signing continuity is already configured",
        episodes = listOf(
            TaskEpisode(
                id = 301L,
                taskSummary = "publish the signed release apk to the internal channel",
                contextSummary = "the release apk was published after signing continuity, channel credentials and a version bump were confirmed",
                outcome = TaskEpisode.OUTCOME_PARTIAL,
                failureSummary = "the first publish attempt was rejected",
                causeSummary = "the version code had not been bumped",
                resolutionSummary = "bumped the version code and republished",
                requiredPrerequisites = listOf(
                    "signing continuity",
                    "channel credentials",
                    "version code bumped"
                ),
                evidenceKeys = listOf("publish:release-apk"),
                createdAtMs = 5_000L
            )
        ),
        expectedTopEpisodeId = 301L,
        // KNOWN LIMITATION: no caution line is rendered at all. "channel credentials" and
        // "version code bumped" are dropped by the applicability filter (the context never mentions
        // them), and "signing continuity" is treated as satisfied by substring containment.
        expectedCautionAbsent = listOf(
            "verify before relying on this:",
            "channel credentials",
            "version code bumped"
        )
    )

    // --- Scenario 4: stale observation ---------------------------------------------------------

    /**
     * The episode's context summary describes an observation that is stale relative to the present
     * task (the keystore was rotated since). Retrieval should still surface the episode, and the
     * caution should tell the model to verify.
     *
     * Current behaviour: retrieval has no notion of time or staleness, so the episode is surfaced
     * normally. The caution is NOT rendered, because the prerequisite "keystore alias" shares only
     * one meaningful token with the context and is therefore judged inapplicable. The episode is
     * surfaced but the model is told nothing about the stale prerequisite. KNOWN LIMITATION.
     */
    private val staleObservation = ReplayFixture(
        id = "staleObservation",
        family = FixtureFamily.HELD_OUT,
        provenance = FixtureProvenance.SYNTHETIC,
        description = "Episode context is stale relative to the present task; retrieval should still surface it and the caution should ask for verification.",
        query = "sign the release apk with the release keystore",
        currentTaskContext = "sign the release apk with the release keystore; the keystore was rotated last week",
        episodes = listOf(
            TaskEpisode(
                id = 401L,
                taskSummary = "sign the release apk with the release keystore",
                contextSummary = "the release keystore alias was observed to be release-old before the rotation",
                outcome = TaskEpisode.OUTCOME_FAILURE,
                failureSummary = "signing failed with an unknown alias",
                causeSummary = "the keystore alias had changed",
                resolutionSummary = "re-read the alias from the keystore before signing",
                requiredPrerequisites = listOf("keystore alias"),
                evidenceKeys = listOf("sign:keystore-alias"),
                createdAtMs = 6_000L
            )
        ),
        expectedTopEpisodeId = 401L,
        // KNOWN LIMITATION: the episode is surfaced, but no caution is rendered for the stale
        // prerequisite, and nothing in the pipeline knows the observation is stale. The phrase
        // "keystore alias" does appear in the block, but only inside the rendered cause line, never
        // as a caution, so the absence is asserted on the caution label.
        expectedCautionAbsent = listOf("verify before relying on this:")
    )

    // --- Scenario 5: executor mismatch ---------------------------------------------------------

    /**
     * The episode was recorded under a different executor (a shell executor) than the present one
     * (a workspace file executor). Current retrieval has NO executor scoping: the episode is
     * retrieved as if it had been produced by the present executor.
     *
     * This is a KNOWN LIMITATION. The fixture records the current behaviour (the mismatched episode
     * is still ranked first) so that adding executor scoping later visibly changes the assertion.
     */
    private val executorMismatch = ReplayFixture(
        id = "executorMismatch",
        family = FixtureFamily.DEVELOPMENT,
        provenance = FixtureProvenance.SYNTHETIC,
        description = "Episode recorded under a different executor; current retrieval has no executor scoping (known limitation).",
        query = "list the files in the workspace output directory",
        currentTaskContext = "list the files in the workspace output directory",
        episodes = listOf(
            TaskEpisode(
                id = 501L,
                taskSummary = "list the files in the workspace output directory",
                contextSummary = "recorded by the shell executor, which had no access to the workspace output directory",
                outcome = TaskEpisode.OUTCOME_FAILURE,
                failureSummary = "the shell executor could not see the workspace output directory",
                causeSummary = "the shell executor runs outside the workspace sandbox",
                resolutionSummary = "used the workspace file executor instead",
                requiredPrerequisites = listOf("workspace file executor"),
                evidenceKeys = listOf("list:output-dir:shell-executor"),
                createdAtMs = 7_000L
            )
        ),
        // KNOWN LIMITATION: no executor scoping, so the shell-executor episode is retrieved for a
        // workspace-file-executor task.
        expectedTopEpisodeId = 501L
    )

    // --- Scenario 6: scope mismatch ------------------------------------------------------------

    /**
     * The episode belongs to a different project scope (project "alpha") than the present task
     * (project "beta"). Current retrieval has NO scope scoping: the episode is retrieved as if it
     * belonged to the present scope.
     *
     * This is a KNOWN LIMITATION. The fixture records the current behaviour so that adding scope
     * scoping later visibly changes the assertion.
     */
    private val scopeMismatch = ReplayFixture(
        id = "scopeMismatch",
        family = FixtureFamily.HELD_OUT,
        provenance = FixtureProvenance.SYNTHETIC,
        description = "Episode from a different project scope; current retrieval has no scope scoping (known limitation).",
        query = "run the unit tests for the billing module",
        currentTaskContext = "run the unit tests for the billing module in project beta",
        episodes = listOf(
            TaskEpisode(
                id = 601L,
                taskSummary = "run the unit tests for the billing module",
                contextSummary = "recorded in project alpha, where the billing module had a separate gradle task",
                outcome = TaskEpisode.OUTCOME_SUCCESS,
                resolutionSummary = "ran the alpha billing test task",
                requiredPrerequisites = listOf("billing test task"),
                evidenceKeys = listOf("test:billing:project-alpha"),
                createdAtMs = 8_000L
            )
        ),
        // KNOWN LIMITATION: no scope scoping, so a project-alpha episode is retrieved for a
        // project-beta task.
        expectedTopEpisodeId = 601L
    )

    // --- Scenario 7: analogous structure, different vocabulary ---------------------------------

    /**
     * The past episode and the present task have the SAME structure (install a package on a device)
     * but use different words ("install apk" vs "deploy package"). The only shared meaningful token
     * is "package", which appears in the episode only inside the incidental phrase "package
     * installer" — it is not the object being installed.
     *
     * Behaviour since the lexical threshold (2026-10-30): the episode is NOT retrieved lexically. One
     * incidental shared token (1 of 4 meaningful query tokens) is below MIN_LEXICAL_SHARED_TOKENS, so
     * the coincidental match that used to retrieve it is gone. Lexical retrieval still has no
     * structural notion, so the real structural match is not found lexically either: that is left to
     * the vector generator. KNOWN LIMITATION (lexical side) and the motivation for structural matching.
     */
    private val analogousStructureDifferentVocabulary = ReplayFixture(
        id = "analogousStructureDifferentVocabulary",
        family = FixtureFamily.DEVELOPMENT,
        provenance = FixtureProvenance.SYNTHETIC,
        description = "Same task structure with different vocabulary; lexical retrieval matches only on an incidental shared word (known limitation, motivates later structural matching).",
        query = "deploy the package onto the connected handset",
        currentTaskContext = "deploy the package onto the connected handset",
        episodes = listOf(
            TaskEpisode(
                id = 701L,
                taskSummary = "install the apk on the emulator",
                contextSummary = "the apk was installed on a running emulator after the device was unlocked",
                outcome = TaskEpisode.OUTCOME_SUCCESS,
                resolutionSummary = "installed the apk with the package installer",
                requiredPrerequisites = listOf("device unlocked"),
                evidenceKeys = listOf("install:apk:emulator"),
                createdAtMs = 9_000L
            )
        ),
        // The incidental single-token hit ("package" from "package installer") no longer qualifies,
        // so lexical retrieval returns nothing for this fixture. The dedicated limitation test
        // asserts that, and that the same episode is still retrieved when the vocabulary matches.
        expectedTopEpisodeId = null
    )

    // --- Scenario 8: substring satisfaction ----------------------------------------------------

    /**
     * The prerequisite "root" is a substring of the unrelated word "rootedness" in the request, so
     * the current substring-based satisfaction check marks it satisfied even though the request
     * says nothing about root access.
     *
     * This is a KNOWN LIMITATION. The fixture records the current behaviour (the caution is
     * omitted) so that a token-boundary fix later visibly changes the assertion.
     */
    private val substringSatisfaction = ReplayFixture(
        id = "substringSatisfaction",
        family = FixtureFamily.HELD_OUT,
        provenance = FixtureProvenance.SYNTHETIC,
        description = "A prerequisite whose text is a substring of an unrelated word in the request; current substring satisfaction falsely marks it satisfied (known limitation).",
        query = "check the rootedness of the build tree before packaging",
        currentTaskContext = "check the rootedness of the build tree before packaging; root access is required for the packaging step",
        episodes = listOf(
            TaskEpisode(
                id = 801L,
                taskSummary = "check the rootedness of the build tree before packaging",
                contextSummary = "packaging failed because root access had not been granted",
                outcome = TaskEpisode.OUTCOME_FAILURE,
                failureSummary = "the packaging step could not write to the system partition",
                causeSummary = "root access was not granted",
                resolutionSummary = "granted root access and repackaged",
                requiredPrerequisites = listOf("root"),
                evidenceKeys = listOf("package:root-access"),
                createdAtMs = 10_000L
            )
        ),
        expectedTopEpisodeId = 801L,
        // KNOWN LIMITATION: "root" occurs inside "rootedness", so the prerequisite is treated as
        // satisfied and the caution is omitted.
        expectedCautionAbsent = listOf("verify before relying on this: root")
    )

    /** The fixed, bounded fixture set. Order is stable and is part of the harness contract. */
    val all: List<ReplayFixture> = listOf(
        sameEntitiesDifferentRoles,
        negatedPrerequisite,
        partialPrerequisites,
        staleObservation,
        executorMismatch,
        scopeMismatch,
        analogousStructureDifferentVocabulary,
        substringSatisfaction
    )

    /** Fixtures in the DEVELOPMENT family. */
    val development: List<ReplayFixture> = all.filter { it.family == FixtureFamily.DEVELOPMENT }

    /** Fixtures in the HELD_OUT family. */
    val heldOut: List<ReplayFixture> = all.filter { it.family == FixtureFamily.HELD_OUT }
}
