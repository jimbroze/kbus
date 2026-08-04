plugins { id("kbus.multiplatform") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.kbusContracts)
            implementation(projects.kbusCore)
            implementation(projects.examples.contexts.orders.application)
            // The only module in this context that names another context's messages.
            implementation(projects.examples.contexts.inventory.contracts)
        }
    }
}
