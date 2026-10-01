package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.collects.ApiProviderConfigs
import com.ai.assistance.operit.data.collects.ModelThinkingConfigDefaults
import com.ai.assistance.operit.data.model.ApiProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XaiProviderReasoningTest {
    @Test
    fun defaultConfigUsesTheOfficialXaiEndpointAndModel() {
        assertEquals(
            "grok-4.6",
            ApiProviderConfigs.getDefaultModelName(ApiProviderType.XAI)
        )
        assertEquals(
            "https://api.x.ai/v1/chat/completions",
            ApiProviderConfigs.getDefaultApiEndpoint(ApiProviderType.XAI)
        )
        assertEquals(
            "https://api.x.ai/v1/models",
            ModelListFetcher.getModelsListUrl(
                "https://api.x.ai/v1/chat/completions",
                ApiProviderType.XAI
            )
        )
    }

    private fun xaiMapping(modelName: String): ThinkingQualityMapping =
        ThinkingQualityMappingRegistry.resolve(
            providerTypeId = ApiProviderType.XAI.name,
            modelName = modelName,
            apiEndpoint = "",
            thinkingConfigurations = ModelThinkingConfigDefaults.forProvider(ApiProviderType.XAI.name)
        )

    @Test
    fun enabledOptionsMapToXaiEfforts() {
        val mapping = xaiMapping("grok-4.6")
        assertEquals(
            listOf("low", "medium", "high", "xhigh"),
            listOf("low", "medium", "high", "xhigh").map { mapping.textValueFor(it) }
        )
    }

    @Test
    fun mapperPreservesTheSelectedEffort() {
        assertEquals("high", xaiMapping("grok-4.6").textValueFor("high"))
    }

    @Test
    fun reasoningEffortUsesTheGrokFamilyRule() {
        assertTrue(xaiMapping("grok-4.6").control == ThinkingQualityControl.LEVELS)
        assertTrue(xaiMapping("grok-4.5-latest").control == ThinkingQualityControl.LEVELS)
        assertTrue(xaiMapping("grok-3-mini").control == ThinkingQualityControl.LEVELS)
        assertFalse(xaiMapping("gpt-4o").control == ThinkingQualityControl.LEVELS)
    }
}
