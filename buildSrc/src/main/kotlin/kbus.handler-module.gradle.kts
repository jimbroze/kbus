import com.google.devtools.ksp.gradle.KspExtension

plugins {
    id("kbus.multiplatform")
    id("com.google.devtools.ksp")
}

interface BoundedContextExtension {
    /** The bounded context this module's handlers belong to. Several modules share one identity. */
    val identity: Property<String>
}

val boundedContext = extensions.create<BoundedContextExtension>("boundedContext")

dependencies { add("kspCommonMainMetadata", project(":kbus-generation")) }

kotlin.sourceSets.commonMain { kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin") }

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    if (name.startsWith("compile")) {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}

// An index class name and package are derived from the submodule name, which therefore has to be
// unique across the build.
afterEvaluate {
    val identity = boundedContext.identity.get()
    extensions.configure<KspExtension> {
        arg("kbus.subModuleName", project.name)
        arg("kbus.boundedContextIdentity", identity)
        arg("kbus.indexPackage", "com.jimbroze.kbus.example.indexes")
    }
}
