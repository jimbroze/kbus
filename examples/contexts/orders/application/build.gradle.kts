plugins { id("kbus.handler-module") }

boundedContext { identity = "orders" }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.kbusApi)
            implementation(projects.kbusCore)
            implementation(projects.kbusDomain)
            api(projects.examples.contexts.ordersContracts)
            api(projects.examples.contexts.ordersDomain)
            // Another context is reachable only through its published contracts.
            implementation(projects.examples.contexts.inventoryContracts)
        }
    }
}
