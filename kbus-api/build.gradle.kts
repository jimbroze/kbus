description = "Handler-authoring API and annotations for KBUS message bus framework"

plugins {
    id("kbus.multiplatform")
    id("kbus.publish")
}

kotlin {
    sourceSets {
        commonMain.dependencies { api(libs.kotlinx.coroutines.core) }

        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}
