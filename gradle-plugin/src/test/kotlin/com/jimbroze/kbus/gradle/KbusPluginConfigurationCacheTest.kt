package com.jimbroze.kbus.gradle

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContains
import org.gradle.testkit.runner.GradleRunner

/**
 * The generated arguments name what the metadata classpath resolves to, which the configuration
 * cache stores at configuration time — so a build that only ever ran without it can pass while
 * every consumer with the cache enabled fails.
 */
class KbusPluginConfigurationCacheTest {
    private val kotlinVersion = System.getProperty("kbus.test.kotlinVersion")
    private val kspVersion = System.getProperty("kbus.test.kspVersion")

    private val projectDir: File = createTempDirectory("kbus-plugin-cc-test").toFile()

    @Test
    fun `stores and reuses a configuration cache entry for a context module`() {
        writeSettings(subprojects = emptyList(), includedBuilds = emptyList())
        writeBuildScript(
            projectDir,
            """
            plugins {
                kotlin("multiplatform") version "$kotlinVersion"
                id("com.google.devtools.ksp") version "$kspVersion"
                id("com.jimbroze.kbus.context")
            }

            kotlin {
                jvm()
                linuxX64()
            }

            kbus {
                indexPackage = "com.example.indexes"
                boundedContext = "example"
            }
            """,
        )

        assertContains(runWithConfigurationCache(), "Configuration cache entry stored")
        assertContains(runWithConfigurationCache(), "Configuration cache entry reused")
    }

    @Test
    fun `stores and reuses a configuration cache entry for a bus module indexing a sibling`() {
        writeSettings(subprojects = listOf("context"), includedBuilds = emptyList())
        writeBuildScript(
            File(projectDir, "context").apply { mkdirs() },
            """
            plugins {
                kotlin("multiplatform")
                id("com.google.devtools.ksp")
                id("com.jimbroze.kbus.context")
            }

            kotlin {
                jvm()
                linuxX64()
            }

            kbus {
                indexPackage = "com.example.indexes"
                boundedContext = "example"
            }
            """,
        )
        writeBuildScript(
            projectDir,
            """
            plugins {
                kotlin("multiplatform") version "$kotlinVersion"
                id("com.google.devtools.ksp") version "$kspVersion"
                id("com.jimbroze.kbus.bus")
            }

            kotlin {
                jvm()
                linuxX64()
                sourceSets.commonMain.dependencies { api(project(":context")) }
            }

            kbus { indexPackage = "com.example.indexes" }
            """,
        )

        assertContains(runWithConfigurationCache(), "Configuration cache entry stored")
        assertContains(runWithConfigurationCache(), "Configuration cache entry reused")
    }

    @Test
    fun `stores a configuration cache entry when the indexed module lives in an included build`() {
        val contextBuild = File(projectDir, "context-build").apply { mkdirs() }
        File(contextBuild, "settings.gradle.kts")
            .writeText(
                """
                pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
                dependencyResolutionManagement { repositories { mavenCentral() } }
                rootProject.name = "shared"
                """
                    .trimIndent()
            )
        writeBuildScript(
            contextBuild,
            """
            plugins {
                kotlin("multiplatform") version "$kotlinVersion"
                id("com.google.devtools.ksp") version "$kspVersion"
                id("com.jimbroze.kbus.context")
            }

            group = "com.example"
            version = "1.0"

            kotlin {
                jvm()
                linuxX64()
            }

            kbus {
                indexPackage = "com.example.indexes"
                boundedContext = "example"
            }
            """,
        )

        writeSettings(subprojects = emptyList(), includedBuilds = listOf("context-build"))
        writeBuildScript(
            projectDir,
            """
            plugins {
                kotlin("multiplatform") version "$kotlinVersion"
                id("com.google.devtools.ksp") version "$kspVersion"
                id("com.jimbroze.kbus.bus")
            }

            kotlin {
                jvm()
                linuxX64()
                sourceSets.commonMain.dependencies { api("com.example:shared:1.0") }
            }

            kbus { indexPackage = "com.example.indexes" }
            """,
        )

        assertContains(runWithConfigurationCache(), "Configuration cache entry stored")
    }

    private fun writeSettings(subprojects: List<String>, includedBuilds: List<String>) {
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
                    .trimIndent() +
                    subprojects.joinToString("") { "\n" + """include(":$it")""" } +
                    includedBuilds.joinToString("") { "\n" + """includeBuild("$it")""" } +
                    "\n"
            )
    }

    private fun writeBuildScript(directory: File, script: String) {
        File(directory, "build.gradle.kts").writeText(script.trimIndent())
    }

    private fun runWithConfigurationCache(): String =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("kspCommonMainKotlinMetadata", "--configuration-cache")
            .build()
            .output
}
