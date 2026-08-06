plugins { id("kbus.multiplatform") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.kbusContracts)
            implementation(projects.examples.contexts.ordersApplication)
            // The only module in this context that names another context's messages.
            implementation(projects.examples.contexts.inventoryContracts)
        }
    }
}
