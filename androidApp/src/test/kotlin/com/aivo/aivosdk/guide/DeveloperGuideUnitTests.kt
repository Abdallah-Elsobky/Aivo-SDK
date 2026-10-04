package com.aivo.aivosdk.guide

import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * JUnit test suite executing each verification test from SdkTestSuite.
 *
 * Runs deterministic offline mock tests covering every layer of the Aivo SDK.
 */
class DeveloperGuideUnitTests {

    private fun runCase(id: String) = runBlocking {
        val testCase = SdkTestSuite.allTests.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Test case with id '$id' not found in SdkTestSuite")
        val logs = mutableListOf<String>()
        try {
            testCase.execute { log -> logs.add(log) }
        } catch (t: Throwable) {
            throw AssertionError("Test '${testCase.name}' failed:\n${logs.joinToString("\n")}", t)
        }
    }

    @Test
    fun test01_init() = runCase("test_01_init")

    @Test
    fun test02_llm_generate() = runCase("test_02_llm_generate")

    @Test
    fun test03_llm_stream() = runCase("test_03_llm_stream")

    @Test
    fun test04_single_agent() = runCase("test_04_single_agent")

    @Test
    fun test05_tool_calling() = runCase("test_05_tool_calling")

    @Test
    fun test06_supervisor_team() = runCase("test_06_supervisor_team")

    @Test
    fun test07_pipeline_workflow() = runCase("test_07_pipeline_workflow")

    @Test
    fun test08_token_budget_guardrail() = runCase("test_08_token_budget_guardrail")

    @Test
    fun test09_observability_events() = runCase("test_09_observability_events")

    @Test
    fun test10_dynamic_tool() = runCase("test_10_dynamic_tool")
}
