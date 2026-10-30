package com.ai.assistance.operit.ui.features.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.ApiPreferences
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Settings for prompt preparation (PromptPreprocessor): on/off, whether the preparation call uses
 * thinking, and how long it may take before the turn continues without a brief.
 * docs/TODO/prompt-preparation-settings-20261030/README.md
 */
@Composable
fun PromptPreparationSettingsSection(backgroundColor: Color) {
    val context = LocalContext.current
    val apiPreferences = remember { ApiPreferences.getInstance(context) }
    val scope = rememberCoroutineScope()

    val enabled by apiPreferences.promptPreparationEnabledFlow
        .collectAsState(initial = ApiPreferences.DEFAULT_PROMPT_PREPARATION_ENABLED)
    val thinking by apiPreferences.promptPreparationThinkingFlow
        .collectAsState(initial = ApiPreferences.DEFAULT_PROMPT_PREPARATION_THINKING)
    val savedTimeout by apiPreferences.promptPreparationTimeoutSecondsFlow
        .collectAsState(initial = ApiPreferences.DEFAULT_PROMPT_PREPARATION_TIMEOUT_SECONDS)

    // Slider position while dragging; saved once the drag ends.
    var timeoutSlider by remember { mutableFloatStateOf(savedTimeout.toFloat()) }
    LaunchedEffect(savedTimeout) { timeoutSlider = savedTimeout.toFloat() }

    val minTimeout = ApiPreferences.MIN_PROMPT_PREPARATION_TIMEOUT_SECONDS
    val maxTimeout = ApiPreferences.MAX_PROMPT_PREPARATION_TIMEOUT_SECONDS

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.prompt_preparation_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }

    PreparationSwitchRow(
        title = stringResource(R.string.prompt_preparation_enable),
        subtitle = stringResource(R.string.prompt_preparation_enable_desc),
        checked = enabled,
        onCheckedChange = { scope.launch { apiPreferences.savePromptPreparationEnabled(it) } },
        backgroundColor = backgroundColor
    )
    PreparationSwitchRow(
        title = stringResource(R.string.prompt_preparation_thinking),
        subtitle = stringResource(R.string.prompt_preparation_thinking_desc),
        checked = thinking,
        onCheckedChange = { scope.launch { apiPreferences.savePromptPreparationThinking(it) } },
        backgroundColor = backgroundColor,
        enabled = enabled
    )

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .padding(bottom = 4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(backgroundColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .alpha(if (enabled) 1f else 0.38f),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.prompt_preparation_timeout),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = stringResource(R.string.prompt_preparation_timeout_value, timeoutSlider.roundToInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = stringResource(R.string.prompt_preparation_timeout_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = timeoutSlider,
            onValueChange = { timeoutSlider = it.roundToInt().toFloat() },
            onValueChangeFinished = {
                val seconds = timeoutSlider.roundToInt()
                scope.launch { apiPreferences.savePromptPreparationTimeoutSeconds(seconds) }
            },
            valueRange = minTimeout.toFloat()..maxTimeout.toFloat(),
            // One stop per 5 seconds between the ends.
            steps = (maxTimeout - minTimeout) / 5 - 1,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PreparationSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    backgroundColor: Color,
    enabled: Boolean = true
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(bottom = 4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(backgroundColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .alpha(if (enabled) 1f else 0.38f),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
