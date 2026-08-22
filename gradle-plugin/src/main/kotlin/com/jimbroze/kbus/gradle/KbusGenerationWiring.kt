package com.jimbroze.kbus.gradle

import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

internal const val KOTLIN_MULTIPLATFORM_PLUGIN_ID = "org.jetbrains.kotlin.multiplatform"
internal const val KSP_PLUGIN_ID = "com.google.devtools.ksp"

private const val GENERATION_COORDINATES = "com.jimbroze:kbus-generation"
private const val GENERATION_VERSION_PROPERTY = "kbus.generationVersion"
private const val GENERATED_SOURCE_DIR = "build/generated/ksp/metadata/commonMain/kotlin"
private const val METADATA_KSP_TASK = "kspCommonMainKotlinMetadata"
private const val METADATA_KSP_CONFIGURATION = "kspCommonMainMetadata"
private const val METADATA_CLASSPATH = "commonMainResolvableDependenciesMetadata"

/**
 * Runs kbus generation once over common metadata, rather than once per target, so that what it
 * writes is common code every target compiles.
 */
internal fun Project.wireKbusGeneration(arguments: Provider<List<String>>) {
    requireMultiplatformAndKsp()

    dependencies.add(METADATA_KSP_CONFIGURATION, "$GENERATION_COORDINATES:$generationVersion")

    the<KotlinMultiplatformExtension>().sourceSets.named("commonMain").configure {
        kotlin.srcDir(GENERATED_SOURCE_DIR)
    }

    tasks.withType<KotlinCompilationTask<*>>().configureEach {
        if (name.startsWith("compile")) {
            dependsOn(METADATA_KSP_TASK)
        }
    }

    // Every per-target KSP task reads commonMain, which is where the metadata task writes.
    tasks
        .matching { it.name.startsWith("ksp") && it.name != METADATA_KSP_TASK }
        .configureEach { dependsOn(METADATA_KSP_TASK) }

    extensions.configure<KspExtension> { arg(CommandLineArgumentProvider { arguments.get() }) }
}

private val Project.generationVersion: String
    get() = providers.gradleProperty(GENERATION_VERSION_PROPERTY).getOrElse(KBUS_PLUGIN_VERSION)

/**
 * Names every module the common metadata compilation resolves, so the processor can look up the
 * index each one would have generated. A module that generated none resolves to nothing, which is
 * how a dependency carrying no kbus handlers reads — the Kotlin stdlib included.
 */
internal fun Project.metadataClasspathModuleNames(): Provider<List<String>> =
    // The Kotlin plugin creates this configuration after the build script has been evaluated.
    provider { configurations.getByName(METADATA_CLASSPATH) }
        .flatMap { it.incoming.artifactView { isLenient = true }.artifacts.resolvedArtifacts }
        .map { artifacts ->
            artifacts
                .mapNotNull { artifact ->
                    when (val component = artifact.id.componentIdentifier) {
                        is ProjectComponentIdentifier -> component.projectName
                        is ModuleComponentIdentifier -> component.module
                        else -> null
                    }
                }
                .distinct()
        }

/**
 * The plugin configures a Kotlin Multiplatform build rather than deciding its shape, so both the
 * Kotlin and KSP plugins are the consumer's to apply and their versions the consumer's to pick.
 */
private fun Project.requireMultiplatformAndKsp() {
    if (!plugins.hasPlugin(KOTLIN_MULTIPLATFORM_PLUGIN_ID)) {
        throw GradleException(
            "$path applies a kbus plugin without the Kotlin Multiplatform plugin. kbus generates " +
                "common code compiled for every target, and a JVM-only build needs a different " +
                "setup that these plugins do not provide. Apply " +
                "`$KOTLIN_MULTIPLATFORM_PLUGIN_ID` first."
        )
    }
    if (!plugins.hasPlugin(KSP_PLUGIN_ID)) {
        throw GradleException(
            "$path applies a kbus plugin without the KSP plugin, which runs the kbus processor. " +
                "Apply `$KSP_PLUGIN_ID` first."
        )
    }
}

internal fun Project.requireSet(value: Provider<String>, property: String): String {
    val set = value.orNull
    if (set.isNullOrBlank()) {
        throw GradleException(
            "$path sets no `$property` in its `kbus { }` block. Without it the processor has " +
                "nowhere to write or read a dependency index, and generation would silently " +
                "produce nothing."
        )
    }
    return set
}
