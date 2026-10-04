package com.aivo.sdk.core.model

import com.aivo.sdk.core.port.Tool

/**
 * A named collection of tools that can be attached to agents as a single cohesive unit.
 */
public data class Toolbox(
    public val name: String,
    public val tools: List<Tool>,
) {
    init {
        require(name.isNotBlank()) { "Toolbox name must not be blank" }
    }

    public operator fun plus(other: Toolbox): Toolbox {
        return Toolbox("${name}_${other.name}", (tools + other.tools).distinctBy { it.spec.name })
    }

    public operator fun plus(tool: Tool): Toolbox {
        return Toolbox(name, (tools + tool).distinctBy { it.spec.name })
    }

    public companion object {
        public fun of(name: String, vararg tools: Tool): Toolbox = Toolbox(name, tools.toList())
    }
}

/**
 * Top-level factory for creating a [Toolbox].
 */
public fun toolbox(name: String, vararg tools: Tool): Toolbox = Toolbox(name, tools.toList())

public fun toolbox(name: String, tools: Collection<Tool>): Toolbox = Toolbox(name, tools.toList())
