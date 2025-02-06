import com.ncorti.ktfmt.gradle.tasks.KtfmtFormatTask
import io.gitlab.arturbosch.detekt.Detekt
import java.util.*

description = "Kotlin message bus framework"

plugins {
    id("com.ncorti.ktfmt.gradle") version ("0.20.1")
    id("io.gitlab.arturbosch.detekt") version ("1.23.7")
}

allprojects {
    group = "com.jimbroze"
    version = System.getenv("VERSION_OVERRIDE") ?: "0.2.0"

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

val gitHooksDir = file(".git/hooks")

tasks.register<Copy>("installGitHooks") {
    onlyIf { gitHooksDir.exists() }
    from(rootProject.rootDir.resolve("bin/pre-commit"))
    into(rootProject.rootDir.resolve(".git/hooks"))
    filePermissions {
        user.read = true
        user.write = true
        user.execute = true
        other.read = true
        other.write = true
        other.execute = true
    }
}

tasks.register<KtfmtFormatTask>("ktfmtPrecommitFormat") {
    group = "formatting"
    description = "Runs ktfmt on kotlin files in the project"
    source = project.fileTree(rootDir).apply { include("**/*.kt", "**/*.kts") }
}

tasks.whenTaskAdded {
    if (name == "build") {
        dependsOn("installGitHooks")
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
