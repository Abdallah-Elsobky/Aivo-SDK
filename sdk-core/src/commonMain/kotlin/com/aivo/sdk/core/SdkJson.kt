package com.aivo.sdk.core

import kotlinx.serialization.json.Json

/**
 * The single, shared [Json] instance used across the entire SDK.
 *
 * Configuration rationale:
 * - [ignoreUnknownKeys]: providers add fields constantly; failing on new fields would break silently.
 * - [explicitNulls = false]: omit null fields from output to keep payloads compact.
 * - [encodeDefaults = false]: omit fields equal to their default value.
 * - [isLenient = false]: strict parsing — fail loudly on malformed JSON.
 *
 * Internal: not part of the public API. Providers access this via their own module-internal references.
 */
public val SdkJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = false
    isLenient = false
}
