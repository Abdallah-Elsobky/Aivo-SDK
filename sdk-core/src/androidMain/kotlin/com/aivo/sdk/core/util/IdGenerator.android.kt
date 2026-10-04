package com.aivo.sdk.core.util

import java.util.UUID

internal actual fun generateUuid(): String = UUID.randomUUID().toString()
