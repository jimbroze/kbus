plugins {
    id("kbus.multiplatform")
    id("com.google.devtools.ksp")
}

interface GeneratedBusExtension {
    /** The package every module of this build writes its dependency index into. */
    val indexPackage: Property<String>
}

val generatedBus = extensions.create<GeneratedBusExtension>("generatedBus")

generateKbusCodeFromCommonMetadata()

afterEvaluate {
    extensions.configure<com.google.devtools.ksp.gradle.KspExtension> {
        arg("kbus.indexPackage", generatedBus.indexPackage.get())
    }
}
