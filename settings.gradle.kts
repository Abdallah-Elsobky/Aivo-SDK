rootProject.name = "AivoSdk"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

// ─── SDK Modules ───────────────────────────────────────────────────────────
include(":sdk-core")
include(":sdk-transport")
include(":sdk-provider-ollama")
include(":sdk-provider-openai-compatible")
include(":sdk-provider-gemini")
include(":sdk-middleware")
include(":sdk-runtime")
include(":sdk-agent-config")
include(":sdk")
include(":sdk-testing")

// ─── App Modules ───────────────────────────────────────────────────────────
include(":androidApp")
include(":samples:cli-jvm")