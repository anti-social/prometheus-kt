// buildscript {
//     dependencies {
//         classpath(kotlin("gradle-plugin", version = "1.3.41"))
//     }
// }

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
    compile("org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.72")
}
