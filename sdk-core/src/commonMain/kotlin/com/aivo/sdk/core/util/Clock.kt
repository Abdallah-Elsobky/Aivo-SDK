package com.aivo.sdk.core.util

/** Abstraction over system time so tests can use a fixed clock without real delays. */
public interface Clock {
    /** Returns current epoch time in milliseconds. */
    public fun nowMillis(): Long
}

/** Production clock that delegates to the platform's system time. */
public object SystemClock : Clock {
    override fun nowMillis(): Long = currentTimeMillis()
}

/** Kotlin Multiplatform — expect declaration resolved per-platform. */
internal expect fun currentTimeMillis(): Long
