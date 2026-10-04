package com.aivo.aivosdk.agent.tools

import com.aivo.sdk.ParamType
import com.aivo.sdk.core.port.Tool
import com.aivo.sdk.tool

/**
 * Sample tool: returns simulated cryptocurrency prices.
 *
 * In a production app, the [execute] block would call a real crypto API
 * (e.g., CoinGecko, Binance). This sample returns static values so the
 * demo works offline.
 *
 * Demonstrates multi-argument aliasing — the tool accepts "symbol", "crypto",
 * or "coin" as parameter names for flexibility.
 */
val cryptoTool: Tool = tool("crypto_price", "Fetch current cryptocurrency price") {
    param("symbol", "Crypto symbol (e.g. BTC, ETH, SOL)", type = ParamType.String, required = true)
    execute { args ->
        val sym = (args.stringOrNull("symbol")
            ?: args.stringOrNull("crypto")
            ?: args.stringOrNull("coin")
            ?: "BTC").uppercase()
        // TODO: Replace with a real crypto price API call in production.
        when (sym) {
            "BTC" -> "Bitcoin (BTC): \$68,450 USD (+3.2% in 24h)"
            "ETH" -> "Ethereum (ETH): \$3,520 USD (+1.8% in 24h)"
            "SOL" -> "Solana (SOL): \$154 USD (-0.5% in 24h)"
            else  -> "$sym price: unavailable (simulated offline stub)"
        }
    }
}
