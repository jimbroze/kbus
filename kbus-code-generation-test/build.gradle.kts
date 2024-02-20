plugins {
//    kotlin("jvm")
    kotlin("multiplatform")
    alias(libs.plugins.devtools.ksp)
}

kotlin {
    jvm()
    js {  }
    sourceSets {
        commonMain.dependencies {
            implementation(projects.kbusCore)
            implementation(projects.kbusAnnotations)

            implementation(libs.kotlinx.datetime)
        }

//        commonTest.dependencies {
//            implementation(libs.kotlin.test)
//            implementation(libs.kotlinx.coroutines.test)
////            implementation(project(":kbus-code-generation"))
////            ksp(project(":kbus-code-generation"))
//        }
    }
}

dependencies {
//    implementation(projects.kbusCore)
    add("kspCommonMainMetadata", projects.kbusCodeGeneration)
//    add("kspJvm", projects.kbusCodeGeneration)
//    implementation(kotlin("stdlib-jdk8"))
}

tasks.withType<org.jetbrains.kotlin.gradle.dsl.KotlinCompile<*>>().all {
    if(name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}

kotlin.sourceSets.commonMain {
    kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
}
