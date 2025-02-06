plugins {
    `kotlin-dsl`
    id("groovy-gradle-plugin")
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.maven.publish.plugin)
    // To access libs in buildSrc file
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
