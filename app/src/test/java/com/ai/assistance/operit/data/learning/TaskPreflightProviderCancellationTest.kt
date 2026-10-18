package com.ai.assistance.operit.data.learning

import com.ai.assistance.operit.data.model.TaskEpisode
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * FIX 2 regression tests: [TaskPreflightProvider.buildPreflightBlockFrom] must NOT swallow
 * [CancellationException], while a genuine retrieval failure must still yield the documented ""
 * no-block result.
 *
 * Retrieval is injected through the `retrieve` lambda seam, so these tests run on a plain JVM: no
 * ObjectBox store, no Android Context, no Robolectric.
 *
 * LOGGING IS ASSERTED INDIRECTLY, NOT BY MOCKING. `AppLogger` is a Kotlin `object` with
 * `@JvmStatic` methods and no injectable sink, so observing its calls would require static mocking
 * (an API whose exact overloads are not worth depending on here). Instead these tests assert the
 * two directly observable outcomes that fully determine the logging behaviour of
 * `buildPreflightBlockFrom`, whose body is:
 *
 * ```
 * try { ... } catch (cancellation: CancellationException) { throw cancellation }
 *             catch (e: Exception) { AppLogger.e(TAG, "Failed to build task preflight block", e); "" }
 * ```
 *
 *  - The ONLY `AppLogger.e` call in the function sits inside the generic `catch (e: Exception)`
 *    block, immediately before its `return ""`.
 *  - Therefore: cancellation escaping the function proves that generic block was never entered,
 *    hence no `AppLogger.e` call happened on that path; and a `""` result proves that generic
 *    block DID run, hence the log call executed.
 */
class TaskPreflightProviderCancellationTest {

    private var previousSystemLogEnabled = true

    @Before
    fun disableAndroidSystemLogForJvmTests() {
        previousSystemLogEnabled = AppLogger.enableSystemLog
        AppLogger.enableSystemLog = false
    }

    @After
    fun restoreAndroidSystemLog() {
        AppLogger.enableSystemLog = previousSystemLogEnabled
    }

    // --- (i) CANCELLATION PROPAGATES: never converted into the "" no-block result ---

    @Test
    fun buildPreflightBlockFrom_cancellation_propagatesInsteadOfReturningEmptyBlock() = runTest {
        // The retrieve lambda never completes on its own; only cancellation can end it.
        val retrieve: suspend (String, Int) -> List<TaskEpisode> = { _, _ ->
            delay(10_000)
            emptyList()
        }

        try {
            // `runTest` installs a virtual-time scheduler and `withTimeout` uses that same
            // scheduler, so the 10_000ms `delay` above is advanced past the 50ms deadline
            // instantly (no real waiting) and `TimeoutCancellationException` is thrown. If the
            // provider swallowed cancellation, this call would return "" instead of throwing.
            val result =
                withTimeout(50) {
                    TaskPreflightProvider.buildPreflightBlockFrom(
                        retrieve = retrieve,
                        taskQuery = "in-place apk update",
                        currentTaskContext = "in-place apk update",
                        useEnglish = true,
                        topK = TaskPreflightProvider.DEFAULT_TOP_K
                    )
                }
            // `fail` throws AssertionError, which is NOT a CancellationException, so the catch
            // below cannot swallow it: reaching this line fails the test loudly.
            fail("cancellation must propagate out of buildPreflightBlockFrom, got: \"$result\"")
        } catch (cancellation: CancellationException) {
            // Expected: TimeoutCancellationException is a CancellationException subclass and it
            // reached the caller unchanged, proving the generic handler did not consume it.
            //
            // This is also the proof that no `AppLogger.e` call happened on this path: the only
            // `AppLogger.e` call in `buildPreflightBlockFrom` lives inside the generic
            // `catch (e: Exception)` block, and a CancellationException that escapes the function
            // proves that block was never entered. No static mocking of `AppLogger` is needed.
            assertTrue(
                "expected a timeout cancellation, got ${cancellation.javaClass.name}",
                cancellation is TimeoutCancellationException
            )
        }
    }

    // --- (ii) ORDINARY EXCEPTION: the documented "" no-block result is preserved ---

    @Test
    fun buildPreflightBlockFrom_ordinaryException_returnsEmptyBlock() = runTest {
        val retrieve: suspend (String, Int) -> List<TaskEpisode> = { _, _ ->
            throw IllegalStateException("boom")
        }

        val block =
            TaskPreflightProvider.buildPreflightBlockFrom(
                retrieve = retrieve,
                taskQuery = "in-place apk update",
                currentTaskContext = "in-place apk update",
                useEnglish = true,
                topK = TaskPreflightProvider.DEFAULT_TOP_K
            )

        // "" is the documented "no block produced" result, and it is produced by exactly one
        // place: the generic `catch (e: Exception)` block, whose `AppLogger.e(TAG, "Failed to
        // build task preflight block", e)` call is unconditional and immediately precedes the
        // `return ""`. So observing "" proves that catch block ran, which proves the log call
        // executed. The log call itself is deliberately not verified (that would need static
        // mocking of the `AppLogger` object).
        assertEquals("", block)
    }
}
