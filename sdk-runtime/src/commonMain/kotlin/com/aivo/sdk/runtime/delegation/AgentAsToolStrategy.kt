package com.aivo.sdk.runtime.delegation

import com.aivo.sdk.core.error.DelegationCycleException
import com.aivo.sdk.core.error.MaxDelegationDepthExceededException
import com.aivo.sdk.core.model.ContentPart
import com.aivo.sdk.core.model.ConversationId
import com.aivo.sdk.core.model.Message
import com.aivo.sdk.core.model.ToolCall
import com.aivo.sdk.core.model.ToolResult
import com.aivo.sdk.core.model.ToolRisk
import com.aivo.sdk.core.model.ToolSpec
import com.aivo.sdk.core.port.Tool
import com.aivo.sdk.core.port.ToolContext
import com.aivo.sdk.runtime.agent.AgentDefinition
import com.aivo.sdk.runtime.agent.AgentEvent
import com.aivo.sdk.runtime.agent.AgentResult
import com.aivo.sdk.runtime.tool.schema.JsonSchema
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * Creates a pseudo-[Tool] that represents delegation to a child agent.
 *
 * When the LLM decides to delegate, it invokes `delegate_to_<agentId>`.
 * The tool checks for delegation cycles and depth bounds before executing the child agent.
 */
public class DelegatedAgentTool(
    private val childAgent: AgentDefinition,
    private val currentAgentPath: List<String>,
    private val maxDepth: Int,
    private val runner: suspend (
        childAgentId: String,
        task: String,
        contextInfo: String?,
        agentPath: List<String>,
    ) -> AgentResult,
) : Tool {

    override val spec: ToolSpec = ToolSpec(
        name = "delegate_to_${childAgent.id}",
        description = childAgent.description
            ?: "Delegates a specific subtask to the ${childAgent.name} specialist agent.",
        parameters = JsonSchema.obj(
            properties = mapOf(
                "task" to JsonSchema.string(description = "The specific instruction or request for the specialist agent."),
                "context" to JsonSchema.string(description = "Relevant background or context required to complete the task."),
            ),
            required = listOf("task"),
        ),
        risk = ToolRisk.LOW,
        requiresConfirmation = false,
    )

    override suspend fun execute(call: ToolCall, context: ToolContext): ToolResult {
        val childId = childAgent.id

        // 1. Cycle detection
        if (currentAgentPath.contains(childId)) {
            throw DelegationCycleException(currentAgentPath + childId)
        }

        // 2. Depth check
        if (currentAgentPath.size > maxDepth) {
            throw MaxDelegationDepthExceededException(maxDepth)
        }

        val task = call.arguments["task"]?.jsonPrimitive?.content
            ?: return ToolResult.Failure(
                com.aivo.sdk.core.model.ToolFailureKind.INVALID_ARGUMENTS,
                "Missing required 'task' argument for delegation to '$childId'.",
            )
        val contextInfo = call.arguments["context"]?.jsonPrimitive?.content

        val nextPath = currentAgentPath + childId
        val childResult = runner(childId, task, contextInfo, nextPath)

        return ToolResult.Success(JsonPrimitive(childResult.text))
    }
}
