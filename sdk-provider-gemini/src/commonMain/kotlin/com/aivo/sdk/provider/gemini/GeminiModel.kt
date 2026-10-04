package com.aivo.sdk.provider.gemini

import com.aivo.sdk.core.model.ProviderId
import com.aivo.sdk.core.model.ProviderModel

/**
 * Strongly-typed catalog of known Google Gemini models.
 */
public enum class GeminiModel(
    override val modelId: String,
    override val isFree: Boolean,
    override val displayName: String,
) : ProviderModel {

    // ── Free Tier Text Models ──
    GEMINI_3_8_FLASH("gemini-3.8-flash", true, "Gemini 3.8 Flash"),
    GEMINI_3_7_FLASH("gemini-3.7-flash", true, "Gemini 3.7 Flash"),
    GEMINI_3_6_FLASH("gemini-3.6-flash", true, "Gemini 3.6 Flash"),
    GEMINI_3_5_FLASH("gemini-3.5-flash", true, "Gemini 3.5 Flash"),
    GEMINI_3_5_FLASH_LITE("gemini-3.5-flash-lite", true, "Gemini 3.5 Flash Lite"),
    GEMINI_3_1_FLASH_LITE("gemini-3.1-flash-lite", true, "Gemini 3.1 Flash Lite"),
    GEMINI_3_FLASH_PREVIEW("gemini-3-flash-preview", true, "Gemini 3 Flash Preview"),
    // ── Paid-Only Text Models ──
    GEMINI_3_1_PRO_PREVIEW("gemini-3.1-pro-preview", false, "Gemini 3.1 Pro Preview");

    override val providerId: ProviderId
        get() = ProviderId("gemini")

    public companion object {
        public val default: GeminiModel = GEMINI_3_8_FLASH

        public val freeModels: List<GeminiModel> by lazy { entries.filter { it.isFree } }
        public val paidModels: List<GeminiModel> by lazy { entries.filter { !it.isFree } }

        public fun fromId(id: String): GeminiModel? =
            entries.firstOrNull { it.modelId.equals(id.trim(), ignoreCase = true) }
    }
}
