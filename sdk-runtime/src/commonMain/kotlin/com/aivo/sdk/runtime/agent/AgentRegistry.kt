package com.aivo.sdk.runtime.agent

/**
 * Registry holding all defined agents.
 */
public class AgentRegistry(
    definitions: Collection<AgentDefinition> = emptyList(),
) {
    private val agentMap: MutableMap<String, AgentDefinition> = mutableMapOf()

    init {
        for (def in definitions) {
            register(def)
        }
    }

    /**
     * Registers an [AgentDefinition].
     *
     * @throws IllegalArgumentException if an agent with the same ID is already registered.
     */
    public fun register(definition: AgentDefinition) {
        require(definition.id.isNotBlank()) { "Agent ID must not be blank" }
        require(!agentMap.containsKey(definition.id)) {
            "Agent with ID '${definition.id}' is already registered."
        }
        agentMap[definition.id] = definition
    }

    /**
     * Finds an agent by [id], or returns `null`.
     */
    public fun get(id: String): AgentDefinition? = agentMap[id]

    /**
     * Returns all registered agent definitions.
     */
    public fun all(): List<AgentDefinition> = agentMap.values.toList()

    /**
     * Returns true if an agent with [id] is registered.
     */
    public fun contains(id: String): Boolean = agentMap.containsKey(id)
}
