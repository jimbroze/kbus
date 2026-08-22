plugins {
    id("kbus.multiplatform")
    id("com.google.devtools.ksp")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.kbusApi)
            implementation(projects.kbusCore)
            implementation(projects.kbusDomain)
            implementation(projects.testDoubles)
        }
    }
}

generateKbusCodeFromCommonMetadata()

ksp {
    arg("kbus.subModuleName", project.name)
    arg("kbus.boundedContextIdentity", "depot")
    arg("kbus.indexPackage", "com.jimbroze.kbus.generation.fixtures.indexes")
}
