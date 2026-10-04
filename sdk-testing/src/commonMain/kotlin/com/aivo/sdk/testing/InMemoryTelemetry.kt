package com.aivo.sdk.testing

import com.aivo.sdk.core.port.Telemetry
import com.aivo.sdk.core.port.TelemetryEvent

/**
 * In-memory test sink for observing emitted [TelemetryEvent] instances.
 */
public class InMemoryTelemetry : Telemetry {
    private val _events = mutableListOf<TelemetryEvent>()
    public val events: List<TelemetryEvent> get() = _events.toList()

    override fun record(event: TelemetryEvent) {
        _events.add(event)
    }

    public fun clear() {
        _events.clear()
    }

    public inline fun <reified T : TelemetryEvent> findEvents(): List<T> =
        events.filterIsInstance<T>()
}
