package com.aivo.aivosdk.ui.guide

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GuideTestScreen(
    viewModel: GuideTestViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            // Header: Title & Overview
            Text(
                text = "🧪 Aivo SDK Verification Suite",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Automated end-to-end tests validating SDK runtime, LLM streaming, tools, supervisor, and guardrails.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Scorecard / Progress Summary
            SummaryCard(uiState = uiState)

            Spacer(modifier = Modifier.height(12.dp))

            // Controls & Filters
            ActionRow(
                uiState = uiState,
                onRunAll = viewModel::runAllTests,
                onReset = viewModel::resetAll,
                onFilterChanged = viewModel::setFilter,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Test Case List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(uiState.filteredTests, key = { it.testCase.id }) { item ->
                    TestCaseCard(
                        item = item,
                        onRun = { viewModel.runSingleTest(item.testCase.id) },
                        onToggleExpand = { viewModel.toggleExpand(item.testCase.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(uiState: GuideTestUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CountBadge(label = "Total", count = uiState.totalCount, color = MaterialTheme.colorScheme.primary)
                    CountBadge(label = "Passed", count = uiState.passedCount, color = Color(0xFF2E7D32))
                    if (uiState.failedCount > 0) {
                        CountBadge(label = "Failed", count = uiState.failedCount, color = MaterialTheme.colorScheme.error)
                    }
                    CountBadge(label = "Pending", count = uiState.pendingCount, color = MaterialTheme.colorScheme.outline)
                }

                val percentage = if (uiState.totalCount > 0) {
                    ((uiState.passedCount.toFloat() / uiState.totalCount) * 100).toInt()
                } else 0
                Text(
                    text = "$percentage% PASS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (uiState.passedCount == uiState.totalCount) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val progress = if (uiState.totalCount > 0) uiState.passedCount.toFloat() / uiState.totalCount else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (uiState.passedCount == uiState.totalCount) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

@Composable
private fun CountBadge(label: String, count: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$label: $count",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ActionRow(
    uiState: GuideTestUiState,
    onRunAll: () -> Unit,
    onReset: () -> Unit,
    onFilterChanged: (TestFilter) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onRunAll,
                enabled = !uiState.isRunningAll,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(40.dp),
            ) {
                if (uiState.isRunningAll) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Running All (${uiState.runningCount})...", fontSize = 13.sp)
                } else {
                    Text("▶ Run All Tests (${uiState.totalCount})", fontSize = 13.sp)
                }
            }

            OutlinedButton(
                onClick = onReset,
                enabled = !uiState.isRunningAll,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(40.dp),
            ) {
                Text("Reset", fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TestFilter.entries.forEach { filter ->
                FilterChip(
                    selected = uiState.currentFilter == filter,
                    onClick = { onFilterChanged(filter) },
                    label = {
                        val count = when (filter) {
                            TestFilter.ALL -> uiState.totalCount
                            TestFilter.PASSED -> uiState.passedCount
                            TestFilter.FAILED -> uiState.failedCount
                        }
                        Text("${filter.name.lowercase().replaceFirstChar { it.uppercase() }} ($count)", fontSize = 12.sp)
                    },
                )
            }
        }
    }
}

@Composable
private fun TestCaseCard(
    item: TestItemState,
    onRun: () -> Unit,
    onToggleExpand: () -> Unit,
) {
    val borderColor = when (item.status) {
        is TestExecutionStatus.Passed -> Color(0xFF2E7D32).copy(alpha = 0.5f)
        is TestExecutionStatus.Failed -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
        is TestExecutionStatus.Running -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        TestExecutionStatus.Pending -> MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    StatusIcon(status = item.status)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.testCase.name,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 2.dp),
                        ) {
                            BadgeChip(text = item.testCase.levelBadge)
                            when (val st = item.status) {
                                is TestExecutionStatus.Passed -> Text("Passed (${st.durationMs}ms)", fontSize = 11.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                is TestExecutionStatus.Failed -> Text("Failed (${st.durationMs}ms)", fontSize = 11.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                TestExecutionStatus.Running -> Text("Running...", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                TestExecutionStatus.Pending -> Text("Pending", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = onRun,
                        enabled = item.status !is TestExecutionStatus.Running,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(32.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
                    ) {
                        Text("Run", fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (item.isExpanded) "▲" else "▼",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }

            Text(
                text = item.testCase.description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            AnimatedVisibility(visible = item.isExpanded && item.logs.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1E1E1E))
                        .padding(8.dp)
                ) {
                    item.logs.forEach { logLine ->
                        val textColor = when {
                            logLine.contains("❌") || logLine.contains("FAILED") -> Color(0xFFFF6B6B)
                            logLine.contains("✅") || logLine.contains("PASSED") || logLine.contains("✓") -> Color(0xFF69F0AE)
                            logLine.contains("→") -> Color(0xFF82B1FF)
                            else -> Color(0xFFE0E0E0)
                        }
                        Text(
                            text = logLine,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = textColor,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusIcon(status: TestExecutionStatus) {
    when (status) {
        TestExecutionStatus.Pending -> Text("⏳", fontSize = 16.sp)
        TestExecutionStatus.Running -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        is TestExecutionStatus.Passed -> Text("✅", fontSize = 16.sp)
        is TestExecutionStatus.Failed -> Text("❌", fontSize = 16.sp)
    }
}

@Composable
private fun BadgeChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
