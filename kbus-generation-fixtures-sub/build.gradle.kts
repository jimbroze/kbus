plugins {
    id("kbus.multiplatform")
    id("com.google.devtools.ksp")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.kbusContracts)
            implementation(projects.kbusCore)
            implementation(projects.kbusDomain)
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
    arg("kbus.boundedContextIdentity", "depot")
    arg("kbus.indexPackage", "com.jimbroze.kbus.generation.fixtures.indexes")
}
