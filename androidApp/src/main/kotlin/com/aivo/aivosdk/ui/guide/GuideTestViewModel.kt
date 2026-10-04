package com.aivo.aivosdk.ui.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aivo.aivosdk.guide.SdkTestCase
import com.aivo.aivosdk.guide.SdkTestSuite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TestFilter {
    ALL,
    PASSED,
    FAILED,
}

sealed interface TestExecutionStatus {
    data object Pending : TestExecutionStatus
    data object Running : TestExecutionStatus
    data class Passed(val durationMs: Long, val message: String) : TestExecutionStatus
    data class Failed(val durationMs: Long, val error: String) : TestExecutionStatus
}

data class TestItemState(
    val testCase: SdkTestCase,
    val status: TestExecutionStatus = TestExecutionStatus.Pending,
    val logs: List<String> = emptyList(),
    val isExpanded: Boolean = false,
)

data class GuideTestUiState(
    val tests: List<TestItemState> = SdkTestSuite.allTests.map { TestItemState(it) },
    val isRunningAll: Boolean = false,
    val currentFilter: TestFilter = TestFilter.ALL,
) {
    val totalCount: Int = tests.size
    val passedCount: Int = tests.count { it.status is TestExecutionStatus.Passed }
    val failedCount: Int = tests.count { it.status is TestExecutionStatus.Failed }
    val runningCount: Int = tests.count { it.status is TestExecutionStatus.Running }
    val pendingCount: Int = tests.count { it.status is TestExecutionStatus.Pending }

    val filteredTests: List<TestItemState>
        get() = when (currentFilter) {
            TestFilter.ALL -> tests
            TestFilter.PASSED -> tests.filter { it.status is TestExecutionStatus.Passed }
            TestFilter.FAILED -> tests.filter { it.status is TestExecutionStatus.Failed }
        }
}

class GuideTestViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(GuideTestUiState())
    val uiState: StateFlow<GuideTestUiState> = _uiState.asStateFlow()

    fun runAllTests() {
        if (_uiState.value.isRunningAll) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRunningAll = true) }

            for (item in _uiState.value.tests) {
                runSingleTest(item.testCase.id)
            }

            _uiState.update { it.copy(isRunningAll = false) }
        }
    }

    fun runSingleTest(testId: String) {
        viewModelScope.launch {
            val testCase = _uiState.value.tests.firstOrNull { it.testCase.id == testId }?.testCase ?: return@launch

            // Mark running & reset logs
            updateTestItem(testId) { current ->
                current.copy(
                    status = TestExecutionStatus.Running,
                    logs = listOf("▶ Starting test: ${testCase.name}"),
                    isExpanded = true,
                )
            }

            val startTime = System.currentTimeMillis()
            val capturedLogs = mutableListOf<String>()
            capturedLogs.add("▶ Starting test: ${testCase.name}")

            try {
                testCase.execute { logMsg ->
                    capturedLogs.add(logMsg)
                    updateTestItem(testId) { current ->
                        current.copy(logs = capturedLogs.toList())
                    }
                }

                val duration = System.currentTimeMillis() - startTime
                capturedLogs.add("✅ Test PASSED in ${duration}ms")
                updateTestItem(testId) { current ->
                    current.copy(
                        status = TestExecutionStatus.Passed(duration, "Success"),
                        logs = capturedLogs.toList(),
                    )
                }
            } catch (e: Throwable) {
                val duration = System.currentTimeMillis() - startTime
                val errorMsg = "${e::class.simpleName}: ${e.message}"
                capturedLogs.add("❌ Test FAILED in ${duration}ms: $errorMsg")
                updateTestItem(testId) { current ->
                    current.copy(
                        status = TestExecutionStatus.Failed(duration, errorMsg),
                        logs = capturedLogs.toList(),
                        isExpanded = true,
                    )
                }
            }
        }
    }

    fun toggleExpand(testId: String) {
        updateTestItem(testId) { it.copy(isExpanded = !it.isExpanded) }
    }

    fun setFilter(filter: TestFilter) {
        _uiState.update { it.copy(currentFilter = filter) }
    }

    fun resetAll() {
        _uiState.update {
            it.copy(
                tests = SdkTestSuite.allTests.map { tc -> TestItemState(tc) },
                isRunningAll = false,
            )
        }
    }

    private fun updateTestItem(testId: String, transform: (TestItemState) -> TestItemState) {
        _uiState.update { state ->
            val updated = state.tests.map { item ->
                if (item.testCase.id == testId) transform(item) else item
            }
            state.copy(tests = updated)
        }
    }
}
