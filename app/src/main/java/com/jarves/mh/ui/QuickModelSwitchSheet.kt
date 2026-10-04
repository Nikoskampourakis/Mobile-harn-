package com.jarves.mh.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarves.mh.model.AgentKind
import com.jarves.mh.model.ProviderKind
import com.jarves.mh.model.ProviderProfile

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickModelSwitchSheet(
    currentProvider: ProviderProfile,
    agentKind: AgentKind,
    onDismiss: () -> Unit,
    onSelectModel: (String) -> Unit,
    onSelectProvider: (ProviderKind, String?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var customModel by rememberSaveable { mutableStateOf(currentProvider.model) }

    val presetModels: List<String> = when (currentProvider.kind) {
        ProviderKind.GEMINI -> listOf(
            "gemini-2.0-flash",
            "gemini-1.5-flash",
            "gemini-1.5-pro",
            "gemini-2.0-pro-exp-02-05",
        )
        ProviderKind.ANTHROPIC -> listOf(
            "claude-sonnet-4-6",
            "claude-3-7-sonnet-20250219",
            "claude-3-5-sonnet-20241022",
            "claude-3-5-haiku-20241022",
        )
        ProviderKind.LLM_ROUTER -> listOf(
            "~anthropic/claude-sonnet-latest",
            "deepseek/deepseek-r1",
            "openai/gpt-4o",
            "meta-llama/llama-3.3-70b-instruct",
        )
        ProviderKind.DEEPSEEK -> listOf(
            "deepseek-v4-flash",
            "deepseek-chat",
            "deepseek-reasoner",
        )
        ProviderKind.GROK -> listOf(
            "grok-3",
            "grok-2-1212",
        )
        ProviderKind.KIMI -> listOf(
            "kimi-k2.6",
            "moonshot-v1-8k",
            "moonshot-v1-32k",
        )
        ProviderKind.OPENCODE_ZEN -> listOf(
            "deepseek-v4-flash",
        )
        ProviderKind.NVIDIA_NIM -> listOf(
            "qwen/qwen2.5-coder-32b-instruct",
        )
        else -> listOfNotNull(
            currentProvider.kind.defaultModel.takeIf { it.isNotBlank() },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ElectricBolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            "Real-Time Model Switcher",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Active: ${currentProvider.kind.title} · ${currentProvider.model}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "Popular Models for ${currentProvider.kind.title}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                presetModels.forEach { modelName ->
                    val isSelected = currentProvider.model.equals(modelName, ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier.clickable {
                            onSelectModel(modelName)
                            onDismiss()
                        },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                modelName,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Text(
                "Custom Model Identifier",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = customModel,
                    onValueChange = { customModel = it },
                    placeholder = { Text("e.g. gemini-2.0-flash, gpt-4o, claude-3-7-sonnet") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val trimmed = customModel.trim()
                        if (trimmed.isNotBlank()) {
                            onSelectModel(trimmed)
                            onDismiss()
                        }
                    },
                    enabled = customModel.isNotBlank() && customModel.trim() != currentProvider.model,
                ) {
                    Text("Apply")
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(14.dp))

            Text(
                "Quick Switch Provider",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            val availableProviders = listOf(
                ProviderKind.GEMINI,
                ProviderKind.ANTHROPIC,
                ProviderKind.DEEPSEEK,
                ProviderKind.LLM_ROUTER,
                ProviderKind.GROK,
                ProviderKind.KIMI,
                ProviderKind.OPENCODE_ZEN,
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                availableProviders.forEach { provider ->
                    val isCurrent = currentProvider.kind == provider
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isCurrent) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.clickable {
                            if (!isCurrent) {
                                val targetModel = if (provider == ProviderKind.GEMINI) "gemini-2.0-flash" else provider.defaultModel
                                onSelectProvider(provider, targetModel)
                                onDismiss()
                            }
                        },
                    ) {
                        Text(
                            provider.title,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            fontSize = 12.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCurrent) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
