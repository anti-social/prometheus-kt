plugins {
    `kotlin-dsl`
    idea
}

repositories {
    mavenLocal()
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
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.22")
    implementation("io.github.gradle-nexus:publish-plugin:2.0.0-rc-1")
}
