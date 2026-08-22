description = "Ports an adapter implements, and the in-memory reference adapters KBUS ships"

plugins {
    id("kbus.multiplatform")
    id("kbus.publish")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.kbusApi)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(projects.testDoubles)
        }
    }
}
