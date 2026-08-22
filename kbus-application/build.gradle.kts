description = "Invocation-scoped dependencies a KBUS handler is given"

plugins {
    id("kbus.multiplatform")
    id("kbus.publish")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.kbusApi)
            api(projects.kbusDomain)
        }

        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}
