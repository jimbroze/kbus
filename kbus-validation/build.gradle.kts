description = "Validation rules and results for KBUS"

plugins {
    id("kbus.multiplatform")
    id("kbus.publish")
}

kotlin { sourceSets { commonTest.dependencies { implementation(libs.kotlin.test) } } }
