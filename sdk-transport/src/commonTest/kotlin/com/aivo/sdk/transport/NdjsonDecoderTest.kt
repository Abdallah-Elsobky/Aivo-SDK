package com.aivo.sdk.transport

import com.aivo.sdk.transport.decoder.NdjsonDecoder
import com.aivo.sdk.transport.protocol.RawFrame
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NdjsonDecoderTest {

    @Test
    fun decode_handles_ndjson_lines_and_skips_blank_lines() = runTest {
        val lines = listOf(
            "{\"message\": {\"content\": \"Line 1\"}}",
            "",
            "   ",
            "{\"message\": {\"content\": \"Line 2\"}}",
        ).asFlow()

        val frames = NdjsonDecoder().decode(lines).toList()
        assertEquals(2, frames.size)
        assertTrue(frames[0] is RawFrame.Data)
        assertTrue(frames[1] is RawFrame.Data)
    }

    @Test
    fun decode_detects_error_line() = runTest {
        val lines = listOf(
            "{\"error\": \"Model not found\"}",
        ).asFlow()

        val frames = NdjsonDecoder().decode(lines).toList()
        assertEquals(1, frames.size)
        assertTrue(frames[0] is RawFrame.Error)
    }
}
