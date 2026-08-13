description = "Code generation module for Kbus: A Kotlin message bus framework"

plugins {
    kotlin("multiplatform")
    id("com.google.devtools.ksp")
    id("kbus.publish")
}

kotlin {
    jvmToolchain {
        (this).languageVersion.set(JavaLanguageVersion.of(libs.versions.jdk.target.get()))
    }
    jvm()
    sourceSets {
        commonMain.dependencies {
            implementation(projects.kbusApi)
            implementation(projects.kbusCore)
            implementation(libs.symbol.processing.api)
            implementation(libs.kotlin.poet)
            implementation(libs.kotlin.poet.ksp)
        }

        commonTest.dependencies { implementation(libs.kotlin.test) }

        // Compiling a processor's rejections needs a real compiler and a real KSP, so this runs
        // only where both exist. The pinned versions keep the harness on the same compiler and
        // KSP the rest of the build uses, rather than the ones it happens to ship with.
        jvmTest.dependencies {
            implementation(libs.kctfork.ksp)
            implementation(libs.kotlin.compiler.embeddable)
            implementation(libs.symbol.processing)
            implementation(libs.symbol.processing.aa.embeddable)
            implementation(libs.symbol.processing.common.deps)
        }
    }
}
