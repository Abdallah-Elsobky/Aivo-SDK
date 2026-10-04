package com.aivo.sdk.core.port

import android.util.Log

public actual fun platformLogger(minLevel: LogLevel): Logger = object : Logger {
    override fun log(level: LogLevel, tag: String, message: () -> String, throwable: Throwable?) {
        if (level.ordinal < minLevel.ordinal) return
        val msg = message()
        when (level) {
            LogLevel.VERBOSE -> Log.v(tag, msg, throwable)
            LogLevel.DEBUG -> Log.d(tag, msg, throwable)
            LogLevel.INFO -> Log.i(tag, msg, throwable)
            LogLevel.WARN -> Log.w(tag, msg, throwable)
            LogLevel.ERROR -> Log.e(tag, msg, throwable)
        }
    }
}
