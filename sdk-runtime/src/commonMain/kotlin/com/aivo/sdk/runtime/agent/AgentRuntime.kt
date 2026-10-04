package com.aivo.sdk.runtime.agent

import com.aivo.sdk.core.error.AgentException
import com.aivo.sdk.core.error.AgentRunException
import com.aivo.sdk.core.error.ConcurrentRunException
import com.aivo.sdk.core.error.ConfigurationException
import com.aivo.sdk.core.error.MaxStepsExceededException
import com.aivo.sdk.core.error.RunTimeoutException
import com.aivo.sdk.core.error.SdkException
import com.aivo.sdk.core.error.TokenBudgetExceededException
import com.aivo.sdk.core.error.UnknownAgentException
import com.aivo.sdk.core.error.UnsupportedCapabilityException
import com.aivo.sdk.core.model.ContentPart
import com.aivo.sdk.core.model.ConversationId
import com.aivo.sdk.core.model.LlmRequest
import com.aivo.sdk.core.model.LlmResponse
import com.aivo.sdk.core.model.LlmStreamEvent
import com.aivo.sdk.core.model.Message
import com.aivo.sdk.core.model.ModelRef
import com.aivo.sdk.core.model.ProviderId
import com.aivo.sdk.core.model.ToolCall
import com.aivo.sdk.core.model.ToolResult
import com.aivo.sdk.core.model.Usage
import com.aivo.sdk.core.port.CharCountEstimator
import com.aivo.sdk.core.port.LlmProvider
import com.aivo.sdk.core.port.Logger
import com.aivo.sdk.core.port.MemoryStore
import com.aivo.sdk.core.port.NoOpLogger
import com.aivo.sdk.core.port.NoOpTelemetry
import com.aivo.sdk.core.port.Telemetry
import com.aivo.sdk.core.port.TelemetryEvent
import com.aivo.sdk.core.port.Tool
import com.aivo.sdk.core.port.ToolContext
import com.aivo.sdk.core.util.Clock
import com.aivo.sdk.core.util.IdGenerator
import com.aivo.sdk.core.util.SystemClock
import com.aivo.sdk.core.util.UuidIdGenerator
import com.aivo.sdk.runtime.delegation.DelegatedAgentTool
import com.aivo.sdk.runtime.memory.context.ContextWindowStrategy
import com.aivo.sdk.runtime.memory.context.KeepAllStrategy
import com.aivo.sdk.runtime.prompt.PromptRenderer
import com.aivo.sdk.runtime.tool.ToolExecutor
import com.aivo.sdk.runtime.tool.ToolRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Concurrency policy for runs within the same [ConversationId].
 */
public enum class ConcurrencyPolicy {
    /** Wait in line for the active run to finish. */
    QUEUE,
    /** Reject immediately with [ConcurrentRunException]. */
    REJECT,
}

/**
 * The orchestrator that executes the agent loop:
 * System Prompt + History -> LLM -> Tool Calls -> Tool Results -> ... -> Final Answer.
 */
