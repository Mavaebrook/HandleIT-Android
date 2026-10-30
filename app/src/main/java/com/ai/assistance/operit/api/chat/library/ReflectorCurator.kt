package com.ai.assistance.operit.api.chat.library

import android.content.Context
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.core.config.FunctionalPrompts
import com.ai.assistance.operit.data.learning.SkillRevision
import com.ai.assistance.operit.data.learning.TaskEpisodeRetrieval
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.TaskEpisode
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.ChatUtils
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/**
 * Logical Reflector and Curator roles (docs/TODO/reliable-learning-architect-20261016 §4).
 *
 * They are NOT separate agents or models: they reuse the existing configured model providers via
 * EnhancedAIService.callFunctionModel. The Reflector proposes evidence-backed lessons (TaskEpisode);
 * the Curator produces localized candidate skill revisions (SkillRevision, status draft).
 *
 * No every-turn call: callers must gate invocation with ReviewTriggerPolicy.
 */
object ReflectorCurator {

    private const val TAG = "ReflectorCurator"

    /**
     * Outcome of one Reflector review.
     *
     * [NoLesson] is a defined outcome, not a failure: a review can be triggered by a turn in which
     * nothing went wrong (a complex but successful turn, or a lexical correction marker that was not
     * a correction). Saving a lesson from such a turn turned the user's own request into stored
     * "constraints" and filled the store with noise
     * (docs/TODO/reflector-no-lesson-20261030/README.md).
     */
    sealed class Reflection {
        data class Lesson(val episode: TaskEpisode) : Reflection()
        data class NoLesson(val reason: String) : Reflection()
    }

    /**
     * Reflector: review one completed turn and return a lesson, or [Reflection.NoLesson] when the
     * evidence shows nothing to learn. Returns null on provider or parsing failure. Coroutine
     * cancellation propagates to the caller.
     */
    suspend fun reflect(
        context: Context,
        taskDescription: String,
        useEnglish: Boolean
    ): Reflection? = reflect(
        taskDescription = taskDescription,
        useEnglish = useEnglish,
        callModel = { type, prompt -> callFunctionModel(context, type, prompt) },
        onFailure = { AppLogger.e(TAG, "Reflector call failed", it) }
    )

    // The public entry point uses this same path; JVM tests can supply a provider without Android.
    internal suspend fun reflect(
        taskDescription: String,
        useEnglish: Boolean,
        callModel: suspend (FunctionType, String) -> String,
        onFailure: (Throwable) -> Unit
    ): Reflection? {
        // taskDescription is the labeled ReflectorInput JSON built by ReviewScheduler.
        val prompt = FunctionalPrompts.reflectorPrompt(useEnglish) + "\n\nTurn evidence (JSON):\n" + taskDescription
        val raw = callModelOrNull(FunctionType.MEMORY, prompt, callModel, onFailure) ?: return null
        return parseReflection(ChatUtils.removeThinkingContent(raw).trim())
    }

    /**
     * Curator: propose a localized candidate skill revision from a lesson and an existing skill.
     * Returns null on provider or parsing failure. Coroutine cancellation propagates to the caller.
     * The result is always a draft candidate.
     */
    suspend fun curate(
        context: Context,
        skillId: String,
        lessonSummary: String,
        existingSkillContent: String,
        useEnglish: Boolean
    ): SkillRevision? = curate(
        skillId = skillId,
        lessonSummary = lessonSummary,
        existingSkillContent = existingSkillContent,
        useEnglish = useEnglish,
        callModel = { type, prompt -> callFunctionModel(context, type, prompt) },
        onFailure = { AppLogger.e(TAG, "Curator call failed", it) }
    )

    internal suspend fun curate(
        skillId: String,
        lessonSummary: String,
        existingSkillContent: String,
        useEnglish: Boolean,
        callModel: suspend (FunctionType, String) -> String,
        onFailure: (Throwable) -> Unit
    ): SkillRevision? {
        val prompt = FunctionalPrompts.curatorPrompt(useEnglish) +
            "\n\nSkill id: " + skillId +
            "\n\nExisting skill content:\n" + existingSkillContent +
            "\n\nLesson:\n" + lessonSummary
        val raw = callModelOrNull(FunctionType.SUMMARY, prompt, callModel, onFailure) ?: return null
        return parseRevision(ChatUtils.removeThinkingContent(raw).trim(), skillId)
    }

