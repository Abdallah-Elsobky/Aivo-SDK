package com.aivo.sdk

import com.aivo.sdk.agent.config.AgentDefinitionLoader
import com.aivo.sdk.agent.config.JsonAgentLoader
import com.aivo.sdk.agent.config.MarkdownAgentLoader
import com.aivo.sdk.agent.config.ResourceReader
import com.aivo.sdk.agent.config.defaultResourceReader
import com.aivo.sdk.core.error.ConfigurationException
import com.aivo.sdk.core.model.ModelRef
import com.aivo.sdk.core.model.ProviderId
import com.aivo.sdk.core.port.AlwaysAllow
import com.aivo.sdk.core.port.CharCountEstimator
import com.aivo.sdk.core.port.ConfirmationHandler
import com.aivo.sdk.core.port.CredentialsProvider
import com.aivo.sdk.core.port.DefaultRedactor
import com.aivo.sdk.core.port.DefaultToolPolicy
import com.aivo.sdk.core.port.LlmProvider
import com.aivo.sdk.core.port.Logger
import com.aivo.sdk.core.port.MemoryStore
import com.aivo.sdk.core.port.NoOpLogger
import com.aivo.sdk.core.port.NoOpTelemetry
import com.aivo.sdk.core.port.Redactor
import com.aivo.sdk.core.port.Telemetry
import com.aivo.sdk.core.port.TokenEstimator
import com.aivo.sdk.core.port.Tool
import com.aivo.sdk.core.port.ToolPolicy
import com.aivo.sdk.core.port.staticCredentials
import com.aivo.sdk.core.util.Clock
import com.aivo.sdk.core.util.IdGenerator
import com.aivo.sdk.core.util.SecretString
import com.aivo.sdk.core.util.SystemClock
import com.aivo.sdk.core.util.UuidIdGenerator
import com.aivo.sdk.middleware.LoggingLlmProvider
import com.aivo.sdk.middleware.RetryPolicy
import com.aivo.sdk.middleware.RetryingLlmProvider
import com.aivo.sdk.middleware.TelemetryLlmProvider
import com.aivo.sdk.provider.gemini.GeminiConfig
import com.aivo.sdk.provider.gemini.GeminiProvider
import com.aivo.sdk.provider.ollama.OllamaConfig
import com.aivo.sdk.provider.ollama.OllamaProvider
import com.aivo.sdk.provider.openai.OpenAiCompatibleConfig
import com.aivo.sdk.provider.openai.OpenAiCompatibleProvider
import com.aivo.sdk.provider.openai.OpenRouterProvider
import com.aivo.sdk.runtime.agent.AgentDefinition
import com.aivo.sdk.runtime.agent.AgentDefinitionBuilder
import com.aivo.sdk.runtime.agent.AgentRegistry
import com.aivo.sdk.runtime.agent.AgentRuntime
import com.aivo.sdk.runtime.agent.ConcurrencyPolicy
import com.aivo.sdk.runtime.memory.InMemoryMemoryStore
import com.aivo.sdk.runtime.memory.context.ContextWindowStrategy
import com.aivo.sdk.runtime.memory.context.KeepAllStrategy
import com.aivo.sdk.runtime.memory.context.SlidingWindowStrategy
import com.aivo.sdk.runtime.memory.context.TokenBudgetStrategy
import com.aivo.sdk.runtime.tool.ToolBuilder
import com.aivo.sdk.runtime.tool.ToolExecutor
import com.aivo.sdk.runtime.tool.ToolRegistry
import com.aivo.sdk.runtime.tool.tool
import com.aivo.sdk.transport.TransportConfig

/**
 * Fluent builder for configuring and assembling an [AivoSdk] instance.
 */
public class AivoSdkBuilder {
    private var defaultModelRef: ModelRef? = null
    private var entryAgentId: String? = null

    private var clock: Clock = SystemClock
    private var idGenerator: IdGenerator = UuidIdGenerator

