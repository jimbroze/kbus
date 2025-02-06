description = "A module to use Koin with Kbus: A Kotlin message bus framework"

plugins {
    id("kbus.multiplatform")
    id("kbus.publish")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":kbus-core"))
            implementation(libs.koin.core)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(project(":testDoubles"))
        }
    }
}
