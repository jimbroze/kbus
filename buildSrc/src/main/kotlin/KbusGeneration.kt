import org.gradle.api.Project
import org.gradle.kotlin.dsl.project
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

private const val GENERATED_SOURCE_DIR = "build/generated/ksp/metadata/commonMain/kotlin"
private const val METADATA_KSP_TASK = "kspCommonMainKotlinMetadata"

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

}
