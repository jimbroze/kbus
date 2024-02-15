plugins {
    id("kbus.multiplatform")
    id("kbus.publish")
}

version = "1.0-SNAPSHOT"

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
