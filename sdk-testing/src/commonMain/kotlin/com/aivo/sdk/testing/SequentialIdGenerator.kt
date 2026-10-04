package com.aivo.sdk.testing

import com.aivo.sdk.core.util.IdGenerator

/**
 * Deterministic test implementation of [IdGenerator].
 */
public class SequentialIdGenerator(
    private val prefix: String = "test-id",
) : IdGenerator {
    private var counter = 0

    override fun next(): String {
        counter++
        return "$prefix-$counter"
    }

    public fun reset() {
        counter = 0
    }
}
