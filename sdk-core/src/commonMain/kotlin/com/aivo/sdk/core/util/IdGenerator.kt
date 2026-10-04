package com.aivo.sdk.core.util

/** Generates unique string identifiers. Inject to keep code testable. */
public interface IdGenerator {
    /** Returns the next unique identifier string. */
    public fun next(): String
}

/** Production ID generator using UUID v4. */
public object UuidIdGenerator : IdGenerator {
    override fun next(): String = generateUuid()
}

/** Kotlin Multiplatform — expect declaration resolved per-platform. */
internal expect fun generateUuid(): String
