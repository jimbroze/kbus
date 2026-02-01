import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.devtools.ksp)
}

kotlin {
    jvm()
    js {}
    sourceSets {
        commonMain.dependencies {
            implementation(projects.kbusAnnotations)
            implementation(projects.kbusCore)
            implementation(projects.testDoubles)

            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

dependencies { add("kspCommonMainMetadata", projects.kbusGeneration) }

tasks.withType(KotlinCompilationTask::class).all {
    if (name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}

kotlin.sourceSets.commonMain { kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin") }
