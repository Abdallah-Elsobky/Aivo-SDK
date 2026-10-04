@file:Suppress("DEPRECATION")
package com.aivo.aivosdk.agent

/**
 * @deprecated Renamed to [SampleAgentRegistry]. Use [SampleAgentRegistry] instead.
 */
@Deprecated(
    message = "Renamed to SampleAgentRegistry. Update your import.",
    replaceWith = ReplaceWith("SampleAgentRegistry", "com.aivo.aivosdk.agent.SampleAgentRegistry"),
    level = DeprecationLevel.ERROR,
)
typealias DemoAgentRegistry = SampleAgentRegistry
