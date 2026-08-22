import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    id("kbus.multiplatform")
    id("com.google.devtools.ksp")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.kbusApi)
            implementation(projects.kbusCore)
            implementation(projects.kbusDomain)
            implementation(projects.testDoubles)
        }
    }
}

// Wired by hand rather than through the kbus Gradle plugin: this is the one module proving the raw
// KSP arguments and task ordering still work without it.
dependencies { add("kspCommonMainMetadata", projects.kbusGeneration) }

kotlin.sourceSets.commonMain { kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin") }

tasks.withType<KotlinCompilationTask<*>>().configureEach {
    if (name.startsWith("compile")) {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}

tasks
    .matching { it.name.startsWith("ksp") && it.name != "kspCommonMainKotlinMetadata" }
    .configureEach { dependsOn("kspCommonMainKotlinMetadata") }

ksp {
    arg("kbus.subModuleName", project.name)
    arg("kbus.boundedContextIdentity", "depot")
    arg("kbus.indexPackage", "com.jimbroze.kbus.generation.fixtures.indexes")
}
