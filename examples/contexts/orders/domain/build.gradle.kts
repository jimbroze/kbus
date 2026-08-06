plugins { id("kbus.multiplatform") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.kbusContracts)
            api(projects.kbusDomain)
        }
    }
}
