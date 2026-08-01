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

include("kbus-contracts")

include("kbus-domain")

include("kbus-core")

include("kbus-generation")

include("kbus-example")

include("kbus-example-sub")

include("kbus-example-sub-two")

include("kbus-example-sub-orders-app")
