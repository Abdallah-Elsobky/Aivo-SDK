package com.aivo.sdk.core.util

/**
 * A string wrapper that never exposes its value in logs, toString(), or serialization.
 * Use everywhere API keys, tokens, and passwords appear.
 */
public class SecretString(private val value: String) {
    /** Always returns "***" — never the actual value. */
    override fun toString(): String = "***"

    /** Returns the raw value. Call only inside transport/auth code. */
    public fun reveal(): String = value

    override fun equals(other: Any?): Boolean =
        other is SecretString && value == other.value

    override fun hashCode(): Int = value.hashCode()
}
