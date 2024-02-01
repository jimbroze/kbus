import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
//    alias(libs.plugins.kotlinAndroid)
//    id("org.jetbrains.kotlin.android") version "2.0.0-Beta3" apply false
    `maven-publish`
}

group = "com.jimbroze"
version = "1.0-SNAPSHOT"

publishing {
    repositories {
        mavenLocal()
    }
}

kotlin {
//    js(IR) {
//        browser()
//        nodejs()
//    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
//        browser()
//        nodejs()
    }
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
        publishAllLibraryVariants()
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    jvm()

    applyDefaultHierarchyTemplate()

    sourceSets {
//        val instantiator by creating {
//            dependsOn(commonMain.get())
//        }
//
//        val koin by creating {
//            dependsOn(commonMain.get())
//        }

        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

//        koin.dependencies {
//            implementation(libs.koin.core)
//        }
    }
}

sourceSets {
    create("instantiator") {
        kotlin {
//            srcDir("src/instantiator/")
//            dependsOn(commonMain.get())
        }
    }
    create("koin") {
        kotlin {
//            srcDir("src/koin/")
        }
    }
}

dependencies {
    "instantiatorImplementation"(kotlin("reflect"))
    "koinImplementation"(libs.koin.core)
}

java {
    registerFeature("instantiator") {
        usingSourceSet(sourceSets["instantiator"])
    }
    registerFeature("koin") {
        usingSourceSet(sourceSets["koin"])
    }
}

android {
    namespace = "org.jimbroze.kbus"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    sourceSets["main"].resources.srcDirs("src/commonMain/resources")

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

//rootProject.plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin> {
//    rootProject.the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension>().download = false
//}
