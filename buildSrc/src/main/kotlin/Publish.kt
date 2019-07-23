import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler

private const val bintrayUsername = "evo"
private const val bintrayRepoName = "maven"
private const val bintrayPackageName = "prometheus-kt"
val bintrayUrl = java.net.URI(
    "https://api.bintray.com/maven/$bintrayUsername/$bintrayRepoName/$bintrayPackageName/;publish=0"
)

fun Project.bintrayUser(): String? {
    return findProperty("bintrayUser")?.toString()
        ?: System.getenv("BINTRAY_USER")
}

fun Project.bintrayApiKey(): String? {
    return findProperty("bintrayApiKey")?.toString()
        ?: System.getenv("BINTRAY_API_KEY")
}

fun RepositoryHandler.bintray(project: Project) = maven {
    name = "bintray"
    project.version = "0.1.0-alpha-0"
    url = bintrayUrl
    credentials {
        username = project.bintrayUser()
        password = project.bintrayApiKey()
    }
}
