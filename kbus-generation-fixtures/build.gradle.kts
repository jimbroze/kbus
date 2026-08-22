plugins {
    id("kbus.multiplatform")
    id("com.google.devtools.ksp")
    id("com.jimbroze.kbus.bus")
}

kbus { indexPackage = "com.jimbroze.kbus.generation.fixtures.indexes" }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.kbusApi)
            implementation(projects.kbusCore)
            implementation(projects.testDoubles)
            implementation(projects.kbusGenerationFixturesSub)

            implementation(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// Exclude Knit-generated samples from ktfmt and detekt — Knit owns these files and knitCheck
// verifies them
tasks.withType<com.ncorti.ktfmt.gradle.tasks.KtfmtBaseTask>().configureEach {
    exclude("**/samples/**")
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach { exclude("**/samples/**") }
