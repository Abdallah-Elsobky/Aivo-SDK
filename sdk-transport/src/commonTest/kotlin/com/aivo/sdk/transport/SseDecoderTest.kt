package com.aivo.sdk.transport

import com.aivo.sdk.transport.decoder.SseDecoder
import com.aivo.sdk.transport.protocol.RawFrame
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SseDecoderTest {

    @Test
    fun decode_handles_standard_events_and_done() = runTest {
        val lines = listOf(
            ": keep-alive comment",
            "event: update",
            "data: {\"text\":\"Hello\"}",
            "",
            "data: {\"text\":\" World\"}",
            "",
            "data: [DONE]",
        ).asFlow()

        val frames = SseDecoder().decode(lines).toList()
        assertEquals(3, frames.size)

        val frame1 = frames[0] as RawFrame.Data
        assertEquals("{\"text\":\"Hello\"}", frame1.data)
        assertEquals("update", frame1.eventType)

        val frame2 = frames[1] as RawFrame.Data
        assertEquals("{\"text\":\" World\"}", frame2.data)

        val frame3 = frames[2]
        assertTrue(frame3 is RawFrame.Done)
    }

    @Test
    fun decode_handles_crlf_and_multiline_data() = runTest {
        val lines = listOf(
            "data: line1\r",
            "data: line2\r",
            "\r",
        ).asFlow()

        val frames = SseDecoder().decode(lines).toList()
        assertEquals(1, frames.size)

        val frame = frames[0] as RawFrame.Data
        assertEquals("line1\nline2", frame.data)
    }
}
