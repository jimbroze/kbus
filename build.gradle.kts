import com.ncorti.ktfmt.gradle.tasks.KtfmtFormatTask
import io.gitlab.arturbosch.detekt.Detekt

plugins {
    id("com.ncorti.ktfmt.gradle") version ("0.20.1")
    id("io.gitlab.arturbosch.detekt") version ("1.23.7")
}

allprojects {
    group = "com.jimbroze"

    apply(plugin = "com.ncorti.ktfmt.gradle")
    ktfmt { kotlinLangStyle() }

    apply(plugin = "io.gitlab.arturbosch.detekt")
    detekt {
        source.from("src")
        allRules = false
        buildUponDefaultConfig = true
        autoCorrect = true
        parallel = true
    }
}

val gitHooksDir = file(".git/hooks")

tasks.register<Copy>("installGitHooks") {
    onlyIf { gitHooksDir.exists() }
    from(rootProject.rootDir.resolve("bin/pre-commit"))
    into(rootProject.rootDir.resolve(".git/hooks"))
    fileMode = 0b111111111 // This is equivalent to 0777 in octal
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