    private val providersDsl = ProvidersDsl()
    private val toolsDsl = ToolsDsl()
    private val agentsDsl = AgentsDsl()
    private val memoryDsl = MemoryDsl()
    private val runtimeDsl = RuntimeDsl()
    private val resilienceDsl = ResilienceDsl()
    private val securityDsl = SecurityDsl()
    private val observabilityDsl = ObservabilityDsl()

    /**
     * Sets the default model for all agents that do not explicitly specify a model.
     *
     * Format: `"providerId:modelName"` (e.g. `"ollama:gemma4:31b"`, `"openrouter:anthropic/claude-3.5-sonnet"`).
     */
    public fun defaultModel(modelRef: String): AivoSdkBuilder = apply {
        this.defaultModelRef = ModelRef.parse(modelRef)
    }

    /**
     * Sets the default model reference.
     */
    public fun defaultModel(modelRef: ModelRef): AivoSdkBuilder = apply {
        this.defaultModelRef = modelRef
    }

    /**
     * Sets the default model from a strongly-typed [ProviderModel] (e.g. [OllamaModel], [OpenRouterModel], [GeminiModel]).
     */
    public fun defaultModel(model: com.aivo.sdk.core.model.ProviderModel): AivoSdkBuilder = apply {
        this.defaultModelRef = model.toModelRef()
    }

    /**
     * Sets the ID of the default or entry agent for chat runs.
     */
    public fun entryAgent(agentId: String): AivoSdkBuilder = apply {
        this.entryAgentId = agentId
    }

    /**
     * Configures LLM providers.
     */
    public fun providers(block: ProvidersDsl.() -> Unit): AivoSdkBuilder = apply {
        providersDsl.apply(block)
    }

    /**
     * Registers tools available for agent execution.
     */
    public fun tools(block: ToolsDsl.() -> Unit): AivoSdkBuilder = apply {
        toolsDsl.apply(block)
    }

    /**
     * Configures agents, loaded from code, Markdown, or JSON.
     */
    public fun agents(block: AgentsDsl.() -> Unit): AivoSdkBuilder = apply {
        agentsDsl.apply(block)
    }

    /**
     * Configures conversation memory storage and context window strategy.
     */
    public fun memory(block: MemoryDsl.() -> Unit): AivoSdkBuilder = apply {
        memoryDsl.apply(block)
    }

    /**
     * Configures execution bounds and concurrency policies for the agent runtime.
     */
    public fun runtime(block: RuntimeDsl.() -> Unit): AivoSdkBuilder = apply {
        runtimeDsl.apply(block)
    }

    /**
     * Configures retry policies, backoffs, and network timeouts.
     */
    public fun resilience(block: ResilienceDsl.() -> Unit): AivoSdkBuilder = apply {
        resilienceDsl.apply(block)
    }

    /**
     * Configures security policies, confirmation handlers, and safety gates.
     */
    public fun security(block: SecurityDsl.() -> Unit): AivoSdkBuilder = apply {
        securityDsl.apply(block)
    }

    /**
     * Configures logging, telemetry, and redacting.
     */
    public fun observability(block: ObservabilityDsl.() -> Unit): AivoSdkBuilder = apply {
        observabilityDsl.apply(block)
    }

    /**
     * Injects a custom [Clock] for testing.
     */
    public fun clock(clock: Clock): AivoSdkBuilder = apply {
        this.clock = clock
    }

    /**
     * Injects a custom [IdGenerator] for testing.
     */
    public fun idGenerator(idGenerator: IdGenerator): AivoSdkBuilder = apply {
        this.idGenerator = idGenerator
    }

