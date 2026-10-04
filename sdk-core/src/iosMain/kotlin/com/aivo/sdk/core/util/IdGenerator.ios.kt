package com.aivo.sdk.core.util

import platform.Foundation.NSUUID

internal actual fun generateUuid(): String =
    NSUUID().UUIDString().lowercase()
