description = "Kotlin message bus framework"

plugins {
    id("kbus.multiplatform")
    id("kbus.publish")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlinx.atomicfu)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.kbusApi)
            api(projects.kbusDomain)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            //            implementation(libs.kotlinx.atomicfu)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(projects.testDoubles)
        }
    }
}
