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

include("kbus-api")

include("kbus-domain")

include("kbus-application")

include("kbus-core")

include("kbus-generation")

include("kbus-generation-fixtures")

include("kbus-generation-fixtures-sub")

include("examples:app")

include("examples:app-contract")

include("examples:app-manual")

include("examples:docs-samples")

// A Gradle module's identity is its group plus its *name*, and a name is only the last path
// segment — so a layer named per context would give two modules one identity, which Gradle
// resolves to whichever it saw first. The name carries the context; the directories still nest.
listOf(
        "orders" to listOf("contracts", "domain", "application", "infrastructure", "acl"),
        "inventory" to listOf("contracts", "domain", "application", "infrastructure"),
    )
    .forEach { (context, layers) ->
        layers.forEach { layer ->
            include("examples:contexts:$context-$layer")
            project(":examples:contexts:$context-$layer").projectDir =
                file("examples/contexts/$context/$layer")
        }
    }
