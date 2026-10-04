package com.aivo.sdk.core.port

/**
 * Returns the platform-specific default [Logger] implementation.
 *
 * - Android: Android Log (`android.util.Log`)
 * - iOS: NSLog / Console
 * - JVM: standard out (`println`)
 */
public expect fun platformLogger(minLevel: LogLevel = LogLevel.WARN): Logger
