plugins {
    `kotlin-dsl`
    idea
}

repositories {
    mavenLocal()
    jcenter()
}

idea {
    module {
        isDownloadJavadoc = false
        isDownloadSources = false
    }
}
