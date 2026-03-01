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

kotlin.targets.configureEach {
    if (name == "metadata") return@configureEach

    val targetCapitalized = name.replaceFirstChar { it.uppercaseChar() }

    dependencies {
        add("ksp${targetCapitalized}Test", projects.kbusGeneration)
        //        add("ksp$targetCapitalized", projects.kbusGeneration)
    }
}

ksp {
    arg("kbus.subModuleName", project.name)
    arg("kbus.indexPackage", "com.jimbroze.kbus.testing.indexes")
}

kotlin.sourceSets.commonMain { kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin") }

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    if (name.startsWith("compile")) {
        val kspTaskName = name.replaceFirst("compile", "ksp")
        dependsOn(tasks.matching { it.name == kspTaskName })
        dependsOn(tasks.matching { it.name == "kspCommonMainKotlinMetadata" })
    }
}
