package com.aivo.aivosdk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aivo.aivosdk.agent.SampleAgentMode
import com.aivo.sdk.AivoProvider
import com.aivo.sdk.core.model.ProviderModel

/**
 * Top app bar displaying connection status indicator, provider, and active model.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    selectedProvider: AivoProvider,
    selectedModel: ProviderModel,
    selectedAgentMode: SampleAgentMode,
    isCustomModel: Boolean,
    customModelName: String,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        "Aivo SDK (${selectedProvider.displayName})",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val modelLabel = if (isCustomModel) {
                        "Custom: ${customModelName.ifBlank { "Unspecified" }}"
                    } else {
                        "${selectedModel.displayName} • ${selectedAgentMode.label}"
                    }
                    Text(
                        modelLabel,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    )
}
