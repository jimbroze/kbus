plugins {
    id("kbus.multiplatform")
    alias(libs.plugins.devtools.ksp)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.kbusContracts)
            implementation(projects.kbusCore)
            implementation(projects.testDoubles)
            implementation(projects.kbusExampleSub)
            implementation(projects.kbusExampleSubTwo)

            implementation(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

dependencies { add("kspCommonMainMetadata", projects.kbusGeneration) }

kotlin.sourceSets.commonMain { kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin") }

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    if (name.startsWith("compile")) {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}

ksp { arg("kbus.indexPackage", "com.jimbroze.kbus.testing.indexes") }

// Exclude Knit-generated samples from ktfmt and detekt — Knit owns these files and knitCheck
// verifies them
tasks.withType<com.ncorti.ktfmt.gradle.tasks.KtfmtBaseTask>().configureEach {
    exclude("**/samples/**")
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach { exclude("**/samples/**") }
