package com.aivo.sdk.runtime.agent

import com.aivo.sdk.core.error.AgentRunException
import com.aivo.sdk.core.error.ConfigurationException
import com.aivo.sdk.core.error.DelegationCycleException
import com.aivo.sdk.core.error.MaxDelegationDepthExceededException
import com.aivo.sdk.core.error.MaxStepsExceededException
import com.aivo.sdk.core.error.TokenBudgetExceededException
import com.aivo.sdk.core.error.UnknownAgentException
import com.aivo.sdk.core.error.UnsupportedCapabilityException
import com.aivo.sdk.core.model.AgentRef
import com.aivo.sdk.core.model.ConversationId
import com.aivo.sdk.core.model.LlmRequest
import com.aivo.sdk.core.model.LlmResponse
import com.aivo.sdk.core.model.LlmStreamEvent
import com.aivo.sdk.core.model.Message
import com.aivo.sdk.core.model.ModelRef
import com.aivo.sdk.core.model.ProviderId
import com.aivo.sdk.core.model.RunState
import com.aivo.sdk.core.model.StateKey
import com.aivo.sdk.core.model.ToolCall
import com.aivo.sdk.core.model.ToolFailureKind
import com.aivo.sdk.core.model.ToolResult
import com.aivo.sdk.core.model.ToolSpec
import com.aivo.sdk.core.model.Usage
import com.aivo.sdk.core.port.LlmProvider
import com.aivo.sdk.core.port.LoopContext
import com.aivo.sdk.core.port.LoopOutcome
import com.aivo.sdk.core.port.MemoryStore
import com.aivo.sdk.core.port.NoOpLogger
import com.aivo.sdk.core.port.PromptComposeContext
import com.aivo.sdk.core.port.TeamMemberSummary
import com.aivo.sdk.core.port.Telemetry
import com.aivo.sdk.core.port.Tool
import com.aivo.sdk.core.port.ToolContext
import com.aivo.sdk.core.port.ToolSelection
import com.aivo.sdk.core.port.Turn
import com.aivo.sdk.core.util.Clock
import com.aivo.sdk.core.util.IdGenerator
import com.aivo.sdk.runtime.delegation.DelegatedAgentTool
import com.aivo.sdk.runtime.memory.context.ContextWindowStrategy
import com.aivo.sdk.runtime.prompt.PromptRenderer
import com.aivo.sdk.runtime.tool.ToolExecutor
import com.aivo.sdk.runtime.tool.ToolRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Concrete implementation of [LoopContext] enforcing safety limits, tool allow-lists,
 * and telemetry inside runtime primitives.
 */
