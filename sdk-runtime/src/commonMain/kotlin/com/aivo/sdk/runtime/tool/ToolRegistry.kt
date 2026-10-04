package com.aivo.sdk.runtime.tool

import com.aivo.sdk.core.port.Tool

/**
 * Registry of executable [Tool] instances.
 */
public class ToolRegistry(
    tools: Collection<Tool> = emptyList(),
) {
    private val toolMap: MutableMap<String, Tool> = mutableMapOf()

    init {
        for (tool in tools) {
            register(tool)
        }
    }

    /**
     * Registers a [Tool].
     *
     * @throws IllegalArgumentException if tool name is invalid or already registered.
     */
    public fun register(tool: Tool) {
        val name = tool.spec.name
        require(NAME_REGEX.matches(name)) {
            "Tool name '$name' is invalid. Must match regex: ^[a-zA-Z0-9_-]{1,64}$"
        }
        toolMap[name] = tool
    }

    /**
     * Finds a tool by [name], or returns `null` if not found.
     */
    public fun get(name: String): Tool? = toolMap[name]

    /**
     * Returns all registered tools.
     */
    public fun all(): List<Tool> = toolMap.values.toList()

    /**
     * Returns true if a tool with [name] is registered.
     */
    public fun contains(name: String): Boolean = toolMap.containsKey(name)

    public companion object {
        private val NAME_REGEX = Regex("^[a-zA-Z0-9_-]{1,64}$")
    }
}
