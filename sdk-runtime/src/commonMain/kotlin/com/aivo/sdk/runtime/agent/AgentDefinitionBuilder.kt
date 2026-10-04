package com.aivo.sdk.runtime.agent

import com.aivo.sdk.core.model.AgentRole
import com.aivo.sdk.core.model.GenerationOptions
import com.aivo.sdk.core.model.ModelRef
import com.aivo.sdk.core.model.OutputFormat
import com.aivo.sdk.core.model.ProviderModel
import com.aivo.sdk.core.model.SafetyRule
import com.aivo.sdk.core.model.StateKey
import com.aivo.sdk.core.model.StylePreset
import com.aivo.sdk.core.model.Toolbox
import com.aivo.sdk.core.port.AgentLoop
import com.aivo.sdk.core.port.PromptComposer
import com.aivo.sdk.core.port.Tool
import com.aivo.sdk.runtime.prompt.SectionedPromptComposer
import kotlin.jvm.JvmName

/**
 * Fluent DSL builder for creating [AgentDefinition] programmatically.
 */
public class AgentDefinitionBuilder(public val id: String) {
    public var name: String = id
    public var description: String? = null
    public var model: ModelRef? = null
    public var systemPrompt: String = ""
    public var role: AgentRole? = null
    public var goal: String? = null
    public val responsibilities: MutableList<String> = mutableListOf()
    public val instructions: MutableList<String> = mutableListOf()
    public val rules: MutableList<String> = mutableListOf()
    public var style: String? = null
    public var outputFormat: OutputFormat? = null
    public var customContext: String? = null
    public var saveResultAs: String? = null
    public var loop: AgentLoop? = null
    public var promptComposer: PromptComposer = SectionedPromptComposer.Default

    public val tools: MutableList<String> = mutableListOf()
    public val toolInstances: MutableList<Tool> = mutableListOf()
    public val delegates: MutableList<String> = mutableListOf()
    public val managedAgents: MutableList<AgentDefinition> = mutableListOf()

    public var limits: AgentLimits = AgentLimits()
    public var options: GenerationOptions = GenerationOptions()

    public fun name(name: String): AgentDefinitionBuilder = apply { this.name = name }
    public fun description(desc: String): AgentDefinitionBuilder = apply { this.description = desc }
    public fun model(modelRef: String): AgentDefinitionBuilder = apply { this.model = ModelRef.parse(modelRef) }
    public fun model(modelRef: ModelRef): AgentDefinitionBuilder = apply { this.model = modelRef }
    public fun model(providerModel: ProviderModel): AgentDefinitionBuilder = apply { this.model = providerModel.toModelRef() }

    public fun role(role: AgentRole): AgentDefinitionBuilder = apply { this.role = role }
    public fun role(roleId: String, title: String): AgentDefinitionBuilder = apply {
        this.role = AgentRole.custom(roleId, title)
    }

    public fun goal(goal: String): AgentDefinitionBuilder = apply { this.goal = goal }

    public fun responsibility(resp: String): AgentDefinitionBuilder = apply { this.responsibilities.add(resp) }
    public fun responsibilities(vararg items: String): AgentDefinitionBuilder = apply { this.responsibilities.addAll(items) }
    public fun responsibilities(items: Collection<String>): AgentDefinitionBuilder = apply { this.responsibilities.addAll(items) }

    public fun systemPrompt(prompt: String): AgentDefinitionBuilder = apply { this.systemPrompt = prompt }

    public fun instruction(inst: String): AgentDefinitionBuilder = apply { this.instructions.add(inst) }
    public fun instructions(vararg items: String): AgentDefinitionBuilder = apply { this.instructions.addAll(items) }
    public fun instructions(items: Collection<String>): AgentDefinitionBuilder = apply { this.instructions.addAll(items) }

    public fun rule(rule: SafetyRule): AgentDefinitionBuilder = apply { this.rules.add(rule.directive) }
    public fun rule(rule: String): AgentDefinitionBuilder = apply { this.rules.add(rule) }
    public fun rules(vararg items: SafetyRule): AgentDefinitionBuilder = apply {
        for (r in items) this.rules.add(r.directive)
    }
    public fun rules(vararg items: String): AgentDefinitionBuilder = apply { this.rules.addAll(items) }
    public fun rules(items: Collection<String>): AgentDefinitionBuilder = apply { this.rules.addAll(items) }

    public fun style(preset: StylePreset): AgentDefinitionBuilder = apply { this.style = preset.directive }
    public fun style(style: String): AgentDefinitionBuilder = apply { this.style = style }

