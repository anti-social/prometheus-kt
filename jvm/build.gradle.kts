buildscript {
    repositories {
        mavenCentral()
    }
}

plugins {
    java
    kotlin("jvm")
    jacoco
}

repositories {
    jcenter()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(project(":"))

    implementation(kotlin("test"))
    implementation(kotlin("test-junit"))
    testImplementation(project(":", configuration = "jvmTestOutput"))
}

tasks {
    named("test") {
        outputs.upToDateWhen { false }

        finalizedBy(jacocoTestReport)
    }
}