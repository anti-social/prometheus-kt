import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    kotlin("jvm") version "1.3.31"
}

group = "dev.evo"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

val kotlinxCoroutinesVersion = "1.2.1"

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", kotlinxCoroutinesVersion)
    implementation("org.jetbrains.kotlinx", "atomicfu", "0.12.8")

    testCompile("junit", "junit", "4.12")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}