    /**
     * Assembles, validates, and builds the immutable [AivoSdk] instance.
     *
     * @throws ConfigurationException if any configuration errors or unresolvable references exist.
     */
    public fun build(): AivoSdk {
        val problems = mutableListOf<String>()

        val rawProviders = providersDsl.buildProviders(resilienceDsl.transportConfig())
        val rawTools = toolsDsl.buildTools().toMutableList()
        val rawAgents = agentsDsl.buildAgents()

        val agentList = mutableListOf<AgentDefinition>()
        fun collectAgent(a: AgentDefinition) {
            if (agentList.none { it.id == a.id }) {
                agentList.add(a)
                for (inlineTool in a.toolInstances) {
                    if (rawTools.none { it.spec.name == inlineTool.spec.name }) {
                        rawTools.add(inlineTool)
                    }
                }
                for (managed in a.managedAgentDefinitions) {
                    collectAgent(managed)
                }
            }
        }
        for (a in rawAgents) {
            collectAgent(a)
        }
        val toolList = rawTools.toList()

        val agentMap = mutableMapOf<String, AgentDefinition>()
        for (agent in agentList) {
            if (agentMap.containsKey(agent.id)) {
                problems.add("Duplicate agent ID: '${agent.id}'")
            } else {
                agentMap[agent.id] = agent
            }
        }

        val toolMap = mutableMapOf<String, Tool>()
        for (t in toolList) {
            if (toolMap.containsKey(t.spec.name)) {
                problems.add("Duplicate tool name: '${t.spec.name}'")
            } else {
                toolMap[t.spec.name] = t
            }
        }

        // Validate entryAgent
        if (entryAgentId != null && !agentMap.containsKey(entryAgentId)) {
            problems.add("Entry agent '$entryAgentId' was configured but not defined in agents block.")
        }

        // Validate agent references
        for (agent in agentList) {
            val effectiveModel = agent.model ?: defaultModelRef
            if (effectiveModel == null) {
                problems.add("Agent '${agent.id}' has no model specified and no defaultModel was set.")
            } else if (!rawProviders.containsKey(effectiveModel.provider)) {
                problems.add("Agent '${agent.id}' requires provider '${effectiveModel.provider.value}', but it was not registered.")
            }

            for (toolName in agent.tools) {
                if (!toolMap.containsKey(toolName)) {
                    problems.add("Agent '${agent.id}' requires tool '$toolName', but it was not registered.")
                }
            }

            for (delegateId in agent.delegates) {
                if (!agentMap.containsKey(delegateId)) {
                    problems.add("Agent '${agent.id}' delegates to '$delegateId', but agent '$delegateId' was not registered.")
                }
            }
        }

        // Cycle check in static definitions
        for (agent in agentList) {
            val visited = mutableSetOf<String>()
            fun detectCycle(currentId: String, path: List<String>) {
                if (path.contains(currentId)) {
                    problems.add("Delegation cycle detected in static definitions: ${(path + currentId).joinToString(" -> ")}")
                    return
                }
                val curAgent = agentMap[currentId] ?: return
                for (next in curAgent.delegates) {
                    detectCycle(next, path + currentId)
                }
            }
            detectCycle(agent.id, emptyList())
        }

        if (problems.isNotEmpty()) {
            throw ConfigurationException(problems.distinct())
        }

        // Decorate providers: Telemetry( Logging( Retrying( base ) ) )
        val decoratedProviders = rawProviders.mapValues { (_, baseProvider) ->
            TelemetryLlmProvider(
                delegate = LoggingLlmProvider(
                    delegate = RetryingLlmProvider(
                        delegate = baseProvider,
                        policy = resilienceDsl.retryPolicy,
                    ),
                    logger = observabilityDsl.logger,
                    logPayloads = observabilityDsl.logPayloads,
                    redactor = observabilityDsl.redactor,
                    clock = clock,
                ),
                telemetry = observabilityDsl.telemetry,
                clock = clock,
            )
        }

        val toolRegistry = ToolRegistry(toolList)
        val agentRegistry = AgentRegistry(agentList)

        val toolExecutor = ToolExecutor(
            registry = toolRegistry,
            policy = securityDsl.toolPolicy,
            confirmationHandler = securityDsl.confirmationHandler,
            defaultTimeoutMs = runtimeDsl.toolTimeoutMs,
            telemetry = observabilityDsl.telemetry,
            clock = clock,
        )

        val agentRuntime = AgentRuntime(
            providers = decoratedProviders,
            agentRegistry = agentRegistry,
            toolRegistry = toolRegistry,
            memoryStore = memoryDsl.store,
            contextStrategy = memoryDsl.window,
            toolExecutor = toolExecutor,
            logger = observabilityDsl.logger,
            telemetry = observabilityDsl.telemetry,
            clock = clock,
            idGenerator = idGenerator,
            concurrencyPolicy = runtimeDsl.concurrencyPolicy,
            defaultModel = defaultModelRef,
        )

        return AivoSdkImpl(
            defaultModel = defaultModelRef,
            entryAgentId = entryAgentId,
            providers = decoratedProviders,
            agents = agentRegistry,
            tools = toolRegistry,
            runtime = agentRuntime,
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Nested DSL Classes
    // ─────────────────────────────────────────────────────────────────────────

    public class ProvidersDsl {
        private val providers = mutableMapOf<ProviderId, LlmProvider>()
        private val factories = mutableListOf<(TransportConfig) -> Pair<ProviderId, LlmProvider>>()

        public fun register(provider: LlmProvider) {
            providers[provider.id] = provider
        }

        public fun register(id: String, provider: LlmProvider) {
            providers[ProviderId(id)] = provider
        }

        public fun ollama(id: String = "ollama", block: (OllamaDsl.() -> Unit)? = null) {
            val dsl = OllamaDsl(id).apply { block?.invoke(this) }
            factories.add { transport ->
                val cfg = OllamaConfig(
                    id = dsl.id,
                    baseUrl = dsl.baseUrl,
                    credentials = dsl.credentials,
                    transport = transport,
                )
                ProviderId(dsl.id) to OllamaProvider(cfg)
            }
        }

        public fun openRouter(id: String = "openrouter", block: (OpenRouterDsl.() -> Unit)? = null) {
            val dsl = OpenRouterDsl(id).apply { block?.invoke(this) }
            factories.add { transport ->
                val creds = dsl.credentials
                    ?: throw ConfigurationException(listOf("OpenRouter provider '$id' requires credentials."))
                ProviderId(dsl.id) to OpenRouterProvider(
                    id = dsl.id,
                    baseUrl = dsl.baseUrl,
                    credentials = creds,
                    httpReferer = dsl.httpReferer,
                    xTitle = dsl.xTitle,
                    transport = transport,
                )
            }
        }

        public fun openAiCompatible(id: String = "openai", block: (OpenAiDsl.() -> Unit)? = null) {
            val dsl = OpenAiDsl(id).apply { block?.invoke(this) }
            factories.add { transport ->
                val creds = dsl.credentials
                    ?: throw ConfigurationException(listOf("OpenAI-compatible provider '$id' requires credentials."))
                val cfg = OpenAiCompatibleConfig(
                    id = dsl.id,
                    baseUrl = dsl.baseUrl,
                    credentials = creds,
                    extraHeaders = dsl.extraHeaders,
                    transport = transport,
                )
                ProviderId(dsl.id) to OpenAiCompatibleProvider(cfg)
            }
        }

        public fun gemini(id: String = "gemini", block: (GeminiDsl.() -> Unit)? = null) {
            val dsl = GeminiDsl(id).apply { block?.invoke(this) }
            factories.add { transport ->
                val creds = dsl.credentials
                    ?: throw ConfigurationException(listOf("Gemini provider '$id' requires credentials."))
                val cfg = GeminiConfig(
                    id = dsl.id,
                    baseUrl = dsl.baseUrl,
                    credentials = creds,
                    transport = transport,
                )
                ProviderId(dsl.id) to GeminiProvider(cfg)
            }
        }

        internal fun buildProviders(transport: TransportConfig): Map<ProviderId, LlmProvider> {
            val result = providers.toMutableMap()
            for (factory in factories) {
                val (id, provider) = factory(transport)
                result[id] = provider
            }
            return result
        }
    }

    public class OllamaDsl(public val id: String) {
        public var baseUrl: String = "https://ollama.com"
        public var credentials: CredentialsProvider? = null

        public fun baseUrl(url: String) { this.baseUrl = url }
        public fun apiKey(key: String) { this.credentials = staticCredentials(SecretString(key)) }
        public fun credentials(provider: CredentialsProvider) { this.credentials = provider }
        public fun credentials(block: suspend () -> SecretString) { this.credentials = CredentialsProvider { block() } }
    }

    public class OpenRouterDsl(public val id: String) {
        public var baseUrl: String = "https://openrouter.ai/api/v1"
        public var credentials: CredentialsProvider? = null
        public var httpReferer: String? = null
        public var xTitle: String? = null

        public fun baseUrl(url: String) { this.baseUrl = url }
        public fun apiKey(key: String) { this.credentials = staticCredentials(SecretString(key)) }
        public fun credentials(provider: CredentialsProvider) { this.credentials = provider }
        public fun credentials(block: suspend () -> SecretString) { this.credentials = CredentialsProvider { block() } }
        public fun referer(url: String) { this.httpReferer = url }
        public fun title(title: String) { this.xTitle = title }
    }

    public class OpenAiDsl(public val id: String) {
        public var baseUrl: String = "https://api.openai.com/v1"
        public var credentials: CredentialsProvider? = null
        public var extraHeaders: Map<String, String> = emptyMap()

        public fun baseUrl(url: String) { this.baseUrl = url }
        public fun apiKey(key: String) { this.credentials = staticCredentials(SecretString(key)) }
        public fun credentials(provider: CredentialsProvider) { this.credentials = provider }
        public fun credentials(block: suspend () -> SecretString) { this.credentials = CredentialsProvider { block() } }
        public fun header(key: String, value: String) { this.extraHeaders = this.extraHeaders + (key to value) }
    }

    public class GeminiDsl(public val id: String) {
        public var baseUrl: String = "https://generativelanguage.googleapis.com"
        public var credentials: CredentialsProvider? = null

        public fun baseUrl(url: String) { this.baseUrl = url }
        public fun apiKey(key: String) { this.credentials = staticCredentials(SecretString(key)) }
        public fun credentials(provider: CredentialsProvider) { this.credentials = provider }
        public fun credentials(block: suspend () -> SecretString) { this.credentials = CredentialsProvider { block() } }
    }

    public class ToolsDsl {
        private val tools = mutableListOf<Tool>()

        public operator fun Tool.unaryPlus() {
            register(this)
        }

        public operator fun com.aivo.sdk.core.model.Toolbox.unaryPlus() {
            register(this.tools)
        }

        public fun register(tool: Tool) {
            if (tools.none { it.spec.name == tool.spec.name }) {
                tools.add(tool)
            }
        }

        public fun register(tools: Collection<Tool>) {
            for (t in tools) {
                register(t)
            }
        }

        public fun tool(
            name: String,
            description: String = "",
            block: ToolBuilder.() -> Unit,
        ): Tool {
            val t = ToolBuilder(name, description).apply(block).build()
            register(t)
            return t
        }

        internal fun buildTools(): List<Tool> = tools.toList()
    }

    public class AgentsDsl {
        private val agents = mutableListOf<AgentDefinition>()

        public operator fun AgentDefinition.unaryPlus() {
            register(this)
        }

        public fun register(agent: AgentDefinition) {
            if (agents.none { it.id == agent.id }) {
                agents.add(agent)
            }
        }

        public fun register(agents: Collection<AgentDefinition>) {
            for (a in agents) {
                register(a)
            }
        }

        public fun agent(id: String, block: AgentDefinitionBuilder.() -> Unit): AgentDefinition {
            val agent = AgentDefinitionBuilder(id).apply(block).build()
            register(agent)
            return agent
        }

        public fun supervisor(id: String, block: AgentDefinitionBuilder.() -> Unit): AgentDefinition {
            val agent = AgentDefinitionBuilder(id).apply {
                role = com.aivo.sdk.core.model.AgentRole.SUPERVISOR
                block()
            }.build()
            register(agent)
            return agent
        }

        public fun define(id: String, block: AgentDefinitionBuilder.() -> Unit): AgentDefinition {
            return agent(id, block)
        }

        public fun fromMarkdown(content: String): AgentDefinition {
            val agent = MarkdownAgentLoader.parse(content)
            register(agent)
            return agent
        }

        public suspend fun fromMarkdownPath(
            resourcePath: String,
            reader: ResourceReader = defaultResourceReader(),
        ): AgentDefinition {
            val content = reader.readText(resourcePath)
            return fromMarkdown(content)
        }

        public fun fromJson(content: String): AgentDefinition {
            val agent = JsonAgentLoader.parse(content)
            register(agent)
            return agent
        }

        public suspend fun fromJsonPath(
            resourcePath: String,
            reader: ResourceReader = defaultResourceReader(),
        ): AgentDefinition {
            val content = reader.readText(resourcePath)
            return fromJson(content)
        }

        internal fun buildAgents(): List<AgentDefinition> = agents.toList()
    }

    public class MemoryDsl {
        public var store: MemoryStore = InMemoryMemoryStore()
        public var window: ContextWindowStrategy = KeepAllStrategy

        public fun keepAll() { window = KeepAllStrategy }
        public fun slidingWindow(maxMessages: Int) { window = SlidingWindowStrategy(maxMessages) }
        public fun tokenBudget(maxTokens: Int, estimator: TokenEstimator = CharCountEstimator) {
            window = TokenBudgetStrategy(maxTokens, estimator)
        }
    }

    public class RuntimeDsl {
        public var maxSteps: Int = 10
        public var maxDelegationDepth: Int = 3
        public var parallelToolExecution: Boolean = true
        public var toolTimeoutMs: Long = 30_000L
        public var runTimeoutMs: Long = 60_000L
        public var concurrencyPolicy: ConcurrencyPolicy = ConcurrencyPolicy.QUEUE
    }

    public class ResilienceDsl {
        public var retryPolicy: RetryPolicy = RetryPolicy()
        public var connectTimeoutMs: Long = 10_000L
        public var requestTimeoutMs: Long = 60_000L
        public var streamIdleTimeoutMs: Long = 30_000L

        public fun retry(block: RetryPolicyBuilder.() -> Unit) {
            val b = RetryPolicyBuilder().apply(block)
            this.retryPolicy = RetryPolicy(
                maxRetries = b.maxAttempts,
                initialDelayMs = b.baseDelayMs,
                maxDelayMs = b.maxDelayMs,
                jitterFactor = if (b.jitter) 0.2 else 0.0,
            )
        }

        public fun timeouts(block: TimeoutsBuilder.() -> Unit) {
            val b = TimeoutsBuilder().apply(block)
            this.connectTimeoutMs = b.connectTimeoutMs
            this.requestTimeoutMs = b.requestTimeoutMs
            this.streamIdleTimeoutMs = b.streamIdleTimeoutMs
        }

        internal fun transportConfig(): TransportConfig = TransportConfig(
            connectTimeoutMs = connectTimeoutMs,
            requestTimeoutMs = requestTimeoutMs,
            streamIdleTimeoutMs = streamIdleTimeoutMs,
        )
    }

    public class RetryPolicyBuilder {
        public var maxAttempts: Int = 3
        public var baseDelayMs: Long = 1_000L
        public var maxDelayMs: Long = 30_000L
        public var jitter: Boolean = true
    }

    public class TimeoutsBuilder {
        public var connectTimeoutMs: Long = 10_000L
        public var requestTimeoutMs: Long = 60_000L
        public var streamIdleTimeoutMs: Long = 30_000L
    }

    public class SecurityDsl {
        public var toolPolicy: ToolPolicy = DefaultToolPolicy
        public var confirmationHandler: ConfirmationHandler = AlwaysAllow
        public var allowInsecure: Boolean = false
    }

    public class ObservabilityDsl {
        public var logger: Logger = NoOpLogger
        public var telemetry: Telemetry = NoOpTelemetry
        public var logPayloads: Boolean = false
        public var redactor: Redactor = DefaultRedactor()
    }
}
