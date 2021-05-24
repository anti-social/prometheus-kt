plugins {
    id("org.jetbrains.kotlin.jvm") version "1.4.30" apply false
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "application")
    apply(plugin = "org.jetbrains.kotlin.jvm")

    repositories {
        mavenCentral()
    }

    val ktorVersion = "1.5.3"
    val slf4jVersion = "1.7.30"

    val implementation by configurations
    dependencies {
        implementation(kotlin("reflect"))
        implementation("dev.evo.prometheus:prometheus-kt-ktor")
        implementation("io.ktor:ktor-server-netty:$ktorVersion")
        implementation("org.slf4j:slf4j-simple:$slf4jVersion")
    }
}
