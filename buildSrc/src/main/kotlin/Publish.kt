import java.net.URI

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository

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
    project.version = "0.1.0-alpha-0"
    url = project.bintrayUrl()
    credentials {
        username = project.bintrayUser()
        password = project.bintrayApiKey()
    }
}