public class DefaultLoopContext(
    public val agent: AgentDefinition,
    override val input: String,
    override val conversationId: ConversationId,
    override val runId: String,
    override val agentPath: List<String>,
    public val variables: Map<String, String>,
    override val state: RunState,
    private val providers: Map<ProviderId, LlmProvider>,
    private val defaultModel: ModelRef?,
    private val agentRegistry: AgentRegistry,
    private val toolRegistry: ToolRegistry,
    private val memoryStore: MemoryStore,
    private val contextStrategy: ContextWindowStrategy,
    private val toolExecutor: ToolExecutor,
    private val collector: FlowCollector<AgentEvent>,
    private val telemetry: Telemetry,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
    private val childRunner: suspend (
        childAgent: AgentDefinition,
        task: String,
        context: Map<String, String>,
        nextPath: List<String>,
    ) -> AgentResult,
) : LoopContext {

    override val agentId: String get() = agent.id

    private var _stepCount: Int = 0
    override val stepCount: Int get() = _stepCount

    public var accumulatedUsage: Usage? = null
        private set

    public val history: MutableList<Message> = mutableListOf()
    public val newMessagesSinceRunStart: MutableList<Message> = mutableListOf()
    public val allExecutedToolCalls: MutableList<ToolCall> = mutableListOf()

    private val provider: LlmProvider
    private val effectiveModel: ModelRef

    init {
        effectiveModel = agent.model ?: defaultModel
            ?: throw ConfigurationException(
                listOf("Provider/Model not configured for agent '${agent.id}' and no default model set.")
            )

        provider = providers[effectiveModel.provider]
            ?: throw ConfigurationException(
                listOf("Provider '${effectiveModel.provider.value}' not configured for agent '${agent.id}'")
            )
    }

    override suspend fun think(
        tools: ToolSelection,
        promptOverride: String?,
    ): Turn {
        _stepCount++
        if (_stepCount > agent.limits.maxSteps) {
            throw MaxStepsExceededException(agent.id, agent.limits.maxSteps)
        }

        collector.emit(AgentEvent.StepStarted(_stepCount, agentPath))

        val selectedHistory = contextStrategy.select(history)

        val renderedPrompt = if (promptOverride != null) {
            PromptRenderer.render(promptOverride, variables)
        } else {
            val teamSummaries = agent.delegates.mapNotNull { subId ->
                val sub = agentRegistry.get(subId)
                    ?: agent.managedAgentDefinitions.firstOrNull { it.id == subId }
                if (sub != null) {
                    TeamMemberSummary(
                        id = sub.id,
                        role = sub.role?.title ?: sub.name,
                        description = sub.description ?: "Specialist agent ${sub.id}"
                    )
                } else null
            }
            val promptContext = PromptComposeContext(
                role = agent.role,
                goal = agent.goal,
                responsibilities = agent.responsibilities,
                instructions = agent.instructions,
                rules = agent.rules,
                style = agent.style,
                teamMembers = teamSummaries,
                outputFormat = agent.outputFormat,
                customContext = agent.customContext,
                rawSystemPrompt = agent.systemPrompt,
                variables = variables,
            )
            agent.promptComposer.compose(promptContext)
        }

        val systemMsg = Message.System(renderedPrompt)
        val messagesForRequest = buildList {
            add(systemMsg)
            addAll(selectedHistory)
        }

        val availableTools = resolveTools(tools)
        val toolSpecs = availableTools.map { it.spec }

        if (toolSpecs.isNotEmpty() && !provider.capabilities.toolCalling) {
            throw UnsupportedCapabilityException("toolCalling", provider.id)
        }

        val extras = buildJsonObject {
            put("_runId", runId)
            put("_conversationId", conversationId.value)
            put("_agentId", agent.id)
            put("_stepIndex", _stepCount)
        }

        val request = LlmRequest(
            model = effectiveModel.model,
            messages = messagesForRequest,
            tools = toolSpecs,
            options = agent.options,
            extras = extras,
        )

        var stepResponse: LlmResponse? = null
        val toolCallIdsByIndex = mutableMapOf<Int, String>()

        provider.stream(request).collect { event ->
            when (event) {
                is LlmStreamEvent.TextDelta -> {
                    collector.emit(AgentEvent.TextDelta(event.text, agentPath))
                }
                is LlmStreamEvent.ReasoningDelta -> {
                    collector.emit(AgentEvent.ReasoningDelta(event.text, agentPath))
                }
                is LlmStreamEvent.ToolCallStarted -> {
                    toolCallIdsByIndex[event.index] = event.id
                    collector.emit(AgentEvent.ToolCallStarted(event.id, event.name, agentPath))
                }
                is LlmStreamEvent.ToolCallArgumentsDelta -> {
                    val callId = toolCallIdsByIndex[event.index] ?: ""
                    collector.emit(AgentEvent.ToolCallArgumentsDelta(callId, event.jsonFragment, agentPath))
                }
                is LlmStreamEvent.Completed -> {
                    stepResponse = event.response
                }
            }
        }

        val response = stepResponse
            ?: throw AgentRunException("Provider stream finished without a Completed event.")

        val assistantMsg = response.message
        history.add(assistantMsg)
        newMessagesSinceRunStart.add(assistantMsg)
        accumulatedUsage = mergeUsage(accumulatedUsage, response.usage)

        agent.limits.maxTokens?.let { maxTokens ->
            val currentTokens = (accumulatedUsage?.inputTokens ?: 0) + (accumulatedUsage?.outputTokens ?: 0)
            if (currentTokens > maxTokens) {
                throw TokenBudgetExceededException(maxTokens)
            }
        }

        collector.emit(AgentEvent.StepCompleted(_stepCount, agentPath))

        return if (assistantMsg.toolCalls.isEmpty()) {
            Turn.Text(assistantMsg.text, response.usage)
        } else {
            Turn.Call(assistantMsg.toolCalls, response.usage)
        }
    }

    override suspend fun act(call: ToolCall): ToolResult {
        val allTools = resolveAllAuthorizedTools()
        val targetTool = allTools.firstOrNull { it.spec.name == call.name }

        val toolContext = ToolContext(
            conversationId = conversationId,
            agentId = agent.id,
            runId = runId,
            callId = call.id,
            variables = variables,
            logger = NoOpLogger,
        )

        val result = if (targetTool == null) {
            ToolResult.Failure(
                ToolFailureKind.DENIED,
                "Tool '${call.name}' is not authorized for agent '${agent.id}'."
            )
        } else {
            toolExecutor.execute(targetTool, call, toolContext)
        }

        allExecutedToolCalls.add(call)
        collector.emit(AgentEvent.ToolCallExecuted(call.id, call.name, result, agentPath))

        val toolMsg = Message.Tool(
            callId = call.id,
            toolName = call.name,
            result = result,
        )
        history.add(toolMsg)
        newMessagesSinceRunStart.add(toolMsg)

        return result
    }

    override suspend fun act(calls: List<ToolCall>): List<ToolResult> {
        val results = mutableListOf<ToolResult>()
        for (c in calls) {
            results.add(act(c))
        }
        return results
    }

    override suspend fun delegate(
        target: AgentRef,
        task: String,
        context: Map<String, String>,
    ): String {
        val childId = target.id

        if (agentPath.contains(childId)) {
            throw DelegationCycleException(agentPath + childId)
        }
        if (agentPath.size >= agent.limits.maxDelegationDepth) {
            throw MaxDelegationDepthExceededException(agent.limits.maxDelegationDepth)
        }

        val childAgent = agentRegistry.get(childId)
            ?: agent.managedAgentDefinitions.firstOrNull { it.id == childId }
            ?: throw UnknownAgentException(childId)

        val nextPath = agentPath + childId
        collector.emit(AgentEvent.AgentEntered(childId, nextPath))

        val childResult = childRunner(childAgent, task, context, nextPath)
        accumulatedUsage = mergeUsage(accumulatedUsage, childResult.usage)

        childAgent.saveResultAs?.let { keyName ->
            state[StateKey.string(keyName)] = childResult.text
        }

        return childResult.text
    }

    private val parallelMutex = Mutex()

    override suspend fun <T> parallel(branches: List<suspend (LoopContext) -> T>): List<T> = coroutineScope {
        branches.mapIndexed { index, branch ->
            async {
                val childContext = DefaultLoopContext(
                    agent = agent,
                    input = input,
                    conversationId = conversationId,
                    runId = "${runId}_branch$index",
                    agentPath = agentPath,
                    variables = variables,
                    state = state,
                    providers = providers,
                    defaultModel = defaultModel,
                    agentRegistry = agentRegistry,
                    toolRegistry = toolRegistry,
                    memoryStore = memoryStore,
                    contextStrategy = contextStrategy,
                    toolExecutor = toolExecutor,
                    collector = collector,
                    telemetry = telemetry,
                    clock = clock,
                    idGenerator = idGenerator,
                    childRunner = childRunner,
                ).apply {
                    history.addAll(this@DefaultLoopContext.history)
                }
                val branchResult = branch(childContext)
                parallelMutex.withLock {
                    accumulatedUsage = mergeUsage(accumulatedUsage, childContext.accumulatedUsage)
                    allExecutedToolCalls.addAll(childContext.allExecutedToolCalls)
                }
                branchResult
            }
        }.awaitAll()
    }

    override suspend fun append(message: Message) {
        history.add(message)
        newMessagesSinceRunStart.add(message)
    }

    override fun finish(text: String, usage: Usage?): LoopOutcome.Completed {
        val finalUsage = usage ?: accumulatedUsage
        return LoopOutcome.Completed(text, finalUsage)
    }

    override fun handoff(target: AgentRef, task: String): LoopOutcome.HandedOff {
        return LoopOutcome.HandedOff(target, task)
    }

    private fun resolveAllAuthorizedTools(): List<Tool> {
        val regularTools = agent.tools.mapNotNull { toolRegistry.get(it) }
        val inlineTools = agent.toolInstances

        val delegatedTools = agent.delegates.mapNotNull { subAgentId ->
            val subAgent = agentRegistry.get(subAgentId)
                ?: agent.managedAgentDefinitions.firstOrNull { it.id == subAgentId }
            if (subAgent != null) {
                DelegatedAgentTool(
                    childAgent = subAgent,
                    currentAgentPath = agentPath,
                    maxDepth = agent.limits.maxDelegationDepth,
                ) { childId, task, contextInfo, nextPath ->
                    val childAgentDef = agentRegistry.get(childId)
                        ?: agent.managedAgentDefinitions.firstOrNull { it.id == childId }
                        ?: subAgent
                    val childVars = if (contextInfo != null) variables + ("context" to contextInfo) else variables
                    val childResult = childRunner(childAgentDef, task, childVars, nextPath)
                    accumulatedUsage = mergeUsage(accumulatedUsage, childResult.usage)
                    childAgentDef.saveResultAs?.let { keyName ->
                        state[StateKey.string(keyName)] = childResult.text
                    }
                    childResult
                }
            } else null
        }

        return (regularTools + inlineTools + delegatedTools).distinctBy { it.spec.name }
    }

    private fun resolveTools(selection: ToolSelection): List<Tool> {
        val all = resolveAllAuthorizedTools()
        return when (selection) {
            is ToolSelection.None -> emptyList()
            is ToolSelection.Auto -> all
            is ToolSelection.Required -> all.filter { it.spec.name == selection.toolName }
            is ToolSelection.Subset -> all.filter { it.spec.name in selection.toolNames }
        }
    }

    private fun mergeUsage(current: Usage?, addition: Usage?): Usage? {
        if (addition == null) return current
        if (current == null) return addition
        return current + addition
    }
}
