description = "Annotations for use with Kbus code generation"

plugins {
    id("kbus.multiplatform")
    id("kbus.publish")
}

kotlin { sourceSets { commonMain.dependencies {} } }
