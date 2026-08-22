plugins { id("kbus.bus-module") }

generatedBus { indexPackage = "com.jimbroze.kbus.example.indexes" }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.kbusApi)
            implementation(projects.kbusCore)
            implementation(projects.kbusDomain)

            implementation(projects.examples.contexts.ordersContracts)
            implementation(projects.examples.contexts.ordersDomain)
            implementation(projects.examples.contexts.ordersApplication)
            implementation(projects.examples.contexts.ordersInfrastructure)
            implementation(projects.examples.contexts.ordersAcl)

            implementation(projects.examples.contexts.inventoryContracts)
            implementation(projects.examples.contexts.inventoryDomain)
            implementation(projects.examples.contexts.inventoryApplication)
            implementation(projects.examples.contexts.inventoryInfrastructure)

            implementation(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(projects.testDoubles)
            implementation(projects.examples.adapters)
            implementation(projects.examples.appContract)
        }
    }
}
