plugins { id("kbus.handler-module") }

boundedContext { identity = "inventory" }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.kbusContracts)
            implementation(projects.kbusCore)
            api(projects.examples.contexts.inventory.contracts)
            api(projects.examples.contexts.inventory.domain)
        }
    }
}
