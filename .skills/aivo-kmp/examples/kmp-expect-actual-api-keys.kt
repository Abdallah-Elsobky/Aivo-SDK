// -------------------------------------------------------------------------------------
// 1. commonMain/kotlin/com/example/shared/config/PlatformSecretProvider.kt
// -------------------------------------------------------------------------------------
package com.example.shared.config

/**
 * Common expect declaration for loading platform-secured API secrets.
 */
expect object PlatformSecretProvider {
    fun getApiKey(providerKey: String): String
}

// -------------------------------------------------------------------------------------
// 2. androidMain/kotlin/com/example/shared/config/PlatformSecretProvider.android.kt
// -------------------------------------------------------------------------------------
/*
package com.example.shared.config

import com.example.shared.BuildConfig

actual object PlatformSecretProvider {
    actual fun getApiKey(providerKey: String): String {
        return when (providerKey.lowercase()) {
            "gemini" -> BuildConfig.GEMINI_API_KEY
            "ollama" -> BuildConfig.OLLAMA_API_KEY
            "openrouter" -> BuildConfig.OPENROUTER_API_KEY
            else -> ""
        }
    }
}
*/

// -------------------------------------------------------------------------------------
// 3. iosMain/kotlin/com/example/shared/config/PlatformSecretProvider.ios.kt
// -------------------------------------------------------------------------------------
/*
package com.example.shared.config

import platform.Foundation.NSBundle
import platform.Foundation.NSProcessInfo

actual object PlatformSecretProvider {
    actual fun getApiKey(providerKey: String): String {
        val envKey = providerKey.uppercase() + "_API_KEY"
        val processEnv = NSProcessInfo.processInfo.environment[envKey] as? String
        if (!processEnv.isNullOrBlank()) return processEnv

        val bundleValue = NSBundle.mainBundle.objectForInfoDictionaryKey(envKey) as? String
        return bundleValue ?: ""
    }
}
*/

// -------------------------------------------------------------------------------------
// 4. jvmMain / desktopMain/kotlin/com/example/shared/config/PlatformSecretProvider.jvm.kt
// -------------------------------------------------------------------------------------
/*
package com.example.shared.config

actual object PlatformSecretProvider {
    actual fun getApiKey(providerKey: String): String {
        val envKey = providerKey.uppercase() + "_API_KEY"
        return System.getenv(envKey) ?: System.getProperty(envKey) ?: ""
    }
}
*/
