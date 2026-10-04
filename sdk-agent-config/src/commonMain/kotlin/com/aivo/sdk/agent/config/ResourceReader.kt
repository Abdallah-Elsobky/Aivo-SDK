package com.aivo.sdk.agent.config

/**
 * Reads agent definition files and system prompt resources across platforms.
 */
public interface ResourceReader {
    /**
     * Reads text content from [path].
     *
     * @throws IllegalArgumentException or java.io.IOException if file is not found.
     */
    public suspend fun readText(path: String): String
}

/**
 * Platform-provided default [ResourceReader].
 */
public expect fun defaultResourceReader(): ResourceReader
