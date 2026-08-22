package com.jimbroze.kbus.gradle

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContains
import org.gradle.testkit.runner.GradleRunner

/**
 * The plugin exists so that wiring which fails silently fails loudly instead, so each of these
 * states a misconfiguration and the message it has to produce. What the wiring generates when it is
 * correct is proved by the example modules, which apply these same plugin ids.
 */
class KbusPluginConfigurationTest {
    private val projectDir: File = createTempDirectory("kbus-plugin-test").toFile()

    @Test
    fun `fails naming the module and the property when a bus module sets no index package`() {
        writeSettings()
        writeBuildScript(
            """
            plugins {
                kotlin("multiplatform")
                id("com.google.devtools.ksp")
                id("com.jimbroze.kbus.bus")
            }

            kotlin { jvm() }
            """
        )

        val result = runAndFail("help")

        assertContains(result, "indexPackage")
        assertContains(result, "generation would silently")
    }

    @Test
    fun `fails naming the module and the property when a context module sets no bounded context`() {
        writeSettings()
        writeBuildScript(
            """
            plugins {
                kotlin("multiplatform")
                id("com.google.devtools.ksp")
                id("com.jimbroze.kbus.context")
            }

            kotlin { jvm() }

            kbus { indexPackage = "com.example.indexes" }
            """
        )

        val result = runAndFail("help")

        assertContains(result, "boundedContext")
    }

    @Test
    fun `fails pointing at the Kotlin Multiplatform plugin when it is absent`() {
        writeSettings()
        writeBuildScript(
            """
            plugins {
                id("com.jimbroze.kbus.bus")
            }

            kbus { indexPackage = "com.example.indexes" }
            """
        )

        val result = runAndFail("help")

        assertContains(result, "Kotlin Multiplatform plugin")
    }

    @Test
    fun `fails pointing at the KSP plugin when it is absent`() {
        writeSettings()
        writeBuildScript(
            """
            plugins {
                kotlin("multiplatform")
                id("com.jimbroze.kbus.bus")
            }

            kotlin { jvm() }

            kbus { indexPackage = "com.example.indexes" }
            """
        )

        val result = runAndFail("help")

        assertContains(result, "KSP plugin")
    }

    private fun writeSettings() {
        File(projectDir, "settings.gradle.kts")
            .writeText(
                """
                pluginManagement {
                    repositories {
                        gradlePluginPortal()
                        mavenCentral()
                    }
                }

                dependencyResolutionManagement { repositories { mavenCentral() } }

                rootProject.name = "consumer"
                """
                    .trimIndent()
            )
    }

    private fun writeBuildScript(script: String) {
        File(projectDir, "build.gradle.kts").writeText(script.trimIndent())
    }

    private fun runAndFail(vararg arguments: String): String =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(*arguments)
            .buildAndFail()
            .output
}
