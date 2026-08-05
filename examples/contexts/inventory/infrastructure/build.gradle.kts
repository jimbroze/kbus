plugins { id("kbus.multiplatform") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.examples.contexts.inventoryApplication)
            implementation(projects.examples.contexts.inventoryDomain)
        }
    }
}
