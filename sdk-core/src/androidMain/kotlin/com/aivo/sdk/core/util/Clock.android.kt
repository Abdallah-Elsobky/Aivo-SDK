package com.aivo.sdk.core.util

/** Platform actual for Android — same JVM implementation. */
internal actual fun currentTimeMillis(): Long = System.currentTimeMillis()
