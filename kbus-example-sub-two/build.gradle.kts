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

ksp {
    arg("kbus.subModuleName", project.name)
    arg("kbus.moduleIdentity", "inventory")
    arg("kbus.indexPackage", "com.jimbroze.kbus.testing.indexes")
}
