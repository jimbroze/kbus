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
        }
    }
}

dependencies { add("kspCommonMainMetadata", projects.kbusGeneration) }

ksp {
    arg("kbus.subModuleName", project.name)
    arg("kbus.indexPackage", "com.jimbroze.kbus.testing.indexes")
}

tasks.withType(KotlinCompilationTask::class).all {
    if (name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}

kotlin.sourceSets.commonMain { kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin") }
