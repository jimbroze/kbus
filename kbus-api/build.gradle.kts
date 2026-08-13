description = "Handler-authoring API and annotations for KBUS message bus framework"

plugins {
    id("kbus.multiplatform")
    id("kbus.publish")
}

kotlin {
    sourceSets {
        commonMain.dependencies {}

        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}
