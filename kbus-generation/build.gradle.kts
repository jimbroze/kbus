plugins {
    kotlin("multiplatform")
    alias(libs.plugins.devtools.ksp)
    id("kbus.publish")
}

version = "1.0-SNAPSHOT"

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
