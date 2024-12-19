plugins {
    `kotlin-dsl`
    id("groovy-gradle-plugin")
}

repositories { gradlePluginPortal() }

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    // To access libs in buildSrc file
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
