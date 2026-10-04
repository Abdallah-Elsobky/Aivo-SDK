package com.aivo.sdk.agent.config

import java.io.File

public actual fun defaultResourceReader(): ResourceReader = AndroidResourceReader

internal object AndroidResourceReader : ResourceReader {
    override suspend fun readText(path: String): String {
        val file = File(path)
        if (file.exists() && file.isFile) {
            return file.readText()
        }

        val normalizedPath = if (path.startsWith("/")) path.substring(1) else path
        val stream = Thread.currentThread().contextClassLoader?.getResourceAsStream(normalizedPath)
            ?: javaClass.classLoader?.getResourceAsStream(normalizedPath)
            ?: throw IllegalArgumentException("Android resource not found at '$path'")

        return stream.bufferedReader().use { it.readText() }
    }
}
