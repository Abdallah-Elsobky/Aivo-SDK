package com.example.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aivo.sdk.runtime.agent.AgentEvent
import com.example.shared.data.KmpChatRepository
import kotlinx.coroutines.launch

data class KmpChatMessage(
    val id: String,
    val text: String,
    val isUser: Boolean
)

/**
 * 100% shared Compose Multiplatform Chat UI running identically on Android, iOS, and Desktop.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KmpChatScreen(
    repository: KmpChatRepository,
    modifier: Modifier = Modifier
) {
    var messages by remember { mutableStateOf(listOf<KmpChatMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    fun sendMessage() {
        val query = inputText.trim()
        if (query.isBlank() || isGenerating) return

        inputText = ""
        val userMsg = KmpChatMessage(System.currentTimeMillis().toString(), query, isUser = true)
        messages = messages + userMsg
        isGenerating = true

        coroutineScope.launch {
            val assistantId = (System.currentTimeMillis() + 1).toString()
            var currentResponse = ""
            messages = messages + KmpChatMessage(assistantId, "", isUser = false)

            repository.streamResponse("kmp-session", query).collect { event ->
                when (event) {
                    is AgentEvent.TextDelta -> {
                        currentResponse += event.text
                        messages = messages.map {
                            if (it.id == assistantId) it.copy(text = currentResponse) else it
                        }
                    }
                    is AgentEvent.RunCompleted -> {
                        isGenerating = false
                    }
                    is AgentEvent.RunFailed -> {
                        isGenerating = false
                        currentResponse += "\n[Error: ${event.error.message}]"
                        messages = messages.map {
                            if (it.id == assistantId) it.copy(text = currentResponse) else it
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Aivo KMP Assistant") })
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Ask anything...") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { sendMessage() },
                    enabled = inputText.isNotBlank() && !isGenerating
                ) {
                    Text("Send")
                }
            }
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            items(messages) { msg ->
                val alignment = if (msg.isUser) Alignment.End else Alignment.Start
                val bg = if (msg.isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                val textColor = if (msg.isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = alignment
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(bg)
                            .padding(12.dp)
                            .widthIn(max = 300.dp)
                    ) {
                        Text(text = msg.text, color = textColor, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
