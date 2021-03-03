import java.net.URI

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

private const val bintrayUsername = "evo"
private const val bintrayRepoName = "maven"
private const val bintrayPackageName = "prometheus-kt"

fun Project.bintrayUrl(): URI {
    val bintrayPublish = findProperty("bintrayPublish")?.toString()
        ?: System.getenv("BINTRAY_PUBLISH")
        ?: "0"
    return URI(
        "https://api.bintray.com/maven/$bintrayUsername/$bintrayRepoName/$bintrayPackageName/;publish=$bintrayPublish"
    )
}

fun Project.bintrayUser(): String? {
    return findProperty("bintrayUser")?.toString()
        ?: System.getenv("BINTRAY_USER")
}

fun Project.bintrayApiKey(): String? {
    return findProperty("bintrayApiKey")?.toString()
        ?: System.getenv("BINTRAY_API_KEY")
}

fun RepositoryHandler.bintray(project: Project): MavenArtifactRepository = maven {
    name = "bintray"
    url = project.bintrayUrl()
    credentials {
        username = project.bintrayUser()
        password = project.bintrayApiKey()
    }
}

fun RepositoryHandler.test(project: Project): MavenArtifactRepository = maven {
    name = "test"
    url = project.uri("file://${project.rootProject.buildDir}/localMaven")
}

fun PublishingExtension.configureRepositories(project: Project) = repositories {
    bintray(project)
    test(project)
}

fun PublishingExtension.configureJvmPublishing(project: Project) {
    project.tasks.register<Jar>("sourcesJar") {
        val kotlin = project.extensions.getByName<KotlinJvmProjectExtension>("kotlin")
        from(kotlin.sourceSets.named("main").get().kotlin)
        archiveClassifier.set("sources")
    }
    publications {
        create<MavenPublication>("maven") {
            from(project.components["java"])
            artifact(project.tasks["sourcesJar"])
        }
    }

    configureRepositories(project)
}

fun PublishingExtension.configureMultiplatformPublishing(project: Project) {
    configureRepositories(project)
}
