import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("multiplatform")
    jacoco
    `maven-publish`
    id("org.ajoberstar.grgit") version Versions.grgit
}

val grgit: org.ajoberstar.grgit.Grgit by extra
val gitDescribe = grgit.describe(mapOf("match" to listOf("v*"), "tags" to true))
    ?: "v0.1.0-SNAPSHOT"

allprojects {
    group = "dev.evo"
    version = gitDescribe.trimStart('v')

    repositories {
        mavenCentral()
        jcenter()
        maven("https://kotlin.bintray.com/kotlinx")
    }
}

subprojects {
    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = Versions.jvmTarget
        }
    }
}

kotlin {
    configureMultiPlatform(project)

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:atomicfu:${Versions.atomicfu}")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotnlinxCoroutines}")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(project(":test-util"))
            }
        }

        val jvmMain by getting {
            languageSettings.useExperimentalAnnotation("kotlin.Experimental")
        }

        val jvmTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.kotnlinxCoroutines}")
            }
        }

        val jsMain by getting {}

        val nativeMain by getting {}
    }
}

tasks {
    val coverage = register<JacocoReport>("jacocoJVMTestReport") {
        group = "Reporting"
        description = "Generate Jacoco coverage report."
        classDirectories.setFrom(files(
            "$buildDir/classes/kotlin/jvm/main"
        ))
        sourceDirectories.setFrom(files(
            "src/commonMain/kotlin",
            "src/jvmMain/kotlin"
        ))
        executionData.setFrom(files("$buildDir/jacoco/jvmTest.exec"))
        reports {
            html.isEnabled = true
            xml.isEnabled = true
            csv.isEnabled = false
        }
    }
    named("jvmTest") {
        outputs.upToDateWhen { false }

        dependsOn(
            ":prometheus-kt-hotspot:test",
            ":prometheus-kt-ktor:test",
            ":prometheus-kt-push:jvmTest"
        )
        finalizedBy(coverage)
    }
    // named("jsNodeTest") {
    //     outputs.upToDateWhen { false }
    // }
    register("test") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        dependsOn(
            "jvmTest"
            // "jsTest"
        )
    }
}

publishing {
    configureMultiplatformPublishing(project)
}
