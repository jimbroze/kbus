package com.jimbroze.kbus.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create

/**
 * Generates only a dependency index, naming what this module declares so that a module applying
 * `com.jimbroze.kbus.bus` can build a bus from it.
 */
class KbusContextPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val kbus = project.extensions.create<KbusContextExtension>("kbus")

        project.afterEvaluate {
            requireMultiplatformAndKsp()

            // An index class name and package are derived from the submodule name, which therefore
            // has to be unique across the build.
            val arguments =
                listOf(
                    "kbus.subModuleName=$name",
                    "kbus.boundedContextIdentity=" +
                        requireSet(kbus.boundedContext, "boundedContext"),
                    "kbus.indexPackage=" + requireSet(kbus.indexPackage, "indexPackage"),
                )

            wireKbusGeneration(providers.provider { arguments })
        }
    }
}
