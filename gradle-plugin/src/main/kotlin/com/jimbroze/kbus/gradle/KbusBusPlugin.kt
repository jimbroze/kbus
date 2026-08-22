package com.jimbroze.kbus.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create

/** Generates a bus from the handlers this module declares and the indexes its classpath carries. */
class KbusBusPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val kbus = project.extensions.create<KbusBusExtension>("kbus")

        val modulesToIndex =
            project.metadataClasspathModuleNames().zip(kbus.additionalModulesToIndex) {
                derivedFromClasspath,
                namedByHand ->
                (derivedFromClasspath + namedByHand).distinct()
            }

        val arguments =
            modulesToIndex.map { modules ->
                listOf(
                    "kbus.indexPackage=" + project.requireSet(kbus.indexPackage, "indexPackage"),
                    "kbus.modulesToIndex=" + modules.joinToString(","),
                )
            }

        project.afterEvaluate {
            wireKbusGeneration(arguments)
            requireSet(kbus.indexPackage, "indexPackage")
        }
    }
}
