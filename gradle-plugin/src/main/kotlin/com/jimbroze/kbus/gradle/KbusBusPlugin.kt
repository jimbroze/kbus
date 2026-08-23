package com.jimbroze.kbus.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create

/** Generates a bus from the handlers this module declares and the indexes its classpath carries. */
class KbusBusPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val kbus = project.extensions.create<KbusBusExtension>("kbus")

        project.afterEvaluate {
            requireMultiplatformAndKsp()

            val modulesToIndex =
                metadataClasspathModuleNames().zip(kbus.additionalModulesToIndex) {
                    derivedFromClasspath,
                    namedByHand ->
                    (derivedFromClasspath + namedByHand).distinct()
                }

            val indexPackage = requireSet(kbus.indexPackage, "indexPackage")

            wireKbusGeneration(
                modulesToIndex.map { modules ->
                    listOf(
                        "kbus.indexPackage=$indexPackage",
                        "kbus.modulesToIndex=" + modules.joinToString(","),
                    )
                }
            )
        }
    }
}
