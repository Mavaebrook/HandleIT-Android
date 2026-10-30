package com.ai.assistance.operit.data.learning

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for review evidence capture (docs/TODO/review-evidence-capture-20261030/README.md).
 * Pure: no ObjectBox, no model call.
 */
class ReviewEvidenceTest {

    private fun observations(
        lastUser: String = "that's wrong, the key is in keystore.jks",
        previousAssistant: String = "Sign it with the debug key.",
        failed: Int = 0,
        total: Int = 0
    ) = ReviewSignalDeriver.TurnObservations(
        toolResultCount = total,
        failedToolResultCount = failed,
        truncatedToolRecoveryCount = 0,
        modelHopCount = 2,
        userTurnCount = 2,
        lastUserTurnContent = lastUser,
        previousAssistantTurnContent = previousAssistant
    )

    @Test
    fun capture_keepsMistakenReplyCorrectionAndFailedToolText() {
        val evidence = ReviewEvidence.capture(
            observations = observations(failed = 1, total = 2),
            matchedCorrectionMarker = "that's wrong",
            finalReply = "Signed with keystore.jks.",
            failedToolResults = listOf(FailedToolRecord("execute_shell", "exit 1", "apksigner: bad key")),
            invalidatedToolNames = listOf("write_file", "write_file")
        )

        assertEquals("Sign it with the debug key.", evidence.previousAssistantReply)
        assertEquals("that's wrong, the key is in keystore.jks", evidence.userRequest)
        assertEquals("Signed with keystore.jks.", evidence.finalReply)
        assertEquals("that's wrong", evidence.matchedCorrectionMarker)
        assertEquals(listOf(FailedToolRecord("execute_shell", "exit 1", "apksigner: bad key")), evidence.failedToolResults)
        assertEquals(listOf("write_file"), evidence.invalidatedToolNames)
        assertEquals(1, evidence.failedToolResultCount)
        assertEquals(2, evidence.toolResultCount)
        assertTrue(evidence.truncatedFields.isEmpty())
    }

    @Test
    fun capture_recordsEveryBoundThatCutSomething() {
        val longReply = "x".repeat(ReviewEvidence.MAX_TEXT_CHARS + 50)
        val manyTools = (1..ReviewEvidence.MAX_FAILED_TOOL_RECORDS + 3).map {
            FailedToolRecord("tool$it", "e".repeat(ReviewEvidence.MAX_TOOL_TEXT_CHARS + 1), "r")
        }
        val evidence = ReviewEvidence.capture(
            observations = observations(failed = manyTools.size, total = manyTools.size),
            matchedCorrectionMarker = "",
            finalReply = longReply,
            failedToolResults = manyTools,
            invalidatedToolNames = emptyList()
        )

        assertEquals(ReviewEvidence.MAX_TEXT_CHARS, evidence.finalReply.length)
        assertEquals(ReviewEvidence.MAX_FAILED_TOOL_RECORDS, evidence.failedToolResults.size)
        // The exact count is kept even though the records are bounded.
        assertEquals(manyTools.size, evidence.failedToolResultCount)
        assertTrue(evidence.truncatedFields.any { it.startsWith("final_reply") })
        assertTrue(evidence.truncatedFields.any { it.startsWith("failed_tool_results (kept first") })
        assertTrue(evidence.truncatedFields.any { it.startsWith("failed_tool_results[0].error") })
        assertFalse(evidence.truncatedFields.any { it.startsWith("user_request") })
    }

    @Test
    fun capture_doesNotLeaveOrphanedHighSurrogateAtCut() {
        val text = "a".repeat(ReviewEvidence.MAX_TEXT_CHARS - 1) + "😀" + "tail"
        val evidence = ReviewEvidence.capture(
            observations = observations(lastUser = text),
            matchedCorrectionMarker = "",
            finalReply = "",
            failedToolResults = emptyList(),
            invalidatedToolNames = emptyList()
        )
        assertFalse(evidence.userRequest.last().isHighSurrogate())
    }

    @Test
    fun encodeDecode_roundTripsExactly() {
        val evidence = ReviewEvidence.capture(
            observations = observations(failed = 1, total = 1),
            matchedCorrectionMarker = "that's wrong",
            finalReply = "multi\nline \"quoted\" 不对",
            failedToolResults = listOf(FailedToolRecord("t", "", "")),
            invalidatedToolNames = emptyList()
        )
        assertEquals(evidence, ReviewEvidence.decode(ReviewEvidence.encode(evidence)))
    }

    @Test
    fun decode_rejectsCorruptAndUnsupportedValues() {
        assertThrows(Exception::class.java) { ReviewEvidence.decode("{not json") }
        assertThrows(IllegalArgumentException::class.java) { ReviewEvidence.decode("""{"v":2}""") }
    }

    @Test
    fun reflectorInput_capturedRow_isLabeledWithTriggerOutcomeAndEvidence() {
        val evidence = ReviewEvidence.capture(
            observations = observations(failed = 1, total = 1),
            matchedCorrectionMarker = "that's wrong",
            finalReply = "fixed",
            failedToolResults = listOf(FailedToolRecord("execute_shell", "exit 1", "")),
            invalidatedToolNames = emptyList()
        )
        val built = ReflectorInput.build(
            triggerReason = "task failure",
            observedOutcome = "failure",
            evidenceJson = ReviewEvidence.encode(evidence),
            legacyFinalReply = "ignored legacy column",
            legacyUserRequest = "ignored legacy column"
        )
        val root = Json.parseToJsonElement(built).jsonObject
        assertEquals(ReflectorInput.STATUS_CAPTURED, root["evidence_status"]!!.jsonPrimitive.content)
        assertEquals("task failure", root["trigger_reason"]!!.jsonPrimitive.content)
        assertEquals("failure", root["observed_outcome"]!!.jsonPrimitive.content)
        val ev = root["evidence"]!!.jsonObject
        assertEquals("Sign it with the debug key.", ev["previous_assistant_reply"]!!.jsonPrimitive.content)
        assertEquals("fixed", ev["final_reply"]!!.jsonPrimitive.content)
        assertEquals(1, ev["failed_tool_result_count"]!!.jsonPrimitive.int)
        assertEquals(
            "execute_shell",
            ev["failed_tool_results"]!!.jsonArray[0].jsonObject["tool_name"]!!.jsonPrimitive.content
        )
        assertFalse(ev["explicit_correction_action"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun reflectorInput_rowQueuedBeforeCapture_saysSoAndMapsLegacyColumns() {
        val built = ReflectorInput.build(
            triggerReason = "candidate correction (lexical marker, unconfirmed)",
            observedOutcome = "success",
            evidenceJson = "",
            legacyFinalReply = "final reply text",
            legacyUserRequest = "user message text"
        )
        val root = Json.parseToJsonElement(built).jsonObject
        assertEquals(ReflectorInput.STATUS_QUEUED_BEFORE_CAPTURE, root["evidence_status"]!!.jsonPrimitive.content)
        val ev = root["evidence"]!!.jsonObject
        assertEquals("final reply text", ev["final_reply"]!!.jsonPrimitive.content)
        assertEquals("user message text", ev["user_request"]!!.jsonPrimitive.content)
        assertEquals("", ev["previous_assistant_reply"]!!.jsonPrimitive.content)
    }

    @Test
    fun reflectorInput_corruptStoredEvidence_throwsInsteadOfReadingEmpty() {
        assertThrows(Exception::class.java) {
            ReflectorInput.build("task failure", "failure", "{broken", "f", "u")
        }
    }
}
