description = "Code generation module for Kbus: A Kotlin message bus framework"

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.devtools.ksp)
    id("kbus.publish")
}

kotlin {
    jvmToolchain {
        (this).languageVersion.set(JavaLanguageVersion.of(libs.versions.jdk.target.get()))
    }
    jvm()
    sourceSets {
        commonMain.dependencies {
            implementation(projects.kbusAnnotations)
            implementation(projects.kbusCore)
            implementation(libs.symbol.processing.api)
        }
    }
}
