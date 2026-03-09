description = "Domain-driven design abstractions for KBUS"

plugins {
    id("kbus.multiplatform")
    id("kbus.publish")
}

kotlin {
    sourceSets {
        commonMain.dependencies { implementation(projects.kbusContracts) }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
