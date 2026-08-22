plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    alias(libs.plugins.ncorti.ktfmt)
    alias(libs.plugins.maven.publish)
}

group = "com.jimbroze"

version = System.getenv("VERSION_OVERRIDE") ?: "0.6.0"

description = "Gradle plugin wiring kbus code generation into a Kotlin Multiplatform build"

ktfmt { kotlinLangStyle() }

kotlin { jvmToolchain(libs.versions.jdk.target.get().toInt()) }

val testPluginClasspath: Configuration = configurations.create("testPluginClasspath")

dependencies {
    // The consumer supplies both: depending on either would pin the version they get.
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(
        "com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:" + libs.versions.ksp.get()
    )

    testImplementation(gradleTestKit())
    testImplementation(libs.kotlin.test)

    // TestKit isolates the injected plugin classpath from the test build's own, so the plugins a
    // consumer supplies have to be injected alongside for the kbus plugin to see their types.
    add(testPluginClasspath.name, libs.kotlin.gradle.plugin)
    add(
        testPluginClasspath.name,
        "com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:" + libs.versions.ksp.get(),
    )
}

tasks.pluginUnderTestMetadata { pluginClasspath.from(testPluginClasspath) }

gradlePlugin {
    website = "https://github.com/jimbroze/kbus"
    vcsUrl = "https://github.com/jimbroze/kbus"

    plugins {
        create("kbusContext") {
            id = "com.jimbroze.kbus.context"
            implementationClass = "com.jimbroze.kbus.gradle.KbusContextPlugin"
            displayName = "kbus bounded context module"
            description = "Generates a kbus dependency index for a module of one bounded context"
            tags = listOf("kbus", "ksp", "cqrs", "kotlin-multiplatform")
        }
        create("kbusBus") {
            id = "com.jimbroze.kbus.bus"
            implementationClass = "com.jimbroze.kbus.gradle.KbusBusPlugin"
            displayName = "kbus composition root"
            description = "Generates a kbus message bus from the handlers on the module's classpath"
            tags = listOf("kbus", "ksp", "cqrs", "kotlin-multiplatform")
        }
    }
}

// The processor the plugin adds defaults to the plugin's own version, so a user who applies the
// plugin gets a processor that speaks the same build arguments it passes.
val generateVersionConstant =
    tasks.register("generateKbusPluginVersion") {
        val version = project.version.toString()
        val outputDir = layout.buildDirectory.dir("generated/kbus-version/kotlin")
        inputs.property("version", version)
        outputs.dir(outputDir)
        doLast {
            val file = outputDir.get().file("com/jimbroze/kbus/gradle/KbusPluginVersion.kt").asFile
            file.parentFile.mkdirs()
            file.writeText(
                """
                package com.jimbroze.kbus.gradle

                internal const val KBUS_PLUGIN_VERSION: String = "$version"
                """
                    .trimIndent() + "\n"
            )
        }
    }

sourceSets.main { kotlin.srcDir(generateVersionConstant) }

tasks.withType<com.ncorti.ktfmt.gradle.tasks.KtfmtBaseTask>().configureEach {
    exclude { it.file.absolutePath.contains("/build/generated/") }
}

mavenPublishing {
    coordinates(project.group.toString(), "kbus-gradle-plugin", project.version.toString())

    pom {
        name.set("kbus-gradle-plugin")
        description.set(project.description)
        inceptionYear.set("2024")
        url.set("https://github.com/jimbroze/kbus")

        licenses {
            license {
                name.set("MIT")
                url.set("https://opensource.org/licenses/MIT")
            }
        }

        developers {
            developer {
                id.set("jimbroze")
                name.set("Jim Dickinson")
                email.set("james.n.dickinson@gmail.com")
            }
        }

        scm { url.set("https://github.com/jimbroze/kbus") }
    }

    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
}
