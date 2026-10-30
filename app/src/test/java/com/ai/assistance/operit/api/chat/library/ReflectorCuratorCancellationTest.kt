package com.ai.assistance.operit.api.chat.library

import com.ai.assistance.operit.data.learning.SkillRevision
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.TaskEpisode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Tests the same role paths used by the public Context-based entry points, with a fake provider. */
@RunWith(Parameterized::class)
class ReflectorCuratorCancellationTest(private val reflector: Boolean) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "reflector={0}")
        fun roles(): List<Array<Boolean>> = listOf(arrayOf(true), arrayOf(false))
    }

    @Test
    fun providerCancellation_propagatesSameExceptionWithoutReportingFailure() = runTest {
        val cancellation = CancellationException("review stopped")
        val reported = mutableListOf<Throwable>()
        var returnedNormally = false
        var propagated: Throwable? = null

        try {
            invokeRole({ _, _ -> throw cancellation }, reported::add)
            returnedNormally = true
        } catch (error: CancellationException) {
            propagated = error
        }

        assertSame(cancellation, propagated)
        assertFalse(returnedNormally)
        assertTrue(reported.isEmpty())
    }

    @Test
    fun cancellationWhileProviderSuspends_doesNotReturnAnEmptyResult() = runTest {
        val reported = mutableListOf<Throwable>()
        var providerStarted = false
        var returnedNormally = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            invokeRole(
                { _, _ ->
                    providerStarted = true
                    awaitCancellation()
                },
                reported::add
            )
            // Returning null here would let the scheduler record an ordinary review failure.
            returnedNormally = true
        }

        assertTrue(providerStarted)
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertFalse(returnedNormally)
        assertTrue(reported.isEmpty())
    }

    @Test
    fun ordinaryProviderFailure_returnsNullAndReportsOnce() = runTest {
        val failure = IllegalStateException("provider unavailable")
        val reported = mutableListOf<Throwable>()

        val result = invokeRole({ _, _ -> throw failure }, reported::add)

        assertNull(result)
        assertEquals(listOf(failure), reported)
    }

    @Test
    fun successfulProviderResponse_keepsRoleAndParsingBehavior() = runTest {
        val reported = mutableListOf<Throwable>()
        var calls = 0
        val result = invokeRole(
            { type, prompt ->
                calls += 1
                if (reflector) {
                    assertEquals(FunctionType.MEMORY, type)
                    assertTrue(prompt.endsWith("Turn evidence (JSON):\nepisode input"))
                    """<think>private reasoning</think>{"task_summary":"lesson","outcome":"success"}"""
                } else {
                    assertEquals(FunctionType.SUMMARY, type)
                    assertTrue(prompt.contains("Skill id: skill-id"))
                    assertTrue(prompt.contains("Existing skill content:\nexisting content"))
                    assertTrue(prompt.endsWith("Lesson:\nlesson input"))
                    """<think>private reasoning</think>{"scope":"localized change","steps":["step"]}"""
                }
            },
            reported::add
        )

        if (reflector) {
            assertEquals("lesson", (result as TaskEpisode).taskSummary)
            assertEquals(TaskEpisode.OUTCOME_SUCCESS, result.outcome)
        } else {
            assertEquals("skill-id", (result as SkillRevision).skillId)
            assertEquals(SkillRevision.STATUS_DRAFT, result.status)
            assertEquals(listOf("step"), result.steps)
        }
        assertEquals(1, calls)
        assertTrue(reported.isEmpty())
    }

    @Test
    fun malformedProviderOutput_stillReturnsNull() = runTest {
        val reported = mutableListOf<Throwable>()

        assertNull(invokeRole({ _, _ -> "not a JSON response" }, reported::add))
        assertTrue(reported.isEmpty())
    }

    private suspend fun invokeRole(
        callModel: suspend (FunctionType, String) -> String,
        onFailure: (Throwable) -> Unit
    ): Any? = if (reflector) {
        ReflectorCurator.reflect("episode input", true, callModel, onFailure)
    } else {
        ReflectorCurator.curate("skill-id", "lesson input", "existing content", true, callModel, onFailure)
    }
}
