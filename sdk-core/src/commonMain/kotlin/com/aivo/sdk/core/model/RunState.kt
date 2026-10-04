package com.aivo.sdk.core.model

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

/**
 * Strongly-typed key for accessing shared run state on the blackboard.
 */
@JvmInline
@Serializable
public value class StateKey<T>(public val name: String) {
    init {
        require(name.isNotBlank()) { "StateKey name must not be blank" }
    }

    public companion object {
        public fun string(name: String): StateKey<String> = StateKey(name)
        public fun int(name: String): StateKey<Int> = StateKey(name)
        public fun boolean(name: String): StateKey<Boolean> = StateKey(name)
        public inline fun <reified T> of(name: String): StateKey<T> = StateKey(name)
    }

    override fun toString(): String = name
}

/**
 * Thread-safe blackboard state shared across a root agent run and its child runs/delegations.
 */
public interface RunState {
    public operator fun <T> get(key: StateKey<T>): T?
    public operator fun <T> set(key: StateKey<T>, value: T)
    public fun <T> remove(key: StateKey<T>): T?
    public fun contains(key: StateKey<*>): Boolean
    public fun keys(): Set<String>
    public fun snapshot(): Map<String, Any?>
}
