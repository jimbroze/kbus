plugins {
    kotlin("jvm") version "1.9.21"
    id("org.jlleitschuh.gradle.ktlint") version "12.0.3"
    id("org.jetbrains.kotlinx.kover") version "0.7.5"
}

group = "com.jimbroze"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(20)
}

koverReport {
    filters {
        // filters for all reports
    }

    verify {
        // verification rules for all reports
    }

    defaults {
        xml { /* default XML report config */ }
        html { /* default HTML report config */ }
        verify { /* default verification config */ }
        log { /* default logging config */ }
    }
}
