plugins { id("kbus.multiplatform") }

// Tests, in a main source set so another module can inherit them. Detekt's default exemptions for
// test code key off the source set name, so they do not reach these.
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach { exclude("**/app/**") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // A consumer subclasses these tests, so it needs the test framework and the contexts
            // the assertions name on its own compile classpath.
            api(kotlin("test"))
            api(libs.kotlinx.coroutines.test)
            api(projects.kbusCore)
            api(projects.examples.contexts.ordersContracts)
            api(projects.examples.contexts.inventoryContracts)
            api(projects.examples.contexts.ordersApplication)
            api(projects.examples.contexts.inventoryApplication)
            implementation(projects.kbusApi)
            implementation(projects.testDoubles)
            implementation(libs.kotlinx.coroutines.core)
        }

        // `kotlin.test.Test` is a JUnit alias on the JVM, and only a *test* compilation gets a test
        // framework by default. This module states its requirements from a main source set, so it
        // names one itself.
        jvmMain.dependencies { api(kotlin("test-junit")) }
    }
}
