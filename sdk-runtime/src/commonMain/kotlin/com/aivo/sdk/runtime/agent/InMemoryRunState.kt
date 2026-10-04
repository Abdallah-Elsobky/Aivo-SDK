package com.aivo.sdk.runtime.agent

import com.aivo.sdk.core.model.RunState
import com.aivo.sdk.core.model.StateKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thread-safe in-memory blackboard for shared run state across agents.
 */
public class InMemoryRunState(
    initialData: Map<String, Any?> = emptyMap(),
) : RunState {

    private val mutex = Mutex()
    private val storage = mutableMapOf<String, Any?>().apply { putAll(initialData) }

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(key: StateKey<T>): T? {
        val raw = storage[key.name] ?: return null
        return raw as? T
    }

    override fun <T> set(key: StateKey<T>, value: T) {
        storage[key.name] = value
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> remove(key: StateKey<T>): T? {
        val raw = storage.remove(key.name) ?: return null
        return raw as? T
    }

    override fun contains(key: StateKey<*>): Boolean {
        return storage.containsKey(key.name)
    }

    override fun keys(): Set<String> {
        return storage.keys.toSet()
    }

    override fun snapshot(): Map<String, Any?> {
        return storage.toMap()
    }

    /**
     * Mutates the state with atomic lock across coroutines.
     */
    public suspend fun <R> withLock(block: (RunState) -> R): R {
        return mutex.withLock {
            block(this)
        }
    }
}
