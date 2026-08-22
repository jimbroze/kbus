plugins { id("kbus.bus-module") }

generatedBus { indexPackage = "com.jimbroze.kbus.example.indexes" }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.kbusApi)
            implementation(projects.kbusCore)
            implementation(projects.kbusDomain)
            implementation(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// Knit owns these files and knitCheck verifies them.
tasks.withType<com.ncorti.ktfmt.gradle.tasks.KtfmtBaseTask>().configureEach {
    exclude("**/samples/**")
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach { exclude("**/samples/**") }
