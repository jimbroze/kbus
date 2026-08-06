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
    implementation("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:" + libs.versions.ksp.get())
    // To access libs in buildSrc file
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
