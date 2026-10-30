package com.ai.assistance.operit.data.learning

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * One failed tool result recorded during a turn, kept for the post-task review.
 *
 * [error] is `ToolResult.error` and [result] is the tool's own result text. Both are recorded as
 * they were returned; neither is substituted for the other, because some tools report the failure
 * reason in the error field and others in the result body.
 */
@Serializable
data class FailedToolRecord(
    @SerialName("tool_name") val toolName: String,
    @SerialName("error") val error: String = "",
    @SerialName("result") val result: String = ""
)

/**
 * Raw evidence of a completed turn, captured at enqueue time and persisted with the pending review.
 *
 * WHY THIS EXISTS (docs/TODO/review-evidence-capture-20261030/README.md): reviews are processed
 * later, in batches, by [com.ai.assistance.operit.api.chat.library.ReviewScheduler]. Before this
 * change the Reflector received only the final reply and the last user message joined by a
 * newline. It never saw the reply the user corrected, the failed tools or their error text, or
 * why the review was triggered, so `failure_summary` and `cause_summary` were reconstructed from
 * the already-fixed answer. Everything the Reflector needs to name the mistake must therefore be
 * captured while the turn is still in memory; it cannot be recovered at drain time.
 *
 * Text fields are bounded. Every bound that actually cut something is recorded in
 * [truncatedFields], so a truncated value is never presented as complete.
 */
@Serializable
data class ReviewEvidence(
    @SerialName("v") val version: Int = VERSION,
    /** The latest user message of the turn (for a correction turn, the correction itself). */
    @SerialName("user_request") val userRequest: String = "",
    /** The assistant reply the latest user message responded to: the mistaken reply on a correction. */
    @SerialName("previous_assistant_reply") val previousAssistantReply: String = "",
    /** The assistant reply that ended this turn. */
    @SerialName("final_reply") val finalReply: String = "",
    /** The lexical correction marker that matched the user request, or "" when none matched. */
    @SerialName("matched_correction_marker") val matchedCorrectionMarker: String = "",
    /** True only when the user marked the reply wrong through an explicit UI action. */
    @SerialName("explicit_correction_action") val explicitCorrectionAction: Boolean = false,
    @SerialName("failed_tool_results") val failedToolResults: List<FailedToolRecord> = emptyList(),
    @SerialName("invalidated_tool_names") val invalidatedToolNames: List<String> = emptyList(),
    @SerialName("tool_result_count") val toolResultCount: Int = 0,
    @SerialName("failed_tool_result_count") val failedToolResultCount: Int = 0,
    @SerialName("truncated_tool_recovery_count") val truncatedToolRecoveryCount: Int = 0,
    @SerialName("model_hop_count") val modelHopCount: Int = 0,
    /** Names of the fields a bound shortened, with what was kept. Empty when nothing was cut. */
    @SerialName("truncated_fields") val truncatedFields: List<String> = emptyList()
) {
    companion object {
        const val VERSION = 1

        /** Bound for each of the three conversation texts. */
        const val MAX_TEXT_CHARS = 4000

        /** Bound for each failed tool's error text and result text. */
        const val MAX_TOOL_TEXT_CHARS = 1000

        /** At most this many failed tool records are kept; the exact count stays in [failedToolResultCount]. */
        const val MAX_FAILED_TOOL_RECORDS = 10

        private val json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = false
        }

        /**
         * Build bounded evidence from the turn's recorded facts.
         *
         * [failedToolResults] and [invalidatedToolNames] come from the turn's
         * [ReviewSignalDeriver.TurnObservationAccumulator]; [observations] carries the conversation
         * texts and counts already used for the review signals.
         */
        fun capture(
            observations: ReviewSignalDeriver.TurnObservations,
            matchedCorrectionMarker: String,
            finalReply: String,
            failedToolResults: List<FailedToolRecord>,
            invalidatedToolNames: List<String>
        ): ReviewEvidence {
            val truncated = mutableListOf<String>()
            fun bound(field: String, value: String, max: Int): String {
                if (value.length <= max) return value
                truncated.add("$field (kept first $max of ${value.length} chars)")
                // Do not keep an orphaned high surrogate at the cut.
                val cut = value.take(max)
                return if (cut.lastOrNull()?.isHighSurrogate() == true) cut.dropLast(1) else cut
            }

            val keptTools = failedToolResults.take(MAX_FAILED_TOOL_RECORDS)
            if (failedToolResults.size > keptTools.size) {
                truncated.add(
                    "failed_tool_results (kept first ${keptTools.size} of ${failedToolResults.size} records)"
                )
            }
            val boundedTools = keptTools.mapIndexed { index, record ->
                FailedToolRecord(
                    toolName = record.toolName,
                    error = bound("failed_tool_results[$index].error", record.error, MAX_TOOL_TEXT_CHARS),
                    result = bound("failed_tool_results[$index].result", record.result, MAX_TOOL_TEXT_CHARS)
                )
            }

            return ReviewEvidence(
                userRequest = bound("user_request", observations.lastUserTurnContent, MAX_TEXT_CHARS),
                previousAssistantReply =
                    bound("previous_assistant_reply", observations.previousAssistantTurnContent, MAX_TEXT_CHARS),
                finalReply = bound("final_reply", finalReply, MAX_TEXT_CHARS),
                matchedCorrectionMarker = matchedCorrectionMarker,
                explicitCorrectionAction = observations.explicitCorrectionAction,
                failedToolResults = boundedTools,
                invalidatedToolNames = invalidatedToolNames.distinct(),
                toolResultCount = observations.toolResultCount,
                failedToolResultCount = observations.failedToolResultCount,
                truncatedToolRecoveryCount = observations.truncatedToolRecoveryCount,
                modelHopCount = observations.modelHopCount,
                truncatedFields = truncated
            )
        }

        fun encode(evidence: ReviewEvidence): String = json.encodeToString(evidence)

        /**
         * Decode a stored evidence value. Throws when the value is not valid version-[VERSION]
         * evidence: a stored row that cannot be read must surface as a failed review (and
         * eventually a dead-lettered, inspectable row), not be silently treated as empty.
         */
        fun decode(stored: String): ReviewEvidence {
            val evidence = json.decodeFromString<ReviewEvidence>(stored)
            require(evidence.version == VERSION) {
                "Unsupported review evidence version ${evidence.version}"
            }
            return evidence
        }
    }
}

