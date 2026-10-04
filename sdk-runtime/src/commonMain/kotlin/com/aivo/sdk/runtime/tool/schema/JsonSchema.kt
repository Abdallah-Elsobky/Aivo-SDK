package com.aivo.sdk.runtime.tool.schema

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * DSL and builder for creating Draft-07 compatible JSON Schemas for tools.
 */
public object JsonSchema {

    public fun obj(
        properties: Map<String, JsonObject> = emptyMap(),
        required: List<String> = emptyList(),
        description: String? = null,
    ): JsonObject = buildJsonObject {
        put("type", "object")
        description?.let { put("description", it) }
        put("properties", buildJsonObject {
            properties.forEach { (name, schema) -> put(name, schema) }
        })
        if (required.isNotEmpty()) {
            put("required", buildJsonArray {
                required.forEach { add(JsonPrimitive(it)) }
            })
        }
    }

    public fun string(
        description: String? = null,
        enum: List<String> = emptyList(),
        minLength: Int? = null,
        maxLength: Int? = null,
    ): JsonObject = buildJsonObject {
        put("type", "string")
        description?.let { put("description", it) }
        if (enum.isNotEmpty()) {
            put("enum", buildJsonArray {
                enum.forEach { add(JsonPrimitive(it)) }
            })
        }
        minLength?.let { put("minLength", it) }
        maxLength?.let { put("maxLength", it) }
    }

    public fun integer(
        description: String? = null,
        minimum: Long? = null,
        maximum: Long? = null,
    ): JsonObject = buildJsonObject {
        put("type", "integer")
        description?.let { put("description", it) }
        minimum?.let { put("minimum", it) }
        maximum?.let { put("maximum", it) }
    }

    public fun number(
        description: String? = null,
        minimum: Double? = null,
        maximum: Double? = null,
    ): JsonObject = buildJsonObject {
        put("type", "number")
        description?.let { put("description", it) }
        minimum?.let { put("minimum", it) }
        maximum?.let { put("maximum", it) }
    }

    public fun boolean(description: String? = null): JsonObject = buildJsonObject {
        put("type", "boolean")
        description?.let { put("description", it) }
    }

    public fun array(items: JsonObject, description: String? = null): JsonObject = buildJsonObject {
        put("type", "array")
        description?.let { put("description", it) }
        put("items", items)
    }
}
