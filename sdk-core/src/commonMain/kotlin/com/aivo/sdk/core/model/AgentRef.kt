package com.aivo.sdk.core.model

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

/**
 * Type-safe reference to an Agent identifier.
 */
@JvmInline
@Serializable
public value class AgentRef(public val id: String) {
    init {
        require(id.isNotBlank()) { "AgentRef must not be blank" }
    }

    override fun toString(): String = id
}
