@file:OptIn(ExperimentalForeignApi::class)

package com.aivo.sdk.agent.config

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile

public actual fun defaultResourceReader(): ResourceReader = IosResourceReader

internal object IosResourceReader : ResourceReader {
    override suspend fun readText(path: String): String {
        val bundle = NSBundle.mainBundle
        val resourcePath = if (path.startsWith("/")) path else bundle.pathForResource(path, ofType = null)
            ?: throw IllegalArgumentException("iOS bundle resource not found at '$path'")

        val content = NSString.stringWithContentsOfFile(
            path = resourcePath,
            encoding = NSUTF8StringEncoding,
            error = null,
        ) ?: throw IllegalArgumentException("Failed to read iOS bundle resource at '$path'")

        return content as String
    }
}
