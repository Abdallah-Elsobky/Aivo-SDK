import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    explicitApi()

    android {
        namespace = "com.aivo.sdk"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }
    iosArm64()
    iosSimulatorArm64()
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(project(":sdk-core"))
            api(project(":sdk-runtime"))
            api(project(":sdk-transport"))
            api(project(":sdk-middleware"))
            api(project(":sdk-agent-config"))
            api(project(":sdk-provider-ollama"))
            api(project(":sdk-provider-openai-compatible"))
            api(project(":sdk-provider-gemini"))
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            implementation(project(":sdk-testing"))
        }
    }
}
