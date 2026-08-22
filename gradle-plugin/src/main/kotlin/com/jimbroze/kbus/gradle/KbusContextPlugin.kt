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

        // An index class name and package are derived from the submodule name, which therefore has
        // to be unique across the build.
        val arguments =
            project.provider {
                listOf(
                    "kbus.subModuleName=${project.name}",
                    "kbus.boundedContextIdentity=" +
                        project.requireSet(kbus.boundedContext, "boundedContext"),
                    "kbus.indexPackage=" + project.requireSet(kbus.indexPackage, "indexPackage"),
                )
            }

        project.afterEvaluate {
            wireKbusGeneration(arguments)
            // Reading them here turns a misconfiguration into a configuration failure naming the
            // property, rather than a build that generates nothing.
            arguments.get()
        }
    }
}
