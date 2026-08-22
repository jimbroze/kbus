import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.project
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

private const val GENERATED_SOURCE_DIR = "build/generated/ksp/metadata/commonMain/kotlin"
private const val METADATA_KSP_TASK = "kspCommonMainKotlinMetadata"
private const val METADATA_CLASSPATH = "commonMainResolvableDependenciesMetadata"
private const val MODULES_TO_INDEX_KEY = "kbus.modulesToIndex"

/**
 * Runs kbus generation once over common metadata, rather than once per target, so that what it
 * writes is common code every target compiles.
 */
fun Project.generateKbusCodeFromCommonMetadata() {
    dependencies.add("kspCommonMainMetadata", project(":kbus-generation"))

    the<KotlinMultiplatformExtension>().sourceSets.named("commonMain").configure {
        kotlin.srcDir(GENERATED_SOURCE_DIR)
    }

    tasks.withType<KotlinCompilationTask<*>>().configureEach {
        if (name.startsWith("compile")) {
            dependsOn(METADATA_KSP_TASK)
        }
    }

    // Every per-target KSP task reads commonMain, which is where the metadata task writes.
    tasks.matching { it.name.startsWith("ksp") && it.name != METADATA_KSP_TASK }.configureEach {
        dependsOn(METADATA_KSP_TASK)
    }

    indexEveryModuleOnTheMetadataClasspath()
}

/**
 * Names every module the common metadata compilation resolves, so the processor can look up the
 * index each one would have generated. A module that generated none resolves to nothing, which is
 * how a dependency carrying no kbus handlers reads — the Kotlin stdlib included.
 *
 * A module whose index is not named after its Gradle module can never be found this way, and has to
 * be named by hand alongside this.
 */
private fun Project.indexEveryModuleOnTheMetadataClasspath() {
    val moduleNames = metadataClasspathModuleNames()

    extensions.configure<KspExtension> {
        arg(CommandLineArgumentProvider { listOf("$MODULES_TO_INDEX_KEY=${moduleNames.get()}") })
    }
}

private fun Project.metadataClasspathModuleNames(): Provider<String> =
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
                .joinToString(",")
        }
