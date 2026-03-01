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
            implementation(projects.kbusGenerationTestSub)

            implementation(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

dependencies { add("kspCommonMainMetadata", projects.kbusGeneration) }

kotlin.targets.configureEach {
    if (name == "metadata") return@configureEach

    val targetCapitalized = name.replaceFirstChar { it.uppercaseChar() }

    dependencies {
        add("ksp${targetCapitalized}Test", projects.kbusGeneration)
        //        add("ksp$targetCapitalized", projects.kbusGeneration)
    }
}

ksp { arg("kbus.indexPackage", "com.jimbroze.kbus.testing.indexes") }

// tasks.withType(KotlinCompilationTask::class).all {
//    if (name != "kspCommonMainKotlinMetadata") {
//        dependsOn("kspCommonMainKotlinMetadata")
//    }
// }

kotlin.sourceSets.commonMain { kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin") }

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    if (name.startsWith("compile")) {
        val kspTaskName = name.replaceFirst("compile", "ksp")
        dependsOn(tasks.matching { it.name == kspTaskName })
        dependsOn(tasks.matching { it.name == "kspCommonMainKotlinMetadata" })
    }
}
