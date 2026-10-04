package com.aivo.sdk.runtime.agent

import com.aivo.sdk.core.model.AgentRef
import com.aivo.sdk.core.model.RunState
import com.aivo.sdk.core.port.AgentLoop
import com.aivo.sdk.core.port.LoopContext
import com.aivo.sdk.core.port.LoopOutcome
import com.aivo.sdk.core.port.ToolSelection
import com.aivo.sdk.core.port.Turn

/**
 * Executes a single generation turn without invoking tools.
 */
public class SingleTurnLoop : AgentLoop {
    override val name: String = "single_turn"

    override suspend fun run(context: LoopContext): LoopOutcome {
        return when (val turn = context.think(tools = ToolSelection.None)) {
            is Turn.Text -> context.finish(turn.text, turn.usage)
            is Turn.Call -> context.finish("SingleTurnLoop received unexpected tool call.")
        }
    }

    public companion object {
        public val Default: SingleTurnLoop = SingleTurnLoop()
    }
}

/**
 * Executes a sequence of agents in order, feeding the output of each as input to the next.
 */
public class SequenceLoop(
    public val agents: List<AgentRef>,
) : AgentLoop {
    public constructor(vararg agentIds: String) : this(agentIds.map { AgentRef(it) })
    public constructor(vararg agentDefs: AgentDefinition) : this(agentDefs.map { AgentRef(it.id) })

    override val name: String = "sequence"

    override suspend fun run(context: LoopContext): LoopOutcome {
        var currentInput = context.input
        for (agentRef in agents) {
            currentInput = context.delegate(agentRef, currentInput)
        }
        return context.finish(currentInput)
    }
}

/**
 * Selects an agent based on user input and delegates to it.
 */
public class RouterLoop(
    public val selector: suspend (input: String, context: LoopContext) -> AgentRef,
) : AgentLoop {
    override val name: String = "router"

    override suspend fun run(context: LoopContext): LoopOutcome {
        val selected = selector(context.input, context)
        val result = context.delegate(selected, context.input)
        return context.finish(result)
    }
}

/**
 * Executes multiple agents concurrently on the same input and combines results with a merger lambda.
 */
public class ParallelLoop(
    public val agents: List<AgentRef>,
    public val merger: suspend (results: List<String>, context: LoopContext) -> String = { results, _ ->
        results.joinToString("\n\n---\n\n")
    },
) : AgentLoop {
    public constructor(vararg agentIds: String) : this(agentIds.map { AgentRef(it) })
    public constructor(vararg agentDefs: AgentDefinition) : this(agentDefs.map { AgentRef(it.id) })

    override val name: String = "parallel"

    override suspend fun run(context: LoopContext): LoopOutcome {
        val branches = agents.map { agentRef ->
            val branch: suspend (LoopContext) -> String = { ctx ->
                ctx.delegate(agentRef, context.input)
            }
            branch
        }
        val branchResults = context.parallel(branches)
        val finalOutput = merger(branchResults, context)
        return context.finish(finalOutput)
    }
}

/**
 * Scope for declarative workflow orchestration over declared team members.
 */
public class WorkflowScope(
    private val context: LoopContext,
    public val members: Set<String>,
) {
    public val input: String get() = context.input
    public val state: RunState get() = context.state

    public suspend fun run(agent: AgentDefinition, task: String = input): String {
        return run(agent.id, task)
    }

    public suspend fun run(agentRef: AgentRef, task: String = input): String {
        return run(agentRef.id, task)
    }

    public suspend fun run(agentId: String, task: String = input): String {
        require(members.contains(agentId)) {
            "Agent '$agentId' is not a declared member of this workflow. Declared members: $members"
        }
        return context.delegate(AgentRef(agentId), task)
    }

    public fun finish(text: String): LoopOutcome.Completed {
        return context.finish(text)
    }
}

/**
 * Creates a workflow [AgentLoop] over a declared set of member agents.
 */
public fun workflow(
    vararg members: AgentDefinition,
    block: suspend WorkflowScope.() -> LoopOutcome,
): AgentLoop {
    val memberIds = members.map { it.id }.toSet()
    return object : AgentLoop {
        override val name: String = "workflow"
        override suspend fun run(context: LoopContext): LoopOutcome {
            val scope = WorkflowScope(context, memberIds)
            return scope.block()
        }
    }
}
