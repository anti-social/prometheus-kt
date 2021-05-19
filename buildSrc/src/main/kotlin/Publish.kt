import java.net.URI

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

private const val sonatypeRepositoryUrl = "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"

fun Project.sonatypeUser(): String? {
    return findProperty("sonatypeUser")?.toString()
        ?: System.getenv("SONATYPE_USER")
}

fun Project.sonatypePassword(): String? {
    return findProperty("sonatypePassword")?.toString()
        ?: System.getenv("SONATYPE_PASSWORD")
}

fun RepositoryHandler.sonatype(project: Project): MavenArtifactRepository = maven {
    name = "sonatype"
    url = URI(sonatypeRepositoryUrl)
    credentials {
        username = project.sonatypeUser()
        password = project.sonatypePassword()
    }
}

fun RepositoryHandler.test(project: Project): MavenArtifactRepository = maven {
    name = "test"
    url = project.uri("file://${project.rootProject.buildDir}/localMaven")
}

fun PublishingExtension.configureRepositories(project: Project) = repositories {
    sonatype(project)
    test(project)
}

fun MavenPublication.configurePom() = pom {
    name.set("prometheus-kt")
    description.set("Prometheus Kotlin Client")
    url.set("https://github.com/anti-social/prometheus-kt")

    licenses {
        license {
            name.set("The Apache License, Version 2.0")
            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
        }
    }

    scm {
        url.set("https://github.com/anti-social/prometheus-kt")
        connection.set("scm:https://github.com/anti-social/prometheus-kt.git")
        developerConnection.set("scm:git://github.com/anti-social/prometheus-kt.git")
    }

    developers {
        developer {
            id.set("anti-social")
            name.set("Oleksandr Koval")
            email.set("kovalidis@gmail.com")
        }
    }
}

fun PublishingExtension.configureJvmPublishing(project: Project) {
    val javadocJar by project.tasks.registering(Jar::class) {
        archiveClassifier.set("javadoc")
    }

    val sourcesJar = project.tasks.register<Jar>("sourcesJar") {
        val kotlin = project.extensions.getByName<KotlinJvmProjectExtension>("kotlin")
        from(kotlin.sourceSets.named("main").get().kotlin)
        archiveClassifier.set("sources")
    }

    publications {
        create<MavenPublication>("maven") {
            from(project.components["java"])

            artifact(sourcesJar.get())
            artifact(javadocJar.get())

            configurePom()
        }
    }



    configureRepositories(project)
}

fun PublishingExtension.configureMultiplatformPublishing(project: Project) {
    val javadocJar by project.tasks.registering(Jar::class) {
        archiveClassifier.set("javadoc")
    }

    publications.withType<MavenPublication> {
        artifact(javadocJar.get())

        configurePom()
    }

    configureRepositories(project)
}
