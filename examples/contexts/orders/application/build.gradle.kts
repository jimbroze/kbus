plugins { id("kbus.handler-module") }

boundedContext { identity = "orders" }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.kbusContracts)
            implementation(projects.kbusCore)
            implementation(projects.kbusDomain)
            api(projects.examples.contexts.orders.contracts)
            api(projects.examples.contexts.orders.domain)
            // Another context is reachable only through its published contracts.
            implementation(projects.examples.contexts.inventory.contracts)
        }
    }
}
