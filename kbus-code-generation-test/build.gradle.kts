plugins {
//    kotlin("jvm")
    kotlin("multiplatform")
    alias(libs.plugins.devtools.ksp)
}

kotlin {
    jvm()
    sourceSets {
        commonMain.dependencies {
            implementation(projects.kbusCore)
            implementation(projects.kbusAnnotations)
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
    add("kspJvm", projects.kbusCodeGeneration)
//    implementation(kotlin("stdlib-jdk8"))
}
