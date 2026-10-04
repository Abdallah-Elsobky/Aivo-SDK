package com.aivo.sdk.core.util

/** Platform actual for JVM/Android. */
internal actual fun currentTimeMillis(): Long = System.currentTimeMillis()
