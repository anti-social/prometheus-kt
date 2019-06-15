buildscript {
    repositories {
        mavenCentral()
    }
}

plugins {
    java
    kotlin("jvm")
    id("me.champeau.gradle.jmh") version "0.4.7"
}

repositories {
    mavenCentral()
}

dependencies {
    jmh(project(":"))
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
