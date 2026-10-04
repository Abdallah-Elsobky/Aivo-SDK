package com.aivo.sdk.ios

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Handle to a running asynchronous operation that can be cancelled from Swift/Objective-C.
 */
public interface CancellableJob {
    public fun cancel()
    public val isCancelled: Boolean
}

/**
 * Bridges a Kotlin [Flow] to Swift / Objective-C with callback-based collection and cancellation.
 */
public class FlowAdapter<T : Any>(private val flow: Flow<T>) {

    /**
     * Subscribes to the flow with callbacks for items, completion, and errors.
     *
     * @return a [CancellableJob] allowing the caller to terminate collection from Swift.
     */
    public fun watch(
        onEach: (T) -> Unit,
        onComplete: () -> Unit = {},
        onError: (Throwable) -> Unit = {},
    ): CancellableJob {
        val scope = CoroutineScope(Dispatchers.Main)
        val job: Job = scope.launch {
            flow
                .catch { cause -> onError(cause) }
                .collect { item -> onEach(item) }
            onComplete()
        }

        return object : CancellableJob {
            override fun cancel() { job.cancel() }
            override val isCancelled: Boolean get() = job.isCancelled
        }
    }
}

/**
 * Convenience extension to convert any [Flow] into a Swift-friendly [FlowAdapter].
 */
public fun <T : Any> Flow<T>.asFlowAdapter(): FlowAdapter<T> = FlowAdapter(this)
