import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories {
        mavenCentral()
    }
}

plugins {
    java
    kotlin("jvm")
}

repositories {
    jcenter()
}

val ktorVersion = "1.2.2"

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    api(project(":"))
    implementation("io.ktor", "ktor-server-core", ktorVersion)

    testImplementation("io.ktor", "ktor-server-test-host", ktorVersion)
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