    private suspend fun callFunctionModel(context: Context, type: FunctionType, prompt: String): String =
        EnhancedAIService.getInstance(context).callFunctionModel(
            functionType = type,
            turns = listOf(PromptTurn(kind = PromptTurnKind.USER, content = prompt)),
            enableThinking = false,
            recordTokenUsage = true
        )

    private suspend fun callModelOrNull(
        type: FunctionType,
        prompt: String,
        callModel: suspend (FunctionType, String) -> String,
        onFailure: (Throwable) -> Unit
    ): String? = runCatching {
        // This includes service acquisition, so cancellation cannot be lost before the model call.
        callModel(type, prompt)
    }.onFailure {
        // A cancelled review must reach the scheduler's cancellation handler, not its retry path.
        if (it is CancellationException) throw it
        onFailure(it)
    }.getOrNull()

    /**
     * Parse Reflector output into a [Reflection]. `{"no_lesson": true, "reason": "..."}` is the
     * explicit "nothing to learn" answer; anything else is parsed as a lesson by [parseEpisode].
     * Returns null when the output is not a JSON object.
     */
    internal fun parseReflection(raw: String): Reflection? {
        val json = extractJsonObject(raw) ?: return null
        if (json.optBoolean("no_lesson", false)) {
            return Reflection.NoLesson(json.optString("reason", "").trim())
        }
        return parseEpisode(raw)?.let { Reflection.Lesson(it) }
    }

    internal fun parseEpisode(raw: String): TaskEpisode? {
        val json = extractJsonObject(raw) ?: return null
        return runCatching {
            TaskEpisode(
                taskSummary = json.optString("task_summary", ""),
                contextSummary = json.optString("context_summary", ""),
                outcome = json.optString("outcome", TaskEpisode.OUTCOME_UNKNOWN),
                failureSummary = json.optString("failure_summary", ""),
                causeSummary = json.optString("cause_summary", ""),
                causeIsHypothesis = json.optBoolean("cause_is_hypothesis", false),
                resolutionSummary = json.optString("resolution_summary", ""),
                requiredPrerequisites = readStringList(json, "required_prerequisites"),
                applicableConstraints = readStringList(json, "applicable_constraints"),
                evidenceKeys = TaskEpisodeRetrieval.deduplicateEvidenceKeys(readStringList(json, "evidence_keys")),
                createdAtMs = System.currentTimeMillis()
            )
        }.getOrNull()
    }

    internal fun parseRevision(raw: String, fallbackSkillId: String): SkillRevision? {
        val json = extractJsonObject(raw) ?: return null
        return runCatching {
            SkillRevision(
                skillId = json.optString("skill_id", fallbackSkillId).ifBlank { fallbackSkillId },
                scope = json.optString("scope", ""),
                prerequisites = readStringList(json, "prerequisites"),
                steps = readStringList(json, "steps"),
                checks = readStringList(json, "checks"),
                pitfalls = readStringList(json, "pitfalls"),
                evidenceKeys = TaskEpisodeRetrieval.deduplicateEvidenceKeys(readStringList(json, "evidence_keys")),
                userConstraints = readStringList(json, "user_constraints"),
                status = SkillRevision.STATUS_DRAFT,
                createdAtMs = System.currentTimeMillis()
            )
        }.getOrNull()
    }

    private fun readStringList(json: JSONObject, key: String): List<String> {
        val array = json.optJSONArray(key) ?: return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val value = array.optString(i, "").trim()
            if (value.isNotEmpty()) result.add(value)
        }
        return result
    }

    private fun extractJsonObject(raw: String): JSONObject? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(raw.substring(start, end + 1)) }.getOrNull()
    }
}
