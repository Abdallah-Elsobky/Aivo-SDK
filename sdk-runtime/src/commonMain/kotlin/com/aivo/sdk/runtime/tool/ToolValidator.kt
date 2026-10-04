package com.aivo.sdk.runtime.tool

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Result of validating tool arguments against a JSON Schema.
 */
public sealed interface ValidationResult {
    public object Valid : ValidationResult
    public data class Invalid(val errors: List<String>) : ValidationResult {
        public fun errorMessage(): String = errors.joinToString("; ")
    }
}

/**
 * Validates a [JsonObject] against a subset of JSON Schema Draft-07:
 * - `type` (object, string, integer, number, boolean, array, null)
 * - `required` fields
 * - `properties`
 * - `enum`
 * - `items`
 * - `minimum` / `maximum`
 * - `minLength` / `maxLength`
 */
public object ToolValidator {

    public fun validate(schema: JsonObject, value: JsonElement, path: String = "$"): ValidationResult {
        val errors = mutableListOf<String>()
        validateInternal(schema, value, path, errors)
        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }

    private fun validateInternal(
        schema: JsonObject,
        value: JsonElement,
        path: String,
        errors: MutableList<String>,
    ) {
        val type = schema["type"]?.jsonPrimitive?.content

        if (type != null) {
            when (type) {
                "object" -> {
                    if (value !is JsonObject) {
                        errors.add("$path: expected object, got ${elementTypeName(value)}")
                        return
                    }
                    val required = schema["required"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                    for (req in required) {
                        if (!value.containsKey(req)) {
                            errors.add("$path: missing required field '$req'")
                        }
                    }
                    val properties = schema["properties"]?.jsonObject ?: emptyMap()
                    for ((propName, propSchema) in properties) {
                        val propValue = value[propName]
                        if (propValue != null && propSchema is JsonObject) {
                            validateInternal(propSchema, propValue, "$path.$propName", errors)
                        }
                    }
                }
                "string" -> {
                    if (value !is JsonPrimitive || value.isString.not()) {
                        errors.add("$path: expected string, got ${elementTypeName(value)}")
                        return
                    }
                    val str = value.content
                    schema["minLength"]?.jsonPrimitive?.intOrNull?.let { minLen ->
                        if (str.length < minLen) errors.add("$path: length ${str.length} < minLength $minLen")
                    }
                    schema["maxLength"]?.jsonPrimitive?.intOrNull?.let { maxLen ->
                        if (str.length > maxLen) errors.add("$path: length ${str.length} > maxLength $maxLen")
                    }
                    schema["enum"]?.jsonArray?.let { enumArr ->
                        val allowed = enumArr.map { it.jsonPrimitive.content }
                        if (str !in allowed) {
                            errors.add("$path: value '$str' is not in enum [${allowed.joinToString(", ")}]")
                        }
                    }
                }
                "integer" -> {
                    val num = if (value is JsonPrimitive) value.longOrNull else null
                    if (num == null) {
                        errors.add("$path: expected integer, got ${elementTypeName(value)}")
                        return
                    }
                    schema["minimum"]?.jsonPrimitive?.longOrNull?.let { min ->
                        if (num < min) errors.add("$path: value $num < minimum $min")
                    }
                    schema["maximum"]?.jsonPrimitive?.longOrNull?.let { max ->
                        if (num > max) errors.add("$path: value $num > maximum $max")
                    }
                }
                "number" -> {
                    val num = if (value is JsonPrimitive) value.doubleOrNull else null
                    if (num == null) {
                        errors.add("$path: expected number, got ${elementTypeName(value)}")
                        return
                    }
                    schema["minimum"]?.jsonPrimitive?.doubleOrNull?.let { min ->
                        if (num < min) errors.add("$path: value $num < minimum $min")
                    }
                    schema["maximum"]?.jsonPrimitive?.doubleOrNull?.let { max ->
                        if (num > max) errors.add("$path: value $num > maximum $max")
                    }
                }
                "boolean" -> {
                    if (value !is JsonPrimitive || value.booleanOrNull == null) {
                        errors.add("$path: expected boolean, got ${elementTypeName(value)}")
                    }
                }
                "array" -> {
                    if (value !is JsonArray) {
                        errors.add("$path: expected array, got ${elementTypeName(value)}")
                        return
                    }
                    val itemSchema = schema["items"]?.jsonObject
                    if (itemSchema != null) {
                        value.forEachIndexed { index, item ->
                            validateInternal(itemSchema, item, "$path[$index]", errors)
                        }
                    }
                }
            }
        }
    }

    private fun elementTypeName(element: JsonElement): String = when (element) {
        is JsonNull -> "null"
        is JsonArray -> "array"
        is JsonObject -> "object"
        is JsonPrimitive -> if (element.isString) "string" else "number/boolean"
    }
}
