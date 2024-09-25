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

            implementation(libs.kotlinx.datetime)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

dependencies { add("kspCommonMainMetadata", projects.kbusGeneration) }

tasks.withType<org.jetbrains.kotlin.gradle.dsl.KotlinCompile<*>>().all {
    if (name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}

kotlin.sourceSets.commonMain { kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin") }