public class AgentRuntime(
    private val providers: Map<ProviderId, LlmProvider>,
    private val agentRegistry: AgentRegistry,
    private val toolRegistry: ToolRegistry,
    private val memoryStore: MemoryStore,
    private val contextStrategy: ContextWindowStrategy = KeepAllStrategy,
    private val toolExecutor: ToolExecutor,
    private val logger: Logger = NoOpLogger,
    private val telemetry: Telemetry = NoOpTelemetry,
    private val clock: Clock = SystemClock,
    private val idGenerator: IdGenerator = UuidIdGenerator,
    private val concurrencyPolicy: ConcurrencyPolicy = ConcurrencyPolicy.QUEUE,
    private val defaultModel: ModelRef? = null,
) {
    private val globalMutex = Mutex()
    private val conversationLocks = mutableMapOf<ConversationId, Mutex>()

    /**
     * Executes an agent run to completion and returns the final [AgentResult].
     */
    public suspend fun run(
        agentId: String,
        input: String,
        conversationId: ConversationId = ConversationId(idGenerator.next()),
        variables: Map<String, String> = emptyMap(),
    ): AgentResult {
        var finalResult: AgentResult? = null
        stream(agentId, input, conversationId, variables).collect { event ->
            if (event is AgentEvent.RunCompleted) {
                finalResult = event.result
            }
        }
        return finalResult ?: throw AgentRunException("Agent run ended without producing a final result.")
    }

    /**
     * Streams events emitted during the execution of an agent run.
     */
    public fun stream(
        agentId: String,
        input: String,
        conversationId: ConversationId = ConversationId(idGenerator.next()),
        variables: Map<String, String> = emptyMap(),
    ): Flow<AgentEvent> = flow {
        val rootPath = listOf(agentId)
        val runId = idGenerator.next()

        emit(AgentEvent.RunStarted(runId, conversationId, agentId, rootPath))
        val runStartMs = clock.nowMillis()

        telemetry.record(
            TelemetryEvent.AgentRunStarted(
                runId = runId,
                conversationId = conversationId,
                agentId = agentId,
                startedAtMs = runStartMs,
            )
        )

        val lock = getConversationLock(conversationId)
        val acquired = if (concurrencyPolicy == ConcurrencyPolicy.REJECT) {
            lock.tryLock()
        } else {
            lock.lock()
            true
        }

        if (!acquired) {
            val ex = ConcurrentRunException(conversationId)
            emit(AgentEvent.RunFailed(ex, rootPath))
            throw ex
        }

        try {
            val agent = agentRegistry.get(agentId)
                ?: throw UnknownAgentException(agentId)

            withTimeout(agent.limits.runTimeoutMs) {
                executeAgentFlow(
                    agent = agent,
                    input = input,
                    conversationId = conversationId,
                    variables = variables,
                    runId = runId,
                    agentPath = rootPath,
                    collector = this@flow,
                )
            }
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) {
                val ex = RunTimeoutException(agentId, 0L)
                emit(AgentEvent.RunFailed(ex, rootPath))
                throw ex
            }
            throw e
        } catch (e: SdkException) {
            emit(AgentEvent.RunFailed(e, rootPath))
            telemetry.record(
                TelemetryEvent.AgentRunFailed(
                    runId = runId,
                    conversationId = conversationId,
                    agentId = agentId,
                    durationMs = clock.nowMillis() - runStartMs,
                    steps = 0,
                    errorType = e::class.simpleName ?: "SdkException",
                )
            )
            throw e
        } catch (e: Throwable) {
            val ex = AgentRunException("Agent run failed unexpectedly: ${e.message}", e)
            emit(AgentEvent.RunFailed(ex, rootPath))
            telemetry.record(
                TelemetryEvent.AgentRunFailed(
                    runId = runId,
                    conversationId = conversationId,
                    agentId = agentId,
                    durationMs = clock.nowMillis() - runStartMs,
                    steps = 0,
                    errorType = e::class.simpleName ?: "Throwable",
                )
            )
            throw ex
        } finally {
            lock.unlock()
        }
    }

    private suspend fun getConversationLock(id: ConversationId): Mutex {
        return globalMutex.withLock {
            conversationLocks.getOrPut(id) { Mutex() }
        }
    }

    private suspend fun executeAgentFlow(
        agent: AgentDefinition,
        input: String,
        conversationId: ConversationId,
        variables: Map<String, String>,
        runId: String,
        agentPath: List<String>,
        collector: FlowCollector<AgentEvent>,
        sharedState: com.aivo.sdk.core.model.RunState = InMemoryRunState(),
    ): AgentResult {
        collector.emit(AgentEvent.AgentEntered(agent.id, agentPath))

        val loopContext = DefaultLoopContext(
            agent = agent,
            input = input,
            conversationId = conversationId,
            runId = runId,
            agentPath = agentPath,
            variables = variables,
            state = sharedState,
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
            childRunner = { childAgent, task, childContext, nextPath ->
                val childConvo = ConversationId("${conversationId.value}_${childAgent.id}")
                executeAgentFlow(
                    agent = childAgent,
                    input = task,
                    conversationId = childConvo,
                    variables = variables + childContext,
                    runId = runId,
                    agentPath = nextPath,
                    collector = collector,
                    sharedState = sharedState,
                )
            },
        )

        // Load history and add user message
        val loadedHistory = memoryStore.load(conversationId)
        loopContext.history.addAll(loadedHistory)
        val userMsg = Message.User(text = input)
        loopContext.history.add(userMsg)
        loopContext.newMessagesSinceRunStart.add(userMsg)

        val loop = agent.loop ?: ToolCallingLoop.Default
        val outcome = loop.run(loopContext)

        return when (outcome) {
            is com.aivo.sdk.core.port.LoopOutcome.Completed -> {
                memoryStore.append(conversationId, loopContext.newMessagesSinceRunStart)

                val result = AgentResult(
                    runId = runId,
                    conversationId = conversationId,
                    agentId = agent.id,
                    text = outcome.text,
                    toolCalls = loopContext.allExecutedToolCalls,
                    steps = loopContext.stepCount,
                    usage = outcome.usage ?: loopContext.accumulatedUsage,
                )

                agent.saveResultAs?.let { keyName ->
                    sharedState[com.aivo.sdk.core.model.StateKey.string(keyName)] = result.text
                }

                collector.emit(AgentEvent.RunCompleted(result, agentPath))
                telemetry.record(
                    TelemetryEvent.AgentRunCompleted(
                        runId = runId,
                        conversationId = conversationId,
                        agentId = agent.id,
                        durationMs = 0L,
                        steps = loopContext.stepCount,
                        totalInputTokens = result.usage?.inputTokens,
                        totalOutputTokens = result.usage?.outputTokens,
                        totalCostUsd = result.usage?.costUsd,
                    )
                )
                result
            }
            is com.aivo.sdk.core.port.LoopOutcome.HandedOff -> {
                val targetAgent = agentRegistry.get(outcome.targetAgent.id)
                    ?: throw UnknownAgentException(outcome.targetAgent.id)
                executeAgentFlow(
                    agent = targetAgent,
                    input = outcome.task,
                    conversationId = conversationId,
                    variables = variables,
                    runId = runId,
                    agentPath = agentPath + targetAgent.id,
                    collector = collector,
                    sharedState = sharedState,
                )
            }
        }
    }
}

