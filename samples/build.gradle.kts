plugins {
    id("org.jetbrains.kotlin.jvm") version "1.3.72" apply false
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "application")
    apply(plugin = "org.jetbrains.kotlin.jvm")

    repositories {
        mavenCentral()
        jcenter()
    }
}
