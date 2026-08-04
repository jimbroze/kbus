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

include("kbus-generation-fixtures")

include("kbus-generation-fixtures-sub")

include("examples:app")

include("examples:docs-samples")

listOf(
        "orders" to listOf("contracts", "domain", "application", "infrastructure", "acl"),
        "inventory" to listOf("contracts", "domain", "application", "infrastructure"),
    )
    .forEach { (context, layers) ->
        layers.forEach { layer -> include("examples:contexts:$context:$layer") }
    }