    public fun outputFormat(format: OutputFormat): AgentDefinitionBuilder = apply { this.outputFormat = format }
    public fun outputFormat(description: String): AgentDefinitionBuilder = apply {
        this.outputFormat = OutputFormat.Custom(description)
    }

    public fun customContext(context: String): AgentDefinitionBuilder = apply { this.customContext = context }

    public fun saveResultAs(key: StateKey<*>): AgentDefinitionBuilder = apply { this.saveResultAs = key.name }
    public fun saveResultAs(keyName: String): AgentDefinitionBuilder = apply { this.saveResultAs = keyName }

    public fun loop(loop: AgentLoop): AgentDefinitionBuilder = apply { this.loop = loop }
    public fun promptComposer(composer: PromptComposer): AgentDefinitionBuilder = apply { this.promptComposer = composer }

    public fun tool(name: String): AgentDefinitionBuilder = apply { this.tools.add(name) }
    public fun tools(vararg toolNames: String): AgentDefinitionBuilder = apply { this.tools.addAll(toolNames) }
    public fun tools(toolNames: Collection<String>): AgentDefinitionBuilder = apply { this.tools.addAll(toolNames) }

    public fun tools(vararg toolObjects: Tool): AgentDefinitionBuilder = apply {
        for (t in toolObjects) {
            this.toolInstances.add(t)
            this.tools.add(t.spec.name)
        }
    }

    public fun tools(toolbox: Toolbox): AgentDefinitionBuilder = apply {
        for (t in toolbox.tools) {
            this.toolInstances.add(t)
            this.tools.add(t.spec.name)
        }
    }

    public fun delegate(agentId: String): AgentDefinitionBuilder = apply { this.delegates.add(agentId) }
    public fun delegate(agent: AgentDefinition): AgentDefinitionBuilder = apply {
        this.managedAgents.add(agent)
        this.delegates.add(agent.id)
    }
    public fun delegates(vararg agentIds: String): AgentDefinitionBuilder = apply { this.delegates.addAll(agentIds) }
    public fun delegates(agentIds: Collection<String>): AgentDefinitionBuilder = apply { this.delegates.addAll(agentIds) }

    public fun delegates(vararg agents: AgentDefinition): AgentDefinitionBuilder = apply {
        for (a in agents) {
            this.managedAgents.add(a)
            this.delegates.add(a.id)
        }
    }

    @JvmName("delegatesAgents")
    public fun delegates(agents: Collection<AgentDefinition>): AgentDefinitionBuilder = apply {
        for (a in agents) {
            this.managedAgents.add(a)
            this.delegates.add(a.id)
        }
    }

    /**
     * Declares member agents managed by this supervisor agent.
     */
    public fun manages(vararg agents: AgentDefinition): AgentDefinitionBuilder = delegates(*agents)
    public fun manages(agents: Collection<AgentDefinition>): AgentDefinitionBuilder = delegates(agents)

    public fun limits(limits: AgentLimits): AgentDefinitionBuilder = apply { this.limits = limits }
    public fun options(options: GenerationOptions): AgentDefinitionBuilder = apply { this.options = options }

    public fun build(): AgentDefinition {
        require(id.isNotBlank()) { "Agent ID must not be blank" }
        return AgentDefinition(
            id = id,
            name = name,
            description = description,
            model = model,
            systemPrompt = systemPrompt,
            instructions = instructions.toList(),
            tools = tools.distinct(),
            subAgents = delegates.distinct(),
            limits = limits,
            options = options,
            role = role,
            goal = goal,
            responsibilities = responsibilities.toList(),
            rules = rules.toList(),
            style = style,
            outputFormat = outputFormat,
            customContext = customContext,
            saveResultAs = saveResultAs,
            loop = loop,
            promptComposer = promptComposer,
            toolInstances = toolInstances.distinctBy { it.spec.name },
            managedAgentDefinitions = managedAgents.toList(),
        )
    }
}

/**
 * Top-level DSL function to define an [AgentDefinition].
 */
public fun agent(id: String, block: AgentDefinitionBuilder.() -> Unit): AgentDefinition {
    return AgentDefinitionBuilder(id).apply(block).build()
}

/**
 * Top-level DSL function to define a supervisor [AgentDefinition].
 * Sets [AgentRole.SUPERVISOR] as default role.
 */
public fun supervisor(id: String, block: AgentDefinitionBuilder.() -> Unit): AgentDefinition {
    return AgentDefinitionBuilder(id).apply {
        role = AgentRole.SUPERVISOR
        block()
    }.build()
}
