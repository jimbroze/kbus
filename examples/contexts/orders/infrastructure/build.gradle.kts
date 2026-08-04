plugins { id("kbus.multiplatform") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.examples.contexts.orders.application)
            implementation(projects.examples.contexts.orders.domain)
        }
    }
}
