package com.ai.assistance.operit.data.learning

/**
 * Deterministic derivation of [ReviewTriggerPolicy.ReviewSignals] from recorded turn events.
 *
 * Every signal is computed from events that were actually recorded during the turn — tool
 * results, model hops, and conversation turns — never from model self-judgement. This is the
 * A02 fix for finding R02: the signals were previously hardcoded to false, so no review was
 * ever enqueued.
 *
 * R14 separation (docs/TODO/learning-pipeline-hardening-20261017/01-findings.md): the raw observed
 * event and its interpreted meaning are kept apart. [ReviewTriggerPolicy.ReviewSignals.observed]
 * carries the recorded counts, the matched lexical marker and the explicit-action flag verbatim;
 * the boolean fields of [ReviewTriggerPolicy.ReviewSignals] are the meaning assigned to them. A
 * reader can therefore tell what was observed from what was inferred.
 *
 * Signal-to-event mapping (implemented behaviour, documented in 01-findings.md under R02 and R14):
 *  - explicitUserCorrection: set ONLY by [TurnObservations.explicitCorrectionAction], i.e. a
 *    deliberate user action marking the reply wrong. A lexical marker match must NEVER set it.
 *    NOTE: nothing in production sets [TurnObservations.explicitCorrectionAction] yet, because the
 *    app has no explicit-correction UI control (searches for markWrong / markAsWrong /
 *    userCorrection / explicitCorrection / reportIncorrect / flagWrong / thumbsDown / dislike /
 *    isWrong return zero hits; the only user-initiated re-run action is `regenerate`, which is a
 *    RETRY, not a correction marker). Wiring such a control is a recorded follow-up.
 *  - candidateCorrection: the turn's last user message carries a [CORRECTION_MARKERS] marker at a
 *    word boundary, and an assistant turn precedes it in the same turn's history. This is a weaker
 *    candidate signal: a lexical marker is evidence the user pushed back, not proof they marked
 *    the reply wrong.
 *  - taskFailed: at least one tool result in the turn reported success = false, or at least one
 *    truncated/unclosed tool round was invalidated during the turn.
 *  - repeatedRepairCount: the number of failed tool results in the turn; each failed tool result
 *    is one repair attempt.
 *  - complexSuccessfulTurn: the turn had no failed tool results and no invalidated tool round, and
 *    it exceeded [COMPLEX_TURN_HOP_THRESHOLD] model hops or [COMPLEX_TURN_TOOL_THRESHOLD] tool
 *    results. R14 renamed this signal: the derivation measures complexity and success only, and
 *    never checks for a lesson or any lesson-shaped content, so the name must not claim one.
 *
 * [ReviewTriggerPolicy] remains the single decision point; this class only produces signals.
 */
object ReviewSignalDeriver {

    /** Model hops at or above which a successful turn counts as complex. */
    const val COMPLEX_TURN_HOP_THRESHOLD = 3

    /** Tool results at or above which a successful turn counts as complex. */
    const val COMPLEX_TURN_TOOL_THRESHOLD = 3

    /**
     * Deterministic lexical correction markers. A user turn is treated as a candidate correction
     * when its trimmed, lower-cased content matches one of these markers at a word boundary (see
     * [containsMarkerAtWordBoundary]). This is a recorded-text detector, not a model judgement.
     *
     * R14 changes to the list:
     *  - the bare marker "wrong" was REMOVED. It matched inside unrelated words ("wrongly") and
     *    inside unrelated topics ("nothing wrong with the build"), so it produced candidate
     *    corrections for turns where the user was not correcting anything. It is replaced by the
     *    more specific phrases "that's wrong", "that is wrong", "is wrong" and "was wrong".
     *  - "no," is kept, but it is boundary-matched, so it only matches as a standalone token and
     *    not inside another word.
     *
     * R14b changes to the list:
     *  - the neutral retry phrases "redo", "try again" and the Chinese "重新" were REMOVED. They
     *    are retry requests, not corrections: a retry is a UI action (regenerate), not a statement
     *    that the previous reply was wrong, so treating them as correction markers produced
     *    candidate corrections for turns where the user only asked for the same work again.
     *  - the genuine corrective phrases are kept, including the Chinese 不对 (not right), 错了
     *    (wrong), 不是这样 (not like this) and 改一下 (change it a bit). A mixed turn such as
     *    "that's wrong, try again" still matches on its corrective part.
     */
    val CORRECTION_MARKERS: List<String> = listOf(
        "no,",
        "that's wrong",
        "that is wrong",
        "is wrong",
        "was wrong",
        "incorrect",
        "not what i asked",
        "actually,",
        "instead,",
        "you misunderstood",
        "fix that",
        "不对",
        "错了",
        "不是这样",
        "改一下"
    )

    /** Deterministic per-turn facts recorded while the turn ran. */
    data class TurnObservations(
        val toolResultCount: Int = 0,
        val failedToolResultCount: Int = 0,
        val truncatedToolRecoveryCount: Int = 0,
        val modelHopCount: Int = 0,
        val userTurnCount: Int = 0,
        val lastUserTurnContent: String = "",
        val previousAssistantTurnContent: String = "",
        /**
         * R14: the explicit user-initiated correction input. True only when the user deliberately
         * marked the reply wrong through a UI action. Nothing in production sets this yet — no such
         * control exists — so it stays false and only [candidateCorrection] can fire from prose.
         */
        val explicitCorrectionAction: Boolean = false
    )

