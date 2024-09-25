import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.konan.target.HostManager

plugins { kotlin("multiplatform") }

kotlin {
    js(IR) {
        //        browser()
        nodejs()
    }
    jvm {
        //        compilations.all {
        //            kotlinOptions {
        //                jvmTarget = "1.8"
        //                apiVersion = "1.7"
        //                languageVersion = "1.9"
        //            }
        //        }
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        //        browser()
        //        nodejs()
        //        d8()
    }
    if (HostManager.hostIsMac) {
        macosX64()
        macosArm64()
        iosX64()
        iosArm64()
        iosSimulatorArm64()
        //        watchosArm32()
        //        watchosArm64()
        //        watchosX64()
        //        watchosSimulatorArm64()
        //        watchosDeviceArm64()
        //        tvosArm64()
        //        tvosX64()
        //        tvosSimulatorArm64()
    }
    if (HostManager.hostIsMingw || HostManager.hostIsMac) {
        mingwX64 {
            binaries.findTest(DEBUG)!!.linkerOpts = mutableListOf("-Wl,--subsystem,windows")
        }
    }
    if (HostManager.hostIsLinux || HostManager.hostIsMac) {
        linuxX64()
        linuxArm64()
    }
}
