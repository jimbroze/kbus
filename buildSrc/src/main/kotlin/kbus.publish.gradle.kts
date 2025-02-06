import com.vanniktech.maven.publish.SonatypeHost

plugins {
    `maven-publish`
    id("com.vanniktech.maven.publish")
}

publishing { repositories { mavenLocal() } }

// Required to get project description
afterEvaluate {
    mavenPublishing {
        coordinates(
            groupId = project.group.toString(),
            artifactId = project.name,
            version = project.version.toString(),
        )

        pom {
            name.set(project.name)
            description.set(project.description)
            inceptionYear.set("2024")
            url.set("https://github.com/jimbroze/kbus")

            licenses {
                license {
                    name.set("MIT")
                    url.set("https://opensource.org/licenses/MIT")
                }
            }

            developers {
                developer {
                    id.set("jimbroze")
                    name.set("Jim Dickinson")
                    email.set("james.n.dickinson@gmail.com")
                }
            }

            // Specify SCM information
            scm { url.set("https://github.com/jimbroze/kbus") }
        }

        // Manual release ./gradlew publishToMavenCentral --no-configuration-cache
        publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
        // Release with publish & publishToMavenCentral tasks
        //        publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
        signAllPublications()
    }
}
