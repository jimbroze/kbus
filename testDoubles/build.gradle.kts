
plugins {
    id("kbus.multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":kbus-core"))
        }
    }
}
