package com.aivo.sdk.core.port

import platform.Foundation.NSLog

public actual fun platformLogger(minLevel: LogLevel): Logger = object : Logger {
    override fun log(level: LogLevel, tag: String, message: () -> String, throwable: Throwable?) {
        if (level.ordinal < minLevel.ordinal) return
        val prefix = when (level) {
            LogLevel.VERBOSE -> "[VERBOSE]"
            LogLevel.DEBUG -> "[DEBUG]"
            LogLevel.INFO -> "[INFO]"
            LogLevel.WARN -> "[WARN]"
            LogLevel.ERROR -> "[ERROR]"
        }
        val errStr = throwable?.let { " - ${it.message}" } ?: ""
        NSLog("$prefix [$tag] ${message()}$errStr")
    }
}
