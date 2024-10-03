plugins {
    id("kbus.multiplatform")
    id("kbus.publish")
}

group = "com.jimbroze"

version = "1.0-SNAPSHOT"

kotlin { sourceSets { commonMain.dependencies {} } }
