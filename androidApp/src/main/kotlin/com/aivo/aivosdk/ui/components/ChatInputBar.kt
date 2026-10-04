package com.aivo.aivosdk.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aivo.aivosdk.agent.SampleAgentMode
import com.aivo.aivosdk.ui.model.ChatUiAction

/**
 * Bottom input bar containing text entry and send/loading button.
 */
@Composable
fun ChatInputBar(
    inputText: String,
    isGenerating: Boolean,
    selectedAgentMode: SampleAgentMode,
    onAction: (ChatUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        tonalElevation = 3.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { onAction(ChatUiAction.InputTextChanged(it)) },
                placeholder = { Text("${selectedAgentMode.icon} Message ${selectedAgentMode.label}...") },
                modifier = Modifier.weight(1f),
                maxLines = 3,
                enabled = !isGenerating,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { onAction(ChatUiAction.SendMessage) },
                enabled = inputText.isNotBlank() && !isGenerating,
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Send")
                }
            }
        }
    }
}
