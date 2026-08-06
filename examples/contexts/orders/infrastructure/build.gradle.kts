plugins { id("kbus.multiplatform") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.examples.contexts.ordersApplication)
            implementation(projects.examples.contexts.ordersDomain)
        }
    }
}
