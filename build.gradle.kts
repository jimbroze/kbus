import io.gitlab.arturbosch.detekt.Detekt
import java.util.*

description = "Kotlin message bus framework"

plugins {
    base
    alias(libs.plugins.ncorti.ktfmt)
    alias(libs.plugins.arturbosch.detekt)

    kotlin("multiplatform") apply false // Required for Knit
    alias(libs.plugins.kotlinx.knit)
}

allprojects {
    group = "com.jimbroze"
    version = System.getenv("VERSION_OVERRIDE") ?: "0.5.0"

    apply(plugin = "com.ncorti.ktfmt.gradle")
    ktfmt { kotlinLangStyle() }

    apply(plugin = "io.gitlab.arturbosch.detekt")
    detekt {
        config.setFrom(file("${rootProject.projectDir}/detekt-config.yml"))
        buildUponDefaultConfig = true
        allRules = false
        autoCorrect = true
        parallel = true
        source.from("src")
    }
}

val localPropertiesFile = file("local.properties")

if (localPropertiesFile.exists()) {
    val localProperties = Properties()
    localPropertiesFile.inputStream().use { localProperties.load(it) }
    localProperties.forEach { (key, value) ->
        project.extensions.extraProperties[key.toString()] = value
    }
}

tasks.withType<Detekt>().configureEach {
    reports {
        // Enable the generation of an HTML report
        html.required.set(true)
        html.outputLocation.set(file("build/reports/detekt.html"))

        txt.required.set(true)
        txt.outputLocation.set(file("build/reports/detekt.txt"))

        md.required.set(true)
        md.outputLocation.set(file("build/reports/detekt.md"))
    }
}

knit {
    files =
        fileTree(project.rootDir) {
            include("**/*.md")
            include("**/*.kt")
            include("**/*.kts")

            exclude("**/build/**")
            exclude("**/.gradle/**")

            exclude("**/.aider.chat.history.md")
            exclude("**/.aider/**")
        }
}

val knitTask = tasks.named("knit")
val ktfmtTask = tasks.named("ktfmtFormat")

allprojects {
    pluginManager.withPlugin("base") {
        tasks.named("check") {
            dependsOn(knitTask)
            dependsOn(ktfmtTask)
        }
    }
}
