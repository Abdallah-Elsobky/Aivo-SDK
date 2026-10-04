package com.aivo.sdk.agent.config

import java.io.File

public actual fun defaultResourceReader(): ResourceReader = JvmResourceReader

internal object JvmResourceReader : ResourceReader {
    override suspend fun readText(path: String): String {
        // Try file path first
        val file = File(path)
        if (file.exists() && file.isFile) {
            return file.readText()
        }

        // Try classloader resource
        val normalizedPath = if (path.startsWith("/")) path.substring(1) else path
        val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(normalizedPath)
            ?: javaClass.classLoader?.getResourceAsStream(normalizedPath)
            ?: throw IllegalArgumentException("Resource not found at path '$path' (checked file system and classpath)")

        return stream.bufferedReader().use { it.readText() }
    }
}
