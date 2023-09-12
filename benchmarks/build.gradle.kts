buildscript {
    repositories {
        mavenCentral()
    }
}

plugins {
    java
    kotlin("jvm")
    id("me.champeau.jmh") version Versions.jmhPlugin
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(project(":"))
    jmh("org.jetbrains.kotlinx", "kotlinx-coroutines-core", Versions.kotnlinxCoroutines)
    jmh("org.openjdk.jmh", "jmh-core", Versions.jmh)
    jmh("io.prometheus", "simpleclient", Versions.prometheusSimpleclient)
}

java {
    sourceCompatibility = Versions.jvmTargetVersion
    targetCompatibility = Versions.jvmTargetVersion
}

// After updating gradle to 8.3 we will be able to replace `set` with assignments.
// For instance `fork = 1`
jmh {
    System.getProperty("jmh.include")?.let {
        includes.set(it.split(','))
    }

    warmupIterations.set(1)
    fork.set(1)
    iterations.set(4)
    timeOnIteration.set("2s")
}
