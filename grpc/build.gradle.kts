buildscript {
    repositories {
        mavenCentral()
    }
}

plugins {
    java
    kotlin("jvm")
    jacoco
    `maven-publish`
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(project(":"))
    implementation(Libs.grpc("stub"))
    implementation(Libs.kotlinxCoroutines("core"))

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
    testImplementation(project(":test-util"))
    testImplementation(Libs.kotlinxCoroutines("test"))
}

kotlin {
    target {
        attributes {
            attribute(
                TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE,
                Versions.jvmTargetVersion.majorVersion.toInt()
            )
        }
    }
}

configureJvmPublishing("prometheus-kt-grpc", "Prometheus Kotlin Client - GRPC Metrics")
