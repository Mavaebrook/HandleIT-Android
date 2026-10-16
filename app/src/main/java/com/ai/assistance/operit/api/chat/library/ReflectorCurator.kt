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
     * Reflector: propose one evidence-backed lesson from a completed task episode.
     * Returns null on any failure (never throws).
     */
    suspend fun reflect(
        context: Context,
        taskDescription: String,
        useEnglish: Boolean
    ): TaskEpisode? {
        val service = runCatching { EnhancedAIService.getInstance(context) }.getOrNull() ?: return null
        val prompt = FunctionalPrompts.reflectorPrompt(useEnglish) + "\n\nTask episode:\n" + taskDescription
        val raw = runCatching {
            service.callFunctionModel(
                functionType = FunctionType.MEMORY,
                turns = listOf(PromptTurn(kind = PromptTurnKind.USER, content = prompt)),
                enableThinking = false,
                recordTokenUsage = true
            )
        }.onFailure { AppLogger.e(TAG, "Reflector call failed", it) }.getOrNull() ?: return null
        return parseEpisode(ChatUtils.removeThinkingContent(raw).trim())
    }

    /**
     * Curator: propose a localized candidate skill revision from a lesson and an existing skill.
     * Returns null on any failure (never throws). The result is always a draft candidate.
     */
    suspend fun curate(
        context: Context,
        skillId: String,
        lessonSummary: String,
        existingSkillContent: String,
        useEnglish: Boolean
    ): SkillRevision? {
        val service = runCatching { EnhancedAIService.getInstance(context) }.getOrNull() ?: return null
        val prompt = FunctionalPrompts.curatorPrompt(useEnglish) +
            "\n\nSkill id: " + skillId +
            "\n\nExisting skill content:\n" + existingSkillContent +
            "\n\nLesson:\n" + lessonSummary
        val raw = runCatching {
            service.callFunctionModel(
                functionType = FunctionType.SUMMARY,
                turns = listOf(PromptTurn(kind = PromptTurnKind.USER, content = prompt)),
                enableThinking = false,
                recordTokenUsage = true
            )
        }.onFailure { AppLogger.e(TAG, "Curator call failed", it) }.getOrNull() ?: return null
        return parseRevision(ChatUtils.removeThinkingContent(raw).trim(), skillId)
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
