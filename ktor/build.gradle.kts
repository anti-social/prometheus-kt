buildscript {
    repositories {
        mavenCentral()
    }
}

plugins {
    kotlin("multiplatform")
    jacoco
    `maven-publish`
}

kotlin {
    configureMultiPlatform(project, disableJs = true)

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":"))
                implementation(Libs.ktor("server-core"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
                implementation(Libs.ktor("server-test-host"))
                implementation(Libs.kotlinxCoroutines("test"))
            }
        }

        val jvmMain by getting {
            dependencies {
                api(project(":prometheus-kt-hotspot"))
            }
        }
        val jvmTest by getting {}
        val nativeMain by getting {}
        val nativeTest by getting {}
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

configureMultiplatformPublishing("prometheus-kt-ktor", "Prometheus Kotlin Client - Ktor Framework")
