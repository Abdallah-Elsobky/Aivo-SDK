plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

sourceSets {
    main {
        kotlin.srcDirs("src/jvmMain/kotlin")
    }
}

dependencies {
    implementation(project(":sdk"))
    implementation(project(":sdk-testing"))
    implementation(libs.kotlinx.coroutines.core)
}

application {
    mainClass.set("com.aivo.samples.cli.MainKt")
}

