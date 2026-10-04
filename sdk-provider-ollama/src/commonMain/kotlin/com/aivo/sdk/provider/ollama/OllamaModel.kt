package com.aivo.sdk.provider.ollama

import com.aivo.sdk.core.model.ProviderId
import com.aivo.sdk.core.model.ProviderModel

/**
 * Strongly-typed catalog of known Ollama models.
 */
public enum class OllamaModel(
    override val modelId: String,
    override val isFree: Boolean,
    override val displayName: String,
) : ProviderModel {

    // ── Free Models ──
    GEMMA4_31B("gemma4:31b", true, "Gemma 4 (31B)"),
    GPT_OSS_120B("gpt-oss:120b", true, "GPT-OSS (120B)"),
    GPT_OSS_20B("gpt-oss:20b", true, "GPT-OSS (20B)"),
    NEMOTRON_3_NANO_30B("nemotron-3-nano:30b", true, "Nemotron 3 Nano (30B)"),
    NEMOTRON_3_SUPER("nemotron-3-super", true, "Nemotron 3 Super"),
    NEMOTRON_3_ULTRA("nemotron-3-ultra", true, "Nemotron 3 Ultra"),

    // ── Paid Tier Models ──
    DEEPSEEK_V4_1_FLASH("deepseek-v4.1-flash", false, "DeepSeek v4.1 Flash"),
    GLM_5_3("glm-5.3", false, "GLM 5.3"),
    GLM_5_3_FLASH("glm-5.3-flash", false, "GLM 5.3 Flash"),
    MINIMAX_M3("minimax-m3", false, "MiniMax M3"),
    GLM_5_2("glm-5.2", false, "GLM 5.2"),
    DEEPSEEK_V4_PRO("deepseek-v4-pro", false, "DeepSeek v4 Pro"),
    KIMI_K2_7_CODE("kimi-k2.7-code", false, "Kimi K2.7 Code"),
    KIMI_K3("kimi-k3", false, "Kimi K3"),
    MINIMAX_M2_7("minimax-m2.7", false, "MiniMax M2.7"),
    KIMI_K2_6("kimi-k2.6", false, "Kimi K2.6"),
    MISTRAL_LARGE_3("mistral-large-3", false, "Mistral Large 3");

    override val providerId: ProviderId
        get() = ProviderId("ollama")

    public companion object {
        public val default: OllamaModel = GPT_OSS_120B

        public val freeModels: List<OllamaModel> by lazy { entries.filter { it.isFree } }
        public val paidModels: List<OllamaModel> by lazy { entries.filter { !it.isFree } }

        public fun fromId(id: String): OllamaModel? =
            entries.firstOrNull { it.modelId.equals(id.trim(), ignoreCase = true) }
    }
}
