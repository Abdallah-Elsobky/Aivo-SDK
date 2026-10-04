package com.aivo.sdk.provider.openai

import com.aivo.sdk.core.model.ProviderId
import com.aivo.sdk.core.model.ProviderModel

/**
 * Strongly-typed catalog of known OpenRouter models.
 */
public enum class OpenRouterModel(
    override val modelId: String,
    override val isFree: Boolean,
    override val displayName: String,
) : ProviderModel {

    // ── Free Models ──
    LING_3_0_FLASH_SANTE_FREE(
        "inclusionai/ling-3.0-flash-sante:free",
        true,
        "Ling 3.0 Flash Sante (Free)"
    ),
    LING_3_0_FLASH_FIN_FREE(
        "inclusionai/ling-3.0-flash-fin:free",
        true,
        "Ling 3.0 Flash Fin (Free)"
    ),
    QWEN3_8_27B_FREE(
        "qwen/qwen3.8-27b:free",
        true,
        "Qwen 3.8 27B (Free)"
    ),
    DOTS_3_NOTE_PREVIEW_FREE(
        "dots-studio/dots-3-note-preview:free",
        true,
        "Dots 3 Note Preview (Free)"
    ),
    LFM_2_5_2_6B_FREE(
        "liquid/lfm-2.5-2.6b:free",
        true,
        "Liquid LFM 2.5 2.6B (Free)"
    ),
    NEMOTRON_3_5_LIGHTNING_FREE(
        "nvidia/nemotron-3.5-lightning:free",
        true,
        "Nemotron 3.5 Lightning (Free)"
    ),
    LAGUNA_S_2_1_FREE(
        "poolside/laguna-s-2.1:free",
        true,
        "Laguna S 2.1 (Free)"
    ),
    LAGUNA_XS_2_1_FREE(
        "poolside/laguna-xs-2.1:free",
        true,
        "Laguna XS 2.1 (Free)"
    ),
    NORTH_MINI_CODE_FREE(
        "cohere/north-mini-code:free",
        true,
        "North Mini Code (Free)"
    ),
    NEMOTRON_3_ULTRA_FREE(
        "nvidia/nemotron-3-ultra-550b-a55b:free",
        true,
        "Nemotron 3 Ultra (Free)"
    ),
    GEMMA_4_26B_A4B_FREE(
        "google/gemma-4-26b-a4b-it:free",
        true,
        "Gemma 4 26B A4B (Free)"
    ),
    NEMOTRON_3_SUPER_FREE(
        "nvidia/nemotron-3-super-120b-a12b:free",
        true,
        "Nemotron 3 Super (Free)"
    ),

    GPT_OSS_120B(
        "openai/gpt-oss-120b",
        false,
        "GPT-OSS 120B"
    ),
    GPT_OSS_20B(
        "openai/gpt-oss-20b",
        false,
        "GPT-OSS 20B"
    ),

    // ── Paid Tier Models ──
    MUSE_SPARK_1_1(
        "meta/muse-spark-1.1",
        false,
        "Meta Muse Spark 1.1"
    ),
    KAT_CODER_PRO_V2_5(
        "kwaipilot/kat-coder-pro-v2.5",
        false,
        "Kat Coder Pro v2.5"
    ),
    GPT_5_6_LUNA_PRO(
        "openai/gpt-5.6-luna-pro",
        false,
        "GPT-5.6 Luna Pro"
    ),
    GPT_5_6_LUNA_PRO_BATCH(
        "openai/gpt-5.6-luna-pro:batch",
        false,
        "GPT-5.6 Luna Pro (Batch)"
    ),
    GPT_5_6_LUNA(
        "openai/gpt-5.6-luna",
        false,
        "GPT-5.6 Luna"
    ),
    GPT_5_6_LUNA_BATCH(
        "openai/gpt-5.6-luna:batch",
        false,
        "GPT-5.6 Luna (Batch)"
    ),
    GPT_5_6_TERRA_PRO(
        "openai/gpt-5.6-terra-pro",
        false,
        "GPT-5.6 Terra Pro"
    ),
    GPT_5_6_TERRA_PRO_BATCH(
        "openai/gpt-5.6-terra-pro:batch",
        false,
        "GPT-5.6 Terra Pro (Batch)"
    ),
    GPT_5_6_TERRA(
        "openai/gpt-5.6-terra",
        false,
        "GPT-5.6 Terra"
    ),
    GPT_5_6_TERRA_BATCH(
        "openai/gpt-5.6-terra:batch",
        false,
        "GPT-5.6 Terra (Batch)"
    ),
    GPT_5_6_SOL_PRO(
        "openai/gpt-5.6-sol-pro",
        false,
        "GPT-5.6 Sol Pro"
    ),
    GPT_5_6_SOL_PRO_BATCH(
        "openai/gpt-5.6-sol-pro:batch",
        false,
        "GPT-5.6 Sol Pro (Batch)"
    ),
    GPT_5_6_SOL(
        "openai/gpt-5.6-sol",
        false,
        "GPT-5.6 Sol"
    ),
    GPT_5_6_SOL_BATCH(
        "openai/gpt-5.6-sol:batch",
        false,
        "GPT-5.6 Sol (Batch)"
    ),
    GROK_4_5(
        "x-ai/grok-4.5",
        false,
        "Grok 4.5"
    ),
    GROK_LATEST(
        "~x-ai/grok-latest",
        false,
        "Grok Latest"
    ),
    AION_3_0(
        "aion-labs/aion-3.0",
        false,
        "Aion 3.0"
    ),
    TENCENT_HY3(
        "tencent/hy3",
        false,
        "Tencent HY3"
    ),
    LAGUNA_XS_2_1(
        "poolside/laguna-xs-2.1",
        false,
        "Laguna XS 2.1"
    ),
    CLAUDE_SONNET_5_BATCH(
        "anthropic/claude-sonnet-5:batch",
        false,
        "Claude Sonnet 5 (Batch)"
    ),
    CLAUDE_SONNET_5(
        "anthropic/claude-sonnet-5",
        false,
        "Claude Sonnet 5"
    ),
    NANO_BANANA_2_LITE(
        "google/gemini-3.1-flash-lite-image",
        false,
        "Nano Banana 2 Lite"
    ),
    FUGU_ULTRA(
        "sakana/fugu-ultra",
        false,
        "Fugu Ultra"
    ),
    NANO_BANANA_2(
        "google/gemini-3.1-flash-image",
        false,
        "Nano Banana 2"
    ),
    NANO_BANANA_PRO(
        "google/gemini-3-pro-image",
        false,
        "Nano Banana Pro"
    ),
    GLM_5_2_PAID(
        "z-ai/glm-5.2",
        false,
        "GLM 5.2"
    ),
    OPENROUTER_FUSION(
        "openrouter/fusion",
        false,
        "OpenRouter Fusion"
    ),
    KIMI_K2_7_CODE_PAID(
        "moonshotai/kimi-k2.7-code",
        false,
        "Kimi K2.7 Code"
    ),
    CLAUDE_FABLE_5(
        "anthropic/claude-fable-5",
        false,
        "Claude Fable 5"
    ),
    CLAUDE_FABLE_5_BATCH(
        "anthropic/claude-fable-5:batch",
        false,
        "Claude Fable 5 (Batch)"
    ),
    NEMOTRON_3_5_CONTENT_SAFETY(
        "nvidia/nemotron-3.5-content-safety",
        false,
        "Nemotron 3.5 Content Safety"
    ),
    NEMOTRON_3_ULTRA(
        "nvidia/nemotron-3-ultra-550b-a55b",
        false,
        "Nemotron 3 Ultra"
    ),
    QWEN3_7_PLUS(
        "qwen/qwen3.7-plus",
        false,
        "Qwen 3.7 Plus"
    ),
    CLAUDE_OPUS_4_1_BATCH(
        "anthropic/claude-opus-4.1:batch",
        false,
        "Claude Opus 4.1 (Batch)"
    ),
    GEMINI_2_5_PRO_PREVIEW(
        "google/gemini-2.5-pro-preview-06-05",
        false,
        "Gemini 2.5 Pro Preview"
    );

    override val providerId: ProviderId
        get() = ProviderId("openrouter")

    public companion object {
        public val default: OpenRouterModel = LING_3_0_FLASH_SANTE_FREE

        public val freeModels: List<OpenRouterModel> by lazy { entries.filter { it.isFree } }
        public val paidModels: List<OpenRouterModel> by lazy { entries.filter { !it.isFree } }

        public fun fromId(id: String): OpenRouterModel? =
            entries.firstOrNull { it.modelId.equals(id.trim(), ignoreCase = true) }
    }
}
