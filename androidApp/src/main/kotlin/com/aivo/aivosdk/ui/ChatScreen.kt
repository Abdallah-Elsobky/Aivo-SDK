package com.aivo.aivosdk.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aivo.aivosdk.ui.components.ChatBubble
import com.aivo.aivosdk.ui.components.ChatInputBar
import com.aivo.aivosdk.ui.components.ChatTopBar
import com.aivo.aivosdk.ui.components.ProviderModelSelector
import com.aivo.aivosdk.ui.model.ChatUiAction
import com.aivo.aivosdk.ui.model.ChatUiState

/**
 * Stateful entry point for the Chat screen.
 */
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    ChatScreenContent(
        uiState = uiState,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}

/**
 * Stateless screen layout for the Chat screen, ideal for UI testing and Compose previews.
 */
@Composable
fun ChatScreenContent(
    uiState: ChatUiState,
    onAction: (ChatUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ChatTopBar(
                selectedProvider = uiState.selectedProvider,
                selectedModel = uiState.selectedModel,
                selectedAgentMode = uiState.selectedAgentMode,
                isCustomModel = uiState.isCustomModel,
                customModelName = uiState.customModelName,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Provider, Model & Agent Mode selector panel
            ProviderModelSelector(
                uiState = uiState,
                onAction = onAction,
            )

            // Conversation messages
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(uiState.messages, key = { it.id }) { msg ->
                    ChatBubble(message = msg)
                }
            }

            // Input Bar
            ChatInputBar(
                inputText = uiState.inputText,
                isGenerating = uiState.isGenerating,
                selectedAgentMode = uiState.selectedAgentMode,
                onAction = onAction,
            )
        }
    }

    LaunchedEffect(uiState.messages.size, uiState.messages.lastOrNull()?.text) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }
}
