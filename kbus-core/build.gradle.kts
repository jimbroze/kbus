description = "Kotlin message bus framework"

plugins {
    id("kbus.multiplatform")
    id("kbus.publish")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            //            implementation(project(":kbus-code-generation"))
            //            ksp(project(":kbus-code-generation"))
        }
    }
}
