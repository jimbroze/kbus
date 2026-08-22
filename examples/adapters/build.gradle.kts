plugins { id("kbus.multiplatform") }

// Deliberately depends on kbus-infrastructure alone. An adapter module writing against the port
// surface must never need the bus, so a port that drifts into kbus-core breaks this build.
kotlin {
    sourceSets {
        commonMain.dependencies { implementation(projects.kbusInfrastructure) }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
