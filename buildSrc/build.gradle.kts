plugins {
    `kotlin-dsl`
    idea
}

repositories {
    mavenLocal()
    jcenter()
    repositories {
    maven("https://plugins.gradle.org/m2/")
  }
}

idea {
    module {
        isDownloadJavadoc = false
        isDownloadSources = false
    }
}

dependencies {
    // TODO: How could we use single kotlin version?
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.21")
    implementation("io.github.gradle-nexus:publish-plugin:1.1.0")
}
