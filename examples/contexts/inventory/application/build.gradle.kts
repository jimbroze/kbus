plugins {
    id("kbus.multiplatform")
    id("com.google.devtools.ksp")
    id("com.jimbroze.kbus.context")
}

kbus {
    indexPackage = "com.jimbroze.kbus.example.indexes"
    boundedContext = "inventory"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.kbusApi)
            implementation(projects.kbusApplication)
            api(projects.examples.contexts.inventoryContracts)
            api(projects.examples.contexts.inventoryDomain)
        }
    }
}
