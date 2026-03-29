description = "Kotlin message bus framework"

plugins {
    id("kbus.multiplatform")
    id("kbus.publish")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.devtools.ksp)
    alias(libs.plugins.kotest)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.kbusContracts)
            api(projects.kbusDomain)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.kotest.framework)
            implementation(libs.kotest.assertions)
            implementation(projects.testDoubles)
        }
    }
}
