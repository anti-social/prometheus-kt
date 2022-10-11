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

dependencies {
    implementation(project(":"))

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
    testImplementation(project(":test-util"))
    testImplementation(Libs.kotlinxCoroutines("test"))
}

kotlin {
    target {
        attributes {
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 8)
        }
    }
}

tasks {
    // jacocoTestReport {
    //     additionalClassDirs.setFrom(
    //         files("${rootProject.buildDir}/classes/kotlin/jvm/main")
    //     )
    //     additionalSourceDirs.setFrom(
    //         files(rootProject.relativeProjectPath("../src/commonMain/kotlin"))
    //     )
    //
    //     reports {
    //         xml.isEnabled = true
    //         html.isEnabled = true
    //         csv.isEnabled = false
    //     }
    // }

    // named("test") {
    //     outputs.upToDateWhen { false }
    //
    //     finalizedBy("jacocoJvmTestReport")
    // }
}

configureJvmPublishing("prometheus-kt-hotspot", "Prometheus Kotlin Client - Hotspot support")
