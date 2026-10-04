package com.aivo.aivosdk.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aivo.aivosdk.agent.SampleAgentMode
import com.aivo.aivosdk.ui.model.ChatUiAction

/**
 * Filter chip row allowing instant switching between agent architecture modes.
 */
@Composable
fun AgentModeSelector(
    selectedAgentMode: SampleAgentMode,
    onAction: (ChatUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Agent:",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SampleAgentMode.entries.forEach { mode ->
            val isSelected = selectedAgentMode == mode
            if (isSelected) {
                Button(
                    onClick = { onAction(ChatUiAction.SelectAgentMode(mode)) },
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                ) {
                    Text("${mode.icon} ${mode.label}", fontSize = 12.sp)
                }
            } else {
                OutlinedButton(
                    onClick = { onAction(ChatUiAction.SelectAgentMode(mode)) },
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                ) {
                    Text("${mode.icon} ${mode.label}", fontSize = 12.sp)
                }
            }
        }
    }
}
