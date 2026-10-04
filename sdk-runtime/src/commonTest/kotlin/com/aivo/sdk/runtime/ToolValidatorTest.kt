package com.aivo.sdk.runtime

import com.aivo.sdk.runtime.tool.ToolValidator
import com.aivo.sdk.runtime.tool.ValidationResult
import com.aivo.sdk.runtime.tool.schema.JsonSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertTrue

class ToolValidatorTest {

    @Test
    fun validate_valid_arguments_passes() {
        val schema = JsonSchema.obj(
            properties = mapOf(
                "accountId" to JsonSchema.string(),
                "amount" to JsonSchema.number(minimum = 0.0),
            ),
            required = listOf("accountId", "amount"),
        )

        val validArgs = buildJsonObject {
            put("accountId", "ACC-101")
            put("amount", 50.0)
        }

        val result = ToolValidator.validate(schema, validArgs)
        assertTrue(result is ValidationResult.Valid)
    }

    @Test
    fun validate_missing_required_property_fails() {
        val schema = JsonSchema.obj(
            properties = mapOf(
                "accountId" to JsonSchema.string(),
            ),
            required = listOf("accountId"),
        )

        val invalidArgs = buildJsonObject { }

        val result = ToolValidator.validate(schema, invalidArgs)
        assertTrue(result is ValidationResult.Invalid)
        assertTrue(result.errorMessage().contains("missing required property 'accountId'"))
    }

    @Test
    fun validate_invalid_type_fails() {
        val schema = JsonSchema.obj(
            properties = mapOf(
                "amount" to JsonSchema.number(),
            ),
        )

        val invalidArgs = buildJsonObject {
            put("amount", "not-a-number")
        }

        val result = ToolValidator.validate(schema, invalidArgs)
        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun validate_enum_constraint() {
        val schema = JsonSchema.obj(
            properties = mapOf(
                "currency" to JsonSchema.string(enum = listOf("USD", "EUR", "EGP")),
            ),
        )

        val invalidArgs = buildJsonObject {
            put("currency", "XYZ")
        }

        val result = ToolValidator.validate(schema, invalidArgs)
        assertTrue(result is ValidationResult.Invalid)
    }
}
