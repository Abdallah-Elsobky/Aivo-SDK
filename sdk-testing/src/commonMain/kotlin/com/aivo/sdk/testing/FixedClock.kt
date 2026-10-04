package com.aivo.sdk.testing

import com.aivo.sdk.core.util.Clock

/**
 * Deterministic test implementation of [Clock].
 */
public class FixedClock(
    public var currentMillis: Long = 1_700_000_000_000L,
) : Clock {
    override fun nowMillis(): Long = currentMillis

    public fun advance(millis: Long) {
        currentMillis += millis
    }
}
