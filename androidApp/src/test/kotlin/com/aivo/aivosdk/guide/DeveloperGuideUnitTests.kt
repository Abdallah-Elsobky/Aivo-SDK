package com.aivo.aivosdk.guide

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JUnit test suite executing each Developer Guide verification test class.
 *
 * Runs all 11 test suites covering Sections 3 to 10 of docs/DEVELOPER_GUIDE.md.
 */
class DeveloperGuideUnitTests {

    @Test
    fun test01_quickStart() = runBlocking {
        val result = QuickStartTest().runTest()
        assertTrue(
            "QuickStartTest failed:\n${result.logs.joinToString("\n")}\nError: ${result.error?.message}",
            result.passed
        )
    }

    @Test
    fun test02_dxLevel1RawLlm() = runBlocking {
        val result = DxLevel1RawLlmTest().runTest()
        assertTrue(
            "DxLevel1RawLlmTest failed:\n${result.logs.joinToString("\n")}\nError: ${result.error?.message}",
            result.passed
        )
    }

    @Test
    fun test03_dxLevel2SingleAgentTools() = runBlocking {
        val result = DxLevel2SingleAgentToolsTest().runTest()
        assertTrue(
            "DxLevel2SingleAgentToolsTest failed:\n${result.logs.joinToString("\n")}\nError: ${result.error?.message}",
            result.passed
        )
    }

    @Test
    fun test04_dxLevel3MultiAgentTeam() = runBlocking {
        val result = DxLevel3MultiAgentTeamTest().runTest()
        assertTrue(
            "DxLevel3MultiAgentTeamTest failed:\n${result.logs.joinToString("\n")}\nError: ${result.error?.message}",
            result.passed
        )
    }

    @Test
    fun test05_dxLevel4CustomLoop() = runBlocking {
        val result = DxLevel4CustomLoopTest().runTest()
        assertTrue(
            "DxLevel4CustomLoopTest failed:\n${result.logs.joinToString("\n")}\nError: ${result.error?.message}",
            result.passed
        )
    }

    @Test
    fun test06_customToolsAndToolbox() = runBlocking {
        val result = CustomToolsAndToolboxTest().runTest()
        assertTrue(
            "CustomToolsAndToolboxTest failed:\n${result.logs.joinToString("\n")}\nError: ${result.error?.message}",
            result.passed
        )
    }

    @Test
    fun test07_declarativeAgentCatalogs() = runBlocking {
        val result = DeclarativeAgentCatalogsTest().runTest()
        assertTrue(
            "DeclarativeAgentCatalogsTest failed:\n${result.logs.joinToString("\n")}\nError: ${result.error?.message}",
            result.passed
        )
    }

    @Test
    fun test08_multiAgentPatterns() = runBlocking {
        val result = MultiAgentPatternsTest().runTest()
        assertTrue(
            "MultiAgentPatternsTest failed:\n${result.logs.joinToString("\n")}\nError: ${result.error?.message}",
            result.passed
        )
    }

    @Test
    fun test09_streamingEvents() = runBlocking {
        val result = StreamingEventsTest().runTest()
        assertTrue(
            "StreamingEventsTest failed:\n${result.logs.joinToString("\n")}\nError: ${result.error?.message}",
            result.passed
        )
    }

    @Test
    fun test10_productionGuardrails() = runBlocking {
        val result = ProductionGuardrailsTest().runTest()
        assertTrue(
            "ProductionGuardrailsTest failed:\n${result.logs.joinToString("\n")}\nError: ${result.error?.message}",
            result.passed
        )
    }

    @Test
    fun test11_endToEndMultiAgent() = runBlocking {
        val result = EndToEndMultiAgentTest().runTest()
        assertTrue(
            "EndToEndMultiAgentTest failed:\n${result.logs.joinToString("\n")}\nError: ${result.error?.message}",
            result.passed
        )
    }
}
