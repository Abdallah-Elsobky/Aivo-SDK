package com.aivo.sdk.runtime.tool

import com.aivo.sdk.core.model.ToolCall
import com.aivo.sdk.core.model.ToolResult
import com.aivo.sdk.core.model.ToolRisk
import com.aivo.sdk.core.model.ToolSpec
import com.aivo.sdk.core.port.Tool
import com.aivo.sdk.core.port.ToolContext
import com.aivo.sdk.runtime.tool.schema.JsonSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * DSL builder for tool parameters (JSON Schema object).
 */
public class ToolParametersBuilder {
    private val properties = mutableMapOf<String, JsonObject>()
    private val requiredFields = mutableListOf<String>()

    public fun string(
        name: String,
        description: String? = null,
        required: Boolean = false,
        enum: List<String> = emptyList(),
        minLength: Int? = null,
        maxLength: Int? = null,
    ) {
        properties[name] = JsonSchema.string(description, enum, minLength, maxLength)
        if (required) requiredFields.add(name)
    }

    public fun integer(
        name: String,
        description: String? = null,
        required: Boolean = false,
        minimum: Long? = null,
        maximum: Long? = null,
    ) {
        properties[name] = JsonSchema.integer(description, minimum, maximum)
        if (required) requiredFields.add(name)
    }

    public fun number(
        name: String,
        description: String? = null,
        required: Boolean = false,
        minimum: Double? = null,
        maximum: Double? = null,
    ) {
        properties[name] = JsonSchema.number(description, minimum, maximum)
        if (required) requiredFields.add(name)
    }

    public fun boolean(
        name: String,
        description: String? = null,
        required: Boolean = false,
    ) {
        properties[name] = JsonSchema.boolean(description)
        if (required) requiredFields.add(name)
    }

    public fun array(
        name: String,
        items: JsonObject,
        description: String? = null,
        required: Boolean = false,
    ) {
        properties[name] = JsonSchema.array(items, description)
        if (required) requiredFields.add(name)
    }

    public fun raw(name: String, schema: JsonObject, required: Boolean = false) {
        properties[name] = schema
        if (required) requiredFields.add(name)
    }

    public fun build(): JsonObject = JsonSchema.obj(
        properties = properties,
        required = requiredFields,
    )
}

/**
 * Parameter types supported by the ergonomic [ToolBuilder.param] DSL.
 */
public enum class ParamType {
    String,
    Int,
    Long,
    Double,
    Boolean,
    Array,
    Object,
}

/**
 * Ergonomic wrapper around [ToolCall] and [ToolContext] providing typed argument accessors.
 */
public class ToolArgs(
    public val call: ToolCall,
    public val context: ToolContext,
) {
    public val arguments: JsonObject get() = call.arguments

    public fun string(name: String): String =
        arguments[name]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("Missing required parameter '$name'")

    public fun stringOrNull(name: String): String? =
        arguments[name]?.jsonPrimitive?.contentOrNull

    public fun int(name: String): Int =
        arguments[name]?.jsonPrimitive?.intOrNull
            ?: throw IllegalArgumentException("Missing required integer parameter '$name'")

    public fun intOrNull(name: String): Int? =
        arguments[name]?.jsonPrimitive?.intOrNull

    public fun longOrNull(name: String): Long? =
        arguments[name]?.jsonPrimitive?.longOrNull

    public fun doubleOrNull(name: String): Double? =
        arguments[name]?.jsonPrimitive?.doubleOrNull

    public fun bool(name: String): Boolean =
        arguments[name]?.jsonPrimitive?.booleanOrNull
            ?: throw IllegalArgumentException("Missing required boolean parameter '$name'")

    public fun boolOrNull(name: String): Boolean? =
        arguments[name]?.jsonPrimitive?.booleanOrNull
}

/**
 * DSL builder for creating a [Tool].
 */
public class ToolBuilder(
    public val name: String,
    public var description: String = "",
) {
    public var risk: ToolRisk = ToolRisk.LOW
    public var requiresConfirmation: Boolean = false
    private var parametersSchema: JsonObject = buildJsonObject { }
    private val paramsBuilder = ToolParametersBuilder()
    private var hasDirectParams = false
    private var executionBlock: (suspend (call: ToolCall, context: ToolContext) -> ToolResult)? = null

    public fun description(desc: String): ToolBuilder = apply { this.description = desc }
    public fun risk(risk: ToolRisk): ToolBuilder = apply { this.risk = risk }
    public fun requiresConfirmation(requires: Boolean): ToolBuilder = apply { this.requiresConfirmation = requires }

    /**
     * Declares a typed parameter for the tool using the ergonomic DSL.
     */
    public fun param(
        name: String,
        description: String? = null,
        type: ParamType = ParamType.String,
        required: Boolean = true,
        enum: List<String> = emptyList(),
    ): ToolBuilder = apply {
        hasDirectParams = true
        when (type) {
            ParamType.String -> paramsBuilder.string(name, description, required, enum)
            ParamType.Int -> paramsBuilder.integer(name, description, required)
            ParamType.Long -> paramsBuilder.integer(name, description, required)
            ParamType.Double -> paramsBuilder.number(name, description, required)
            ParamType.Boolean -> paramsBuilder.boolean(name, description, required)
            ParamType.Array -> paramsBuilder.array(name, JsonSchema.string(), description, required)
            ParamType.Object -> paramsBuilder.raw(name, JsonSchema.obj(), required)
        }
    }

    public fun parameters(schema: JsonObject): ToolBuilder = apply {
        this.parametersSchema = schema
    }

    public fun parameters(block: ToolParametersBuilder.() -> Unit): ToolBuilder = apply {
        this.parametersSchema = ToolParametersBuilder().apply(block).build()
    }

    public fun execute(block: suspend (call: ToolCall, context: ToolContext) -> ToolResult): ToolBuilder = apply {
        this.executionBlock = block
    }

    /**
     * Ergonomic execute overload that receives [ToolArgs] and automatically wraps the returned value
     * or any thrown exception into a safe [ToolResult].
     */
    @kotlin.jvm.JvmName("executeWithArgs")
    public fun execute(block: suspend (args: ToolArgs) -> Any?): ToolBuilder = apply {
        this.executionBlock = { call, context ->
            try {
                val args = ToolArgs(call, context)
                val result = block(args)
                when (result) {
                    is ToolResult -> result
                    null -> ToolResult.Success("null")
                    is String -> ToolResult.Success(result)
                    else -> ToolResult.Success(result.toString())
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                ToolResult.Failure(e.message ?: "Tool execution failed")
            }
        }
    }

    public fun build(): Tool {
        val exec = executionBlock ?: throw IllegalStateException("Tool '$name' must define an execute block.")
        val schema = if (hasDirectParams) paramsBuilder.build() else parametersSchema
        val spec = ToolSpec(
            name = name,
            description = description,
            parameters = schema,
            risk = risk,
            requiresConfirmation = requiresConfirmation,
        )
        return object : Tool {
            override val spec: ToolSpec = spec
            override suspend fun execute(call: ToolCall, context: ToolContext): ToolResult = exec(call, context)
        }
    }
}

/**
 * Creates a [Tool] using the DSL builder.
 */
public fun tool(
    name: String,
    description: String = "",
    block: ToolBuilder.() -> Unit,
): Tool {
    return ToolBuilder(name, description).apply(block).build()
}

