package com.example.aivo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Production-ready Jetpack Compose Chat Screen utilizing Aivo SDK streaming events.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onSendMessage: (String) -> Unit,
    onConfirmTool: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll on new message / token arrival
    LaunchedEffect(uiState.messages.size, uiState.messages.lastOrNull()?.content) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Aivo Assistant", fontSize = 18.sp)
                        uiState.activeAgent?.let {
                            Text("Agent: $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        bottomBar = {
            ChatInputBar(
                inputText = inputText,
                onTextChanged = { inputText = it },
                isGenerating = uiState.isGenerating,
                onSend = {
                    if (inputText.isNotBlank() && !uiState.isGenerating) {
                        val text = inputText
                        inputText = ""
                        onSendMessage(text)
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(uiState.messages) { message ->
                    MessageBubble(message = message)
                }

                // Tool Execution Status Badge
                if (uiState.activeToolName != null) {
                    item {
                        ToolExecutionIndicator(toolName = uiState.activeToolName)
                    }
                }

                // Error Banner
                if (uiState.errorMessage != null) {
                    item {
                        ErrorMessageCard(message = uiState.errorMessage)
                    }
                }
            }

            // Human-in-the-Loop Confirmation Dialog for High-Risk Tools
            uiState.pendingConfirmation?.let { confirmation ->
                AlertDialog(
                    onDismissRequest = { onConfirmTool(false) },
                    title = { Text("Approve Tool Execution") },
                    text = {
                        Column {
                            Text("The agent wants to execute high-risk tool:")
                            Text(
                                text = confirmation.toolName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Parameters: ${confirmation.argumentsJson}", fontSize = 12.sp)
                        }
                    },
                    confirmButton = {
                        Button(onClick = { onConfirmTool(true) }) {
                            Text("Allow")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { onConfirmTool(false) }) {
                            Text("Deny")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun MessageBubble(message: UiMessage) {
    val isUser = message.isUser
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bgColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        // Reasoning / Chain-of-thought accordion if present
        if (!message.reasoning.isNullOrBlank()) {
            var expanded by remember { mutableStateOf(false) }
            OutlinedCard(
                onClick = { expanded = !expanded },
                modifier = Modifier.padding(bottom = 4.dp).widthIn(max = 300.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = if (expanded) "▼ Thinking Process" else "▶ Thinking Process (${message.reasoning.length} chars)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    AnimatedVisibility(visible = expanded) {
                        Text(
                            text = message.reasoning,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(bgColor)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .widthIn(max = 320.dp)
        ) {
            Text(
                text = message.content,
                color = textColor,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
fun ToolExecutionIndicator(toolName: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(
            text = "Executing tool: $toolName...",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
fun ErrorMessageCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(12.dp),
            fontSize = 13.sp
        )
    }
}

@Composable
fun ChatInputBar(
    inputText: String,
    onTextChanged: (String) -> Unit,
    isGenerating: Boolean,
    onSend: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .navigationBarsPadding()
                .imePadding()
        ) {
            TextField(
                value = inputText,
                onValueChange = onTextChanged,
                placeholder = { Text("Ask Aivo Assistant...") },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onSend,
                enabled = inputText.isNotBlank() && !isGenerating,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (inputText.isNotBlank() && !isGenerating)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (inputText.isNotBlank() && !isGenerating)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
