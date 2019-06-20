import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories {
        mavenCentral()
    }
}

plugins {
    java
    kotlin("jvm")
    id("me.champeau.gradle.jmh") version "0.4.8"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(project(":"))
    jmh("org.jetbrains.kotlinx", "kotlinx-coroutines-core", "1.2.1")
    jmh("org.openjdk.jmh", "jmh-core", "1.21")
    jmh("io.prometheus", "simpleclient", "0.6.0")
}

jmh {
    System.getProperty("jmh.include")?.let {
        include = it.split(',')
    }

    warmupIterations = 1
    fork = 1
    iterations = 4
    timeOnIteration = "2s"
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
