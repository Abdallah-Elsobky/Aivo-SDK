package com.aivo.sdk.core.util

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

/** iOS actual for currentTimeMillis. */
internal actual fun currentTimeMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1000).toLong()
