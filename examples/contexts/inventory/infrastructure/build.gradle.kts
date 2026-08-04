plugins { id("kbus.multiplatform") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.examples.contexts.inventory.application)
            implementation(projects.examples.contexts.inventory.domain)
        }
    }
}