    /** Mutable accumulator written during the turn, converted to [TurnObservations] at the end. */
    class TurnObservationAccumulator {
        var toolResultCount: Int = 0
        var failedToolResultCount: Int = 0
        var truncatedToolRecoveryCount: Int = 0

        /**
         * R14: the explicit user-initiated correction input, threaded through to
         * [TurnObservations.explicitCorrectionAction]. Defaults to false so existing call sites
         * compile unchanged; no production path sets it yet (recorded follow-up).
         */
        var explicitCorrectionAction: Boolean = false

        fun toObservations(
            modelHopCount: Int,
            userTurnCount: Int,
            lastUserTurnContent: String,
            previousAssistantTurnContent: String,
            explicitCorrectionAction: Boolean = false
        ): TurnObservations = TurnObservations(
            toolResultCount = toolResultCount,
            failedToolResultCount = failedToolResultCount,
            truncatedToolRecoveryCount = truncatedToolRecoveryCount,
            modelHopCount = modelHopCount,
            userTurnCount = userTurnCount,
            lastUserTurnContent = lastUserTurnContent,
            previousAssistantTurnContent = previousAssistantTurnContent,
            explicitCorrectionAction = explicitCorrectionAction
        )
    }

    fun derive(observations: TurnObservations): ReviewTriggerPolicy.ReviewSignals {
        val taskFailed =
            observations.failedToolResultCount > 0 || observations.truncatedToolRecoveryCount > 0
        val turnSucceeded = !taskFailed
        val complexSuccessfulTurn =
            turnSucceeded &&
                (observations.modelHopCount >= COMPLEX_TURN_HOP_THRESHOLD ||
                    observations.toolResultCount >= COMPLEX_TURN_TOOL_THRESHOLD)
        val matchedMarker = firstMatchingMarker(observations)
        val candidateCorrection = isCandidateCorrectionTurn(observations)
        return ReviewTriggerPolicy.ReviewSignals(
            observed =
                ReviewTriggerPolicy.ObservedTurnEvents(
                    failedToolResultCount = observations.failedToolResultCount,
                    truncatedToolRecoveryCount = observations.truncatedToolRecoveryCount,
                    toolResultCount = observations.toolResultCount,
                    modelHopCount = observations.modelHopCount,
                    userTurnCount = observations.userTurnCount,
                    matchedCorrectionMarker = matchedMarker,
                    explicitCorrectionAction = observations.explicitCorrectionAction
                ),
            explicitUserCorrection = observations.explicitCorrectionAction,
            candidateCorrection = candidateCorrection,
            taskFailed = taskFailed,
            repeatedRepairCount = observations.failedToolResultCount,
            complexSuccessfulTurn = complexSuccessfulTurn
        )
    }

    /**
     * A turn is a CANDIDATE correction turn when an assistant turn precedes the user turn in the
     * same turn's history and the user content carries a [CORRECTION_MARKERS] marker at a word
     * boundary. R14 renamed this predicate: a lexical match is a candidate, not an explicit
     * correction.
     */
    fun isCandidateCorrectionTurn(observations: TurnObservations): Boolean {
        if (observations.previousAssistantTurnContent.isBlank()) return false
        return firstMatchingMarker(observations).isNotEmpty()
    }

    /**
     * The first [CORRECTION_MARKERS] entry that matches the recorded user turn at a word boundary,
     * or "" when none matched. R14: the matched marker is retained as an observed event so the
     * review enqueue can pass what was actually seen, not only the derived boolean.
     */
    fun firstMatchingMarker(observations: TurnObservations): String {
        val normalized = observations.lastUserTurnContent.trim().lowercase()
        if (normalized.isEmpty()) return ""
        return CORRECTION_MARKERS.firstOrNull { containsMarkerAtWordBoundary(normalized, it) }
            ?: ""
    }

    /**
     * Word-boundary marker matching.
     *
     * For ASCII markers a boundary assertion is applied: the marker must not be preceded or
     * followed by a letter or digit, so "wrong" cannot match inside "wrongly" and "redo" cannot
     * match inside "redone".
     *
     * For markers containing non-ASCII characters (the CJK markers 不对, 错了, 不是这样, 改一下)
     * a plain `contains` on the normalized text is used instead. This is NOT a degraded path: CJK
     * text has no word separators, so a `\b`-style boundary assertion would never match and would
     * silently disable every CJK marker. Plain containment is the correct matching rule for a
     * script without spaces.
     */
    private fun containsMarkerAtWordBoundary(normalized: String, marker: String): Boolean {
        val isAsciiMarker = marker.all { it.code < 128 }
        if (!isAsciiMarker) {
            // Correct rule for CJK: no word separators exist, so boundaries cannot be asserted.
            return normalized.contains(marker)
        }
        val pattern = Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(marker) + "(?![\\p{L}\\p{N}])")
        return pattern.containsMatchIn(normalized)
    }
}
