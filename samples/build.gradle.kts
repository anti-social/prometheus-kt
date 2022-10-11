plugins {
    kotlin("jvm")
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "application")
    apply(plugin = "org.jetbrains.kotlin.jvm")

    repositories {
        mavenCentral()
    }

    val implementation by configurations
    dependencies {
        implementation(kotlin("reflect"))
        implementation(project(":prometheus-kt-ktor"))
        implementation(Libs.ktor("server-netty"))
        implementation("org.slf4j:slf4j-simple:${Versions.slf4j}")
    }
}
