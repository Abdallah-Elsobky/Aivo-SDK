package com.aivo.sdk

// Re-export core models and catalogs for convenient top-level usage
public typealias AgentRole = com.aivo.sdk.core.model.AgentRole
public typealias AgentRef = com.aivo.sdk.core.model.AgentRef
public typealias PromptSection = com.aivo.sdk.core.model.PromptSection
public typealias StylePreset = com.aivo.sdk.core.model.StylePreset
public typealias SafetyRule = com.aivo.sdk.core.model.SafetyRule
public typealias OutputFormat = com.aivo.sdk.core.model.OutputFormat
public typealias StateKey<T> = com.aivo.sdk.core.model.StateKey<T>
public typealias RunState = com.aivo.sdk.core.model.RunState
public typealias Toolbox = com.aivo.sdk.core.model.Toolbox

// Re-export ports
public typealias AgentLoop = com.aivo.sdk.core.port.AgentLoop
public typealias LoopContext = com.aivo.sdk.core.port.LoopContext
public typealias LoopOutcome = com.aivo.sdk.core.port.LoopOutcome
public typealias Turn = com.aivo.sdk.core.port.Turn
public typealias ToolSelection = com.aivo.sdk.core.port.ToolSelection
public typealias PromptComposer = com.aivo.sdk.core.port.PromptComposer

// Re-export tool DSL
public typealias ParamType = com.aivo.sdk.runtime.tool.ParamType
public typealias ToolArgs = com.aivo.sdk.runtime.tool.ToolArgs

// Re-export prebuilt loops
public typealias ToolCallingLoop = com.aivo.sdk.runtime.agent.ToolCallingLoop
public typealias SingleTurnLoop = com.aivo.sdk.runtime.agent.SingleTurnLoop
public typealias SequenceLoop = com.aivo.sdk.runtime.agent.SequenceLoop
public typealias RouterLoop = com.aivo.sdk.runtime.agent.RouterLoop
public typealias ParallelLoop = com.aivo.sdk.runtime.agent.ParallelLoop
public typealias WorkflowScope = com.aivo.sdk.runtime.agent.WorkflowScope

// Forward top-level DSL functions
public fun tool(
    name: String,
    description: String = "",
    block: com.aivo.sdk.runtime.tool.ToolBuilder.() -> Unit,
): com.aivo.sdk.core.port.Tool = com.aivo.sdk.runtime.tool.tool(name, description, block)

public fun toolbox(
    name: String,
    vararg tools: com.aivo.sdk.core.port.Tool,
): Toolbox = com.aivo.sdk.core.model.toolbox(name, *tools)

public fun agent(
    id: String,
    block: com.aivo.sdk.runtime.agent.AgentDefinitionBuilder.() -> Unit,
): com.aivo.sdk.runtime.agent.AgentDefinition = com.aivo.sdk.runtime.agent.agent(id, block)

public fun supervisor(
    id: String,
    block: com.aivo.sdk.runtime.agent.AgentDefinitionBuilder.() -> Unit,
): com.aivo.sdk.runtime.agent.AgentDefinition = com.aivo.sdk.runtime.agent.supervisor(id, block)

public fun workflow(
    vararg members: com.aivo.sdk.runtime.agent.AgentDefinition,
    block: suspend WorkflowScope.() -> LoopOutcome,
): AgentLoop = com.aivo.sdk.runtime.agent.workflow(*members, block = block)
