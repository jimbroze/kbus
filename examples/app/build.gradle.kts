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

            implementation(projects.examples.contexts.orders.contracts)
            implementation(projects.examples.contexts.orders.domain)
            implementation(projects.examples.contexts.orders.application)
            implementation(projects.examples.contexts.orders.infrastructure)
            implementation(projects.examples.contexts.orders.acl)

            implementation(projects.examples.contexts.inventory.contracts)
            implementation(projects.examples.contexts.inventory.domain)
            implementation(projects.examples.contexts.inventory.application)
            implementation(projects.examples.contexts.inventory.infrastructure)

            implementation(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
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

ksp { arg("kbus.indexPackage", "com.jimbroze.kbus.example.indexes") }
