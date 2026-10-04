package com.aivo.aivosdk.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aivo.aivosdk.ui.model.ChatUiAction
import com.aivo.aivosdk.ui.model.ChatUiState
import com.aivo.sdk.AivoProvider

/**
 * Configuration panel for selecting LLM provider, switching models, selecting agent mode, and managing API keys.
 */
@Composable
fun ProviderModelSelector(
    uiState: ChatUiState,
    onAction: (ChatUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            // Row 1: Provider Selection
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AivoProvider.entries.forEach { provider ->
                    val isSelected = uiState.selectedProvider == provider
                    if (isSelected) {
                        Button(
                            onClick = { onAction(ChatUiAction.SelectProvider(provider)) },
                            modifier = Modifier.height(36.dp),
                        ) {
                            Text(provider.displayName, fontSize = 13.sp)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onAction(ChatUiAction.SelectProvider(provider)) },
                            modifier = Modifier.height(36.dp),
                        ) {
                            Text(provider.displayName, fontSize = 13.sp)
                        }
                    }
                }

                OutlinedButton(
                    onClick = { onAction(ChatUiAction.ToggleConfigVisibility) },
                    modifier = Modifier.height(36.dp),
                ) {
                    Text(if (uiState.showConfig) "Hide Key" else "🔑 Key", fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Row 2: Model Dropdown Selector
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { onAction(ChatUiAction.SetModelMenuExpanded(true)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val activeText = if (uiState.isCustomModel) {
                        "✏️ Custom: ${uiState.customModelName.ifBlank { "Type model name..." }}"
                    } else {
                        "🤖 ${uiState.selectedModel.displayName} ${if (uiState.selectedModel.isFree) "• [FREE]" else "• [PAID]"}"
                    }
                    Text(activeText, fontSize = 13.sp, maxLines = 1)
                }

                DropdownMenu(
                    expanded = uiState.modelMenuExpanded,
                    onDismissRequest = { onAction(ChatUiAction.SetModelMenuExpanded(false)) },
                    modifier = Modifier.fillMaxWidth(0.9f),
                ) {
                    DropdownMenuItem(
                        text = { Text("✏️ Custom / Unlisted Model...", fontWeight = FontWeight.Bold) },
                        onClick = { onAction(ChatUiAction.SetCustomModelActive(true)) },
                    )

                    HorizontalDivider()

                    val freeList = uiState.selectedProvider.freeModels
                    if (freeList.isNotEmpty()) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "── FREE MODELS ──",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                            },
                            onClick = {},
                            enabled = false,
                        )
                        freeList.forEach { model ->
                            DropdownMenuItem(
                                text = { Text("🟢 ${model.displayName} (${model.modelId})", fontSize = 13.sp) },
                                onClick = { onAction(ChatUiAction.SelectModel(model)) },
                            )
                        }
                    }

                    val paidList = uiState.selectedProvider.paidModels
                    if (paidList.isNotEmpty()) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "── PAID MODELS ──",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline,
                                    fontWeight = FontWeight.Bold,
                                )
                            },
                            onClick = {},
                            enabled = false,
                        )
                        paidList.forEach { model ->
                            DropdownMenuItem(
                                text = { Text("⭐ ${model.displayName} (${model.modelId})", fontSize = 13.sp) },
                                onClick = { onAction(ChatUiAction.SelectModel(model)) },
                            )
                        }
                    }
                }
            }

            // Row 3: Agent Mode Selector
            AgentModeSelector(
                selectedAgentMode = uiState.selectedAgentMode,
                onAction = onAction,
            )

            // Optional Custom Model Input
            AnimatedVisibility(visible = uiState.isCustomModel) {
                OutlinedTextField(
                    value = uiState.customModelName,
                    onValueChange = { onAction(ChatUiAction.CustomModelChanged(it)) },
                    label = { Text("Custom Model Name") },
                    placeholder = { Text("e.g. gpt-4o or your-fine-tuned-model") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    singleLine = true,
                )
            }

            // Optional API Key Input
            AnimatedVisibility(visible = uiState.showConfig) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        OutlinedTextField(
                            value = uiState.currentApiKey,
                            onValueChange = { onAction(ChatUiAction.ApiKeyChanged(uiState.selectedProvider, it)) },
                            label = { Text("${uiState.selectedProvider.displayName} API Key") },
                            placeholder = { Text("Enter API key for ${uiState.selectedProvider.displayName}") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }
                }
            }
        }
    }
}
