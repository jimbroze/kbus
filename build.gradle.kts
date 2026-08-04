import io.gitlab.arturbosch.detekt.Detekt
import java.util.*
import org.gradle.plugins.ide.idea.model.IdeaModel

description = "Kotlin message bus framework"

plugins {
    base
    alias(libs.plugins.ncorti.ktfmt)
    alias(libs.plugins.arturbosch.detekt)

    kotlin("multiplatform") apply false // Required for Knit
    alias(libs.plugins.kotlinx.knit)
}

allprojects {
    // A layer name repeats in every context, and a project's module identity is group + name — so
    // without the context in the group, `orders:contracts` and `inventory:contracts` are one module
    // and Gradle silently resolves both to whichever it saw first.
    group =
        if (path.startsWith(":examples:contexts:")) "com.jimbroze.examples.${parent!!.name}"
        else "com.jimbroze"
    version = System.getenv("VERSION_OVERRIDE") ?: "0.5.0"

    apply(plugin = "com.ncorti.ktfmt.gradle")
    ktfmt { kotlinLangStyle() }

    apply(plugin = "io.gitlab.arturbosch.detekt")
    // `source.from("src")` below only feeds the plain `detekt` task (no type resolution). The
    // type-resolution tasks (detektJvmMain/detektJvmTest — needed for type-aware rules like
    // ForbiddenMethodCall) source from `compilation.kotlinSourceSets`, which does NOT include
    // commonMain/commonTest transitively; since this project has no src/jvmMain or src/jvmTest,
    // those tasks silently report NO-SOURCE and analyze nothing. Fixed properly in detekt 2.0
    // (per-compilation task wiring via the Kotlin Analysis API) — a breaking plugin migration,
    // tracked as a follow-up rather than done here.
    detekt {
        config.setFrom(file("${rootProject.projectDir}/detekt-config.yml"))
        buildUponDefaultConfig = true
        allRules = false
        autoCorrect = true
        parallel = true
        source.from("src")
    }

    // This is a dependency-free Gradle task rather than a detekt ForbiddenMethodCall rule because
    // detekt's type-resolution tasks are currently broken for this project (NO-SOURCE — see the
    // detekt block above and CLAUDE.md); a custom task was the only way to actually verify this
    // check works.
    // A CoroutineScope built directly in test code (rather than runTest's own this/backgroundScope)
    // is never cancelled by test teardown. If it's ever passed to something that starts real
    // background work (a bus, a coordinator's startPolling/startConsuming), the launched loop leaks
    // past the test — invisible on JVM/Native (process exit doesn't wait for it) but a genuine
    // 20+ minute CI hang on Node, which won't exit while a timer is pending (InboxCoordinatorTest,
    // 2026-07-26).
    // The invariant checked is *parentage*, not syntax: a scope whose job descends from
    // `backgroundScope`'s dies with the test no matter what is launched into it, so a test needing a
    // real dispatcher writes
    // `CoroutineScope(SupervisorJob(backgroundScope.coroutineContext[Job]) + Dispatchers.Default)`
    // and passes. That is a property this check can actually see, which is why there is no opt-out
    // marker and no exempt directory — fixtures included, since a fixture that manufactures its own
    // scope leaks into every test that uses it. Fixtures take a scope from the caller instead.
    tasks.register("checkNoLeakedTestScopes") {
        group = "verification"
        description = "Fails if test code builds a CoroutineScope not parented to backgroundScope."
        val testFiles =
            fileTree(projectDir) {
                include("src/*Test*/**/*.kt")
                // testDoubles is shared fixture code living in commonMain, not a *Test* source set —
                // it is exactly the shared-helper blast radius this check exists for, so include it.
                if (project.name == "testDoubles") include("src/**/*.kt")
            }
        inputs.files(testFiles)
        outputs.upToDateWhen { true }
        doLast {
            val construction = Regex("""\bCoroutineScope\(""")
            val violations = mutableListOf<String>()
            testFiles.forEach { file ->
                val text = file.readText()
                construction.findAll(text).forEach { match ->
                    // Read the constructor's own argument list, balanced-paren, across line breaks.
                    // A line window would let any nearby mention of `backgroundScope` — a very
                    // common token in these files — silence a real violation by accident.
                    val open = match.range.last
                    var depth = 0
                    var end = open
                    while (end < text.length) {
                        if (text[end] == '(') depth++
                        if (text[end] == ')') {
                            depth--
                            if (depth == 0) break
                        }
                        end++
                    }
                    val arguments = text.substring(open, minOf(end + 1, text.length))
                    if (arguments.contains("backgroundScope")) return@forEach
                    val line = text.substring(0, match.range.first).count { it == '\n' } + 1
                    val source = text.lineSequence().elementAt(line - 1).trim()
                    violations += "${file.relativeTo(projectDir)}:$line: $source"
                }
            }
            if (violations.isNotEmpty()) {
                throw GradleException(
                    "CoroutineScope built in test code without backgroundScope parentage — nothing " +
                        "cancels it at teardown, so a launched pump/poll loop leaks past the test " +
                        "(this hung CI for 20+ minutes once already). Use backgroundScope directly, " +
                        "or make a child of it when you need a real dispatcher: CoroutineScope(" +
                        "SupervisorJob(backgroundScope.coroutineContext[Job]) + Dispatchers.Default)" +
                        " — note `backgroundScope.coroutineContext + …` shares its Job rather than " +
                        "parenting to it, so cancelling that would cancel backgroundScope itself. " +
                        "Test helpers should take a scope as a parameter rather than build one.\n" +
                        violations.joinToString("\n")
                )
            }
        }
    }
    tasks.matching { it.name == "check" }.configureEach { dependsOn("checkNoLeakedTestScopes") }

    // idea.module.excludeDirs already defaults to [buildDir, .gradle]. KSP output lives
    // under buildDir, so it must be re-declared as a (generated) source dir to stay
    // included despite that default exclude.
    apply(plugin = "idea")
    configure<IdeaModel> {
        module {
            val kspGenerated = file("build/generated/ksp")
            sourceDirs = sourceDirs + kspGenerated
            generatedSourceDirs = generatedSourceDirs + kspGenerated
        }
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
