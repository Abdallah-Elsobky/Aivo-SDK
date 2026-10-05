import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.bundling.Zip
import org.gradle.jvm.tasks.Jar
import org.gradle.plugins.signing.SigningExtension

plugins {
    // Applied false — each submodule opts in via its own build script.
    alias(libs.plugins.androidApplication)          apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform)        apply false
    alias(libs.plugins.composeCompiler)             apply false
    alias(libs.plugins.kotlinMultiplatform)         apply false
    alias(libs.plugins.kotlinJvm)                   apply false
    alias(libs.plugins.kotlinSerialization)         apply false
}

val sdkModules = setOf(
    "sdk",
    "sdk-core",
    "sdk-transport",
    "sdk-provider-ollama",
    "sdk-provider-openai-compatible",
    "sdk-provider-gemini",
    "sdk-middleware",
    "sdk-runtime",
    "sdk-agent-config",
    "sdk-testing"
)

subprojects {
    if (name in sdkModules) {
        apply(plugin = "maven-publish")
        apply(plugin = "signing")

        group = findProperty("GROUP")?.toString() ?: "io.github.abdallah-elsobky"
        version = findProperty("VERSION_NAME")?.toString() ?: "1.0.0"

        tasks.withType<GenerateModuleMetadata>().configureEach {
            if (name.contains("Android", ignoreCase = true)) {
                enabled = false
            }
        }

        extensions.configure<PublishingExtension> {
            publications.withType<MavenPublication>().configureEach {
                if (project.name == "sdk") {
                    artifactId = if (artifactId == "sdk") "aivo-sdk" else artifactId.replaceFirst("sdk", "aivo-sdk")
                }

                pom {
                    name.set(if (project.name == "sdk") "Aivo SDK" else "Aivo SDK - ${project.name}")
                    description.set(findProperty("POM_DESCRIPTION")?.toString() ?: "Production-grade, provider-agnostic Agentic AI SDK for Kotlin Multiplatform")
                    url.set(findProperty("POM_URL")?.toString() ?: "https://github.com/Abdallah-Elsobky/Aivo-SDK")

                    licenses {
                        license {
                            name.set(findProperty("POM_LICENSE_NAME")?.toString() ?: "The Apache License, Version 2.0")
                            url.set(findProperty("POM_LICENSE_URL")?.toString() ?: "https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }

                    developers {
                        developer {
                            id.set(findProperty("POM_DEVELOPER_ID")?.toString() ?: "abdallah-elsobky")
                            name.set(findProperty("POM_DEVELOPER_NAME")?.toString() ?: "Abdallah Elsobky")
                            url.set(findProperty("POM_URL")?.toString() ?: "https://github.com/Abdallah-Elsobky")
                        }
                    }

                    scm {
                        connection.set(findProperty("POM_SCM_CONNECTION")?.toString() ?: "scm:git:git://github.com/Abdallah-Elsobky/Aivo-SDK.git")
                        developerConnection.set(findProperty("POM_SCM_DEV_CONNECTION")?.toString() ?: "scm:git:ssh://github.com/Abdallah-Elsobky/Aivo-SDK.git")
                        url.set(findProperty("POM_SCM_URL")?.toString() ?: "https://github.com/Abdallah-Elsobky/Aivo-SDK")
                    }
                }
            }

            repositories {
                maven {
                    name = "SonatypeCentral"
                    url = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
                    credentials {
                        username = providers.environmentVariable("SONATYPE_USERNAME").orNull
                            ?: providers.gradleProperty("sonatype.username").orNull
                        password = providers.environmentVariable("SONATYPE_PASSWORD").orNull
                            ?: providers.gradleProperty("sonatype.password").orNull
                    }
                }
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/Abdallah-Elsobky/Aivo-SDK")
                    credentials {
                        username = providers.environmentVariable("GITHUB_ACTOR").orNull
                            ?: providers.gradleProperty("gpr.user").orNull
                        password = providers.environmentVariable("GITHUB_TOKEN").orNull
                            ?: providers.gradleProperty("gpr.key").orNull
                    }
                }
            }
        }

        extensions.configure<SigningExtension> {
            val signingKey = providers.environmentVariable("SIGNING_KEY").orNull
                ?: providers.gradleProperty("signing.key").orNull
            val signingPassword = providers.environmentVariable("SIGNING_PASSWORD").orNull
                ?: providers.gradleProperty("signing.password").orNull
            val signingKeyId = providers.environmentVariable("SIGNING_KEY_ID").orNull
                ?: providers.gradleProperty("signing.keyId").orNull

            val isSigningRequired = !signingKey.isNullOrBlank()
            isRequired = isSigningRequired

            if (isSigningRequired) {
                if (!signingKeyId.isNullOrBlank()) {
                    useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
                } else {
                    useInMemoryPgpKeys(signingKey, signingPassword)
                }
                val publishing = extensions.getByType<PublishingExtension>()
                sign(publishing.publications)
            }
        }
    }
}

// Helper task: Bundles all published artifacts for manual upload to central.sonatype.com if needed
tasks.register<Zip>("bundleForMavenCentral") {
    group = "publishing"
    description = "Zips all published Aivo SDK artifacts from mavenLocal for manual upload to central.sonatype.com"
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    archiveFileName.set("aivo-sdk-bundle-1.0.0.zip")

    val groupPath = (findProperty("GROUP")?.toString() ?: "io.github.abdallah-elsobky").replace('.', '/')
    val m2Dir = File(System.getProperty("user.home"), ".m2/repository/$groupPath")

    from(m2Dir) {
        into(groupPath)
    }
}