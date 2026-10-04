package com.aivo.sdk.runtime.memory.context

import com.aivo.sdk.core.model.ContentPart
import com.aivo.sdk.core.model.Message
import com.aivo.sdk.core.port.CharCountEstimator
import com.aivo.sdk.core.port.TokenEstimator

/**
 * Strategy for selecting which messages from conversation history are sent to the LLM.
 *
 * **Crucial Invariant:**
 * An assistant message with [Message.Assistant.toolCalls] must **never** be sent without
 * all of its corresponding [Message.Tool] messages. Trimming must always discard
 * tool calls and tool results as a single atomic unit.
 */
public interface ContextWindowStrategy {
    public fun select(messages: List<Message>): List<Message>
}

/**
 * Sends the complete message history without trimming.
 */
public object KeepAllStrategy : ContextWindowStrategy {
    override fun select(messages: List<Message>): List<Message> = messages
}

/**
 * Keeps the system message (if any), the latest user message, and the most recent [maxHistoryUnits]
 * turns of conversation, preserving tool-call atomic grouping.
 */
public class SlidingWindowStrategy(
    private val maxHistoryUnits: Int = 10,
) : ContextWindowStrategy {

    override fun select(messages: List<Message>): List<Message> {
        if (messages.isEmpty() || maxHistoryUnits <= 0) return emptyList()

        val systemMessages = messages.filterIsInstance<Message.System>()
        val nonSystemMessages = messages.filter { it !is Message.System }
        if (nonSystemMessages.isEmpty()) return systemMessages

        val latestUser = nonSystemMessages.lastOrNull { it is Message.User }
        val historyToGroup = if (latestUser != null && nonSystemMessages.last() == latestUser) {
            nonSystemMessages.dropLast(1)
        } else {
            nonSystemMessages
        }

        val groups = groupAtomicUnits(historyToGroup)
        val selectedGroups = groups.takeLast(maxHistoryUnits).flatten()

        return buildList {
            addAll(systemMessages)
            addAll(selectedGroups)
            if (latestUser != null && nonSystemMessages.last() == latestUser) {
                add(latestUser)
            }
        }
    }
}

/**
 * Selects messages such that the total estimated token count does not exceed [maxTokens].
 *
 * Guarantees:
 * - Always preserves [Message.System] and the latest [Message.User].
 * - Traverses historical turns backward (newest to oldest), adding atomic assistant+tool groups.
 * - Restores original chronological order in the result.
 */
public class TokenBudgetStrategy(
    private val maxTokens: Int,
    private val estimator: TokenEstimator = CharCountEstimator,
) : ContextWindowStrategy {

    override fun select(messages: List<Message>): List<Message> {
        if (messages.isEmpty()) return emptyList()

        val systemMessages = messages.filterIsInstance<Message.System>()
        val nonSystemMessages = messages.filter { it !is Message.System }
        if (nonSystemMessages.isEmpty()) return systemMessages

        val latestUser = nonSystemMessages.lastOrNull { it is Message.User }
        val historyToGroup = if (latestUser != null && nonSystemMessages.last() == latestUser) {
            nonSystemMessages.dropLast(1)
        } else {
            nonSystemMessages
        }

        var remainingBudget = maxTokens
        for (sys in systemMessages) {
            remainingBudget -= estimateTokens(sys, estimator)
        }
        if (latestUser != null && nonSystemMessages.last() == latestUser) {
            remainingBudget -= estimateTokens(latestUser, estimator)
        }

        val groups = groupAtomicUnits(historyToGroup)
        val chosenGroups = mutableListOf<List<Message>>()

        // From newest unit to oldest
        for (group in groups.reversed()) {
            val groupTokens = group.sumOf { estimateTokens(it, estimator) }
            if (groupTokens <= remainingBudget) {
                chosenGroups.add(group)
                remainingBudget -= groupTokens
            } else {
                break
            }
        }

        return buildList {
            addAll(systemMessages)
            // Restore chronological order
            chosenGroups.reversed().forEach { addAll(it) }
            if (latestUser != null && nonSystemMessages.last() == latestUser) {
                add(latestUser)
            }
        }
    }
}

/**
 * Groups messages into atomic conversation units so that assistant messages with tool calls
 * and their subsequent tool results are never split apart.
 */
internal fun groupAtomicUnits(messages: List<Message>): List<List<Message>> {
    val units = mutableListOf<List<Message>>()
    var i = 0
    while (i < messages.size) {
        val current = messages[i]
        if (current is Message.Assistant && current.toolCalls.isNotEmpty()) {
            val atomicGroup = mutableListOf<Message>(current)
            i++
            while (i < messages.size && messages[i] is Message.Tool) {
                atomicGroup.add(messages[i])
                i++
            }
            units.add(atomicGroup)
        } else {
            units.add(listOf(current))
            i++
        }
    }
    return units
}

internal fun estimateTokens(message: Message, estimator: TokenEstimator): Int {
    val text = when (message) {
        is Message.System -> message.text
        is Message.User -> message.parts.filterIsInstance<ContentPart.Text>().joinToString(" ") { it.text }
        is Message.Assistant -> {
            val partsText = message.parts.filterIsInstance<ContentPart.Text>().joinToString(" ") { it.text }
            val callsText = message.toolCalls.joinToString(" ") { "${it.name}:${it.arguments}" }
            "$partsText $callsText"
        }
        is Message.Tool -> "${message.toolName}:${message.result}"
    }
    return estimator.estimate(text)
}
