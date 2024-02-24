rootProject.name = "kbus"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

include("kotlin-library")
include("testDoubles")
include("kbus-core")
include("kbus-koin")
include("kbus-annotations")
include("kbus-generation")
include("kbus-generation-test")

//includeBuild("core")
//includeBuild("koin")
