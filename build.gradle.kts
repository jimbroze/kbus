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

sourceSets {
    main {
        kotlin {
            srcDir("src/main/")
        }
    }
    create("instantiator") {
        kotlin {
            srcDir("src/instantiator/")
        }
    }
    create("koin") {
        kotlin {
            srcDir("src/koin/")
        }
    }
    test {
        compileClasspath += sourceSets.main.get().output +
                sourceSets.getByName("koin").output + sourceSets.getByName("instantiator").output
        runtimeClasspath += sourceSets.main.get().output +
                sourceSets.getByName("koin").output + sourceSets.getByName("instantiator").output
        kotlin {
            srcDir("src/test/")
        }
    }
}

configurations["testImplementation"].extendsFrom(configurations["koinImplementation"])
configurations["testImplementation"].extendsFrom(configurations["instantiatorImplementation"])

java {
    registerFeature("instantiator") {
        usingSourceSet(sourceSets["instantiator"])
    }
    registerFeature("koin") {
        usingSourceSet(sourceSets["koin"])
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0-RC")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")

    testImplementation("org.jetbrains.kotlin:kotlin-test")

    "instantiatorImplementation"(project(":"))
    "instantiatorImplementation"(kotlin("reflect"))

    "koinImplementation"(project(":"))
    "koinImplementation"("io.insert-koin:koin-core:3.5.3")
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
