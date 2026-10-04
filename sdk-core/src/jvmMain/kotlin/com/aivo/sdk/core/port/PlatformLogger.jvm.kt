package com.aivo.sdk.core.port

public actual fun platformLogger(minLevel: LogLevel): Logger = object : Logger {
    override fun log(level: LogLevel, tag: String, message: () -> String, throwable: Throwable?) {
        if (level.ordinal < minLevel.ordinal) return
        val prefix = when (level) {
            LogLevel.VERBOSE -> "[V]"
            LogLevel.DEBUG -> "[D]"
            LogLevel.INFO -> "[I]"
            LogLevel.WARN -> "[W]"
            LogLevel.ERROR -> "[E]"
        }
        val text = "$prefix [$tag] ${message()}"
        if (level == LogLevel.ERROR) {
            System.err.println(text)
            throwable?.printStackTrace(System.err)
        } else {
            println(text)
            throwable?.printStackTrace(System.out)
        }
    }
}
