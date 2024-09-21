import com.ncorti.ktfmt.gradle.tasks.KtfmtFormatTask

plugins { id("com.ncorti.ktfmt.gradle") version ("0.20.1") }

allprojects {
    group = "com.jimbroze"

    apply(plugin = "com.ncorti.ktfmt.gradle")
    ktfmt { kotlinLangStyle() }
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
