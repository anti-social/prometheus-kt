buildscript {
    repositories {
        mavenCentral()
    }
}

plugins {
    java
    kotlin("jvm")
    id("me.champeau.gradle.jmh") version Versions.jmhPlugin
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(project(":"))
    jmh("org.jetbrains.kotlinx", "kotlinx-coroutines-core", Versions.kotnlinxCoroutines)
    jmh("org.openjdk.jmh", "jmh-core", Versions.jmh)
    jmh("io.prometheus", "simpleclient", Versions.prometheusSimpleclient)
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