/**
 * The labeled input the Reflector receives for one pending review.
 *
 * [evidenceStatus] states what kind of row the input was built from:
 *  - [STATUS_CAPTURED]: the row carries [ReviewEvidence] captured at enqueue time;
 *  - [STATUS_QUEUED_BEFORE_CAPTURE]: the row was queued by a build without evidence capture. Its
 *    two legacy columns are reported under the names of what they actually held (the final reply
 *    and the latest user message), and every evidence field that was never recorded is empty. The
 *    status tells the Reflector that the absence of failed tools or a previous reply means "not
 *    recorded", not "did not happen".
 */
@Serializable
data class ReflectorInput(
    @SerialName("input_version") val inputVersion: Int = 1,
    @SerialName("evidence_status") val evidenceStatus: String,
    @SerialName("trigger_reason") val triggerReason: String,
    @SerialName("observed_outcome") val observedOutcome: String,
    @SerialName("evidence") val evidence: ReviewEvidence
) {
    companion object {
        const val STATUS_CAPTURED = "captured"
        const val STATUS_QUEUED_BEFORE_CAPTURE = "queued_before_evidence_capture"

        private val json = Json {
            encodeDefaults = true
            prettyPrint = true
        }

        /**
         * Build the Reflector input for a pending review row.
         *
         * @param evidenceJson the row's stored evidence; blank only for rows queued before capture.
         * @param legacyFinalReply the row's `taskSummary` column (the final reply).
         * @param legacyUserRequest the row's `contextSummary` column (the latest user message).
         */
        fun build(
            triggerReason: String,
            observedOutcome: String,
            evidenceJson: String,
            legacyFinalReply: String,
            legacyUserRequest: String
        ): String {
            val input = if (evidenceJson.isNotBlank()) {
                ReflectorInput(
                    evidenceStatus = STATUS_CAPTURED,
                    triggerReason = triggerReason,
                    observedOutcome = observedOutcome,
                    evidence = ReviewEvidence.decode(evidenceJson)
                )
            } else {
                ReflectorInput(
                    evidenceStatus = STATUS_QUEUED_BEFORE_CAPTURE,
                    triggerReason = triggerReason,
                    observedOutcome = observedOutcome,
                    evidence = ReviewEvidence(
                        userRequest = legacyUserRequest,
                        finalReply = legacyFinalReply
                    )
                )
            }
            return json.encodeToString(input)
        }
    }
}
