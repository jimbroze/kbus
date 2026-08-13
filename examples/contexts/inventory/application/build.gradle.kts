plugins { id("kbus.handler-module") }

boundedContext { identity = "inventory" }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.kbusApi)
            implementation(projects.kbusCore)
            api(projects.examples.contexts.inventoryContracts)
            api(projects.examples.contexts.inventoryDomain)
        }
    }
}
