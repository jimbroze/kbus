plugins {
    id("kbus.multiplatform")
    id("com.google.devtools.ksp")
    id("com.jimbroze.kbus.context")
}

kbus {
    indexPackage = "com.jimbroze.kbus.example.indexes"
    boundedContext = "orders"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.kbusApi)
            implementation(projects.kbusApplication)
            implementation(projects.kbusDomain)
            api(projects.examples.contexts.ordersContracts)
            api(projects.examples.contexts.ordersDomain)
            // Another context is reachable only through its published contracts.
            implementation(projects.examples.contexts.inventoryContracts)
        }
    }
}
