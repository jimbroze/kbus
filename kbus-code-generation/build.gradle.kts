plugins {
    kotlin("multiplatform")
    alias(libs.plugins.devtools.ksp)
}

kotlin {
    jvm()
    sourceSets {
        commonMain.dependencies {
            implementation(projects.kbusAnnotations)
            implementation(projects.kbusCore)
            implementation(libs.symbol.processing.api)
        }
    }
}
