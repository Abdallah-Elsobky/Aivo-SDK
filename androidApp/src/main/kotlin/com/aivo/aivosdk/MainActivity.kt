package com.aivo.aivosdk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aivo.aivosdk.data.ChatRepositoryImpl
import com.aivo.aivosdk.ui.ChatScreen
import com.aivo.aivosdk.ui.ChatViewModel
import com.aivo.aivosdk.ui.guide.GuideTestScreen
import com.aivo.aivosdk.ui.guide.GuideTestViewModel

/**
 * Sample application entry point demonstrating Aivo SDK integration on Android.
 *
 * Provides:
 * 1. Live Chat Demo — Multi-provider, multi-agent chat interface with live streaming.
 * 2. SDK Test Suite — End-to-end verification dashboard proving SDK correctness.
 */
class MainActivity : ComponentActivity() {

    private val chatViewModel: ChatViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ChatViewModel(ChatRepositoryImpl()) as T
            }
        }
    }

    private val guideTestViewModel: GuideTestViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    var selectedTab by remember { mutableIntStateOf(0) }

                    Scaffold(
                        bottomBar = {
                            NavigationBar(modifier = Modifier.fillMaxWidth()) {
                                NavigationBarItem(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    icon = { Text("💬", fontSize = 18.sp) },
                                    label = { Text("Live Chat") },
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    icon = { Text("🧪", fontSize = 18.sp) },
                                    label = { Text("SDK Tests") },
                                )
                            }
                        }
                    ) { padding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                        ) {
                            if (selectedTab == 0) {
                                ChatScreen(
                                    viewModel = chatViewModel,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                GuideTestScreen(
                                    viewModel = guideTestViewModel,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}